package com.dgp.security;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/** 认证、角色权限与令牌编排。 */
@Service
public class AuthService {

    /** PBKDF2 迭代次数（OWASP 对 PBKDF2-HMAC-SHA256 的建议量级）；旧哈希自带迭代数，可平滑升级。 */
    private static final int ITERATIONS = 210_000;
    private static final int MIN_PASSWORD_LEN = 8;

    private final Db db;
    private final JwtService tokenService;

    /** 鉴权缓存 TTL：用户与权限 30s，角色权限 60s；变更接口会立即失效。 */
    private static final long USER_CACHE_MS = 30_000;
    private static final long PERM_CACHE_MS = 60_000;
    private static final long REVOKE_SYNC_MS = 10_000;

    private final java.util.concurrent.ConcurrentHashMap<String, Object[]> userCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Set<String>> permCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> permExpire =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 已吊销 jti（登出立即写入；每 10s 与库做一次全量对齐，支持多实例部署）。 */
    private final Set<String> revokedJti = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile long revokedSyncedAt = 0L;
    /** 会话活跃缓存：sid -> 校验通过到期时间（毫秒）；登出即时清除，跨实例 5 秒内生效。 */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> sessionActive =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long SESSION_CACHE_MS = 5_000;

    public AuthService(Db db, JwtService tokenService) {
        this.db = db;
        this.tokenService = tokenService;
    }

    // ---------------- 口令散列（PBKDF2，无外部依赖） ----------------
    public static String hash(String raw) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] dk = pbkdf2(raw.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(dk);
    }

    public static boolean matches(String raw, String stored) {
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4) return false;
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expect = Base64.getDecoder().decode(p[3]);
            byte[] actual = pbkdf2(raw.toCharArray(), salt, Integer.parseInt(p[1]));
            return MessageDigest.isEqual(expect, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] raw, byte[] salt, int iter) {
        try {
            PBEKeySpec spec = new PBEKeySpec(raw, salt, iter, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 口令复杂度校验：长度 ≥ 8，且同时包含字母与数字。 */
    public static void assertPasswordPolicy(String pwd) {
        if (pwd == null || pwd.length() < MIN_PASSWORD_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID, "口令长度至少 " + MIN_PASSWORD_LEN + " 位");
        }
        boolean hasLetter = pwd.chars().anyMatch(Character::isLetter);
        boolean hasDigit = pwd.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BizException(ErrorCode.PARAM_INVALID, "口令必须同时包含字母与数字");
        }
    }

    // ---------------- 登录 ----------------
    public Map<String, Object> login(String username, String password, String ip, String userAgent) {
        // 登录失败限速：同一账号 15 分钟内失败 10 次后暂时锁定，抵御暴力破解
        long recentFails = db.count("SELECT COUNT(*) FROM sys_login_log WHERE username=? AND status='FAIL' AND login_time > ?",
                username, LocalDateTime.now().minusMinutes(15));
        if (recentFails >= 10) {
            throw new BizException(ErrorCode.RATE_LIMITED, "登录失败次数过多，请 15 分钟后再试");
        }
        Map<String, Object> user = db.one("SELECT * FROM sys_user WHERE username=?", username);
        if (user == null || !matches(password, String.valueOf(user.get("password")))) {
            db.update("INSERT INTO sys_login_log(username,ip,status,message,user_agent,login_time) VALUES(?,?,?,?,?,?)",
                    username, ip, "FAIL", "用户名或口令错误", userAgent, LocalDateTime.now());
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或口令错误");
        }
        if (!"启用".equals(user.get("status"))) {
            db.update("INSERT INTO sys_login_log(username,ip,status,message,user_agent,login_time) VALUES(?,?,?,?,?,?)",
                    username, ip, "FAIL", "账号已停用", userAgent, LocalDateTime.now());
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        AuthUser au = toAuthUser(user);
        db.update("UPDATE sys_user SET last_login_time=? WHERE id=?", LocalDateTime.now(), user.get("id"));
        // 成功日志只写一次（原实现在 AuthService 与 AuthController 各写一次，导致重复计数）
        db.update("INSERT INTO sys_login_log(username,ip,status,message,user_agent,login_time) VALUES(?,?,?,?,?,?)",
                username, ip, "SUCCESS", "登录成功", userAgent, LocalDateTime.now());
        String sid = UUID.randomUUID().toString().replace("-", "");
        db.insert("INSERT INTO sys_session(sid,username,user_id,tenant_id,status,ip,user_agent,create_time,last_seen) "
                        + "VALUES(?,?,?,?,'ACTIVE',?,?,?,?)",
                sid, au.getUsername(), au.getUserId(), au.getTenantId(), ip,
                userAgent == null ? null : (userAgent.length() > 250 ? userAgent.substring(0, 250) : userAgent),
                LocalDateTime.now(), LocalDateTime.now());
        return tokenPair(au, sid);
    }

    /**
     * 刷新令牌：一次性轮换 + 重放检测。
     * 旧 refreshToken 使用后立即标记 used_at；再次使用视为重放，直接吊销整个会话（sid 下所有令牌）。
     */
    public Map<String, Object> refresh(String refreshToken) {
        Map<String, Object> payload = tokenService.verify(refreshToken, JwtService.TYP_REFRESH);
        if (payload == null) throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken 无效或已过期");
        String jti = String.valueOf(payload.get("jti"));
        String sid = payload.get("sid") == null ? null : String.valueOf(payload.get("sid"));
        Map<String, Object> row = db.one("SELECT * FROM sys_refresh_token WHERE jti=?", jti);
        if (row == null) throw new BizException(ErrorCode.UNAUTHORIZED, "会话已失效，请重新登录");
        if (sid == null || sid.isBlank()) sid = String.valueOf(row.get("sid"));
        if (row.get("revokedAt") != null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话已登出，请重新登录");
        }
        if (row.get("usedAt") != null) {
            revokeSession(sid);
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken 已被使用（疑似重放），会话已注销");
        }
        long exp = row.get("expireAt") == null ? 0L : ((Number) row.get("expireAt")).longValue();
        if (exp < System.currentTimeMillis() / 1000) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken 已过期");
        }
        if (!sessionActive(sid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        String username = String.valueOf(payload.get("sub"));
        Map<String, Object> user = db.one("SELECT * FROM sys_user WHERE username=?", username);
        if (user == null || !"启用".equals(user.get("status"))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或已停用");
        }
        // CAS 标记已用：并发刷新只有一个成功，其余视为重放
        int marked = db.update("UPDATE sys_refresh_token SET used_at=? WHERE jti=? AND used_at IS NULL",
                LocalDateTime.now(), jti);
        if (marked == 0) {
            revokeSession(sid);
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken 已被使用（疑似重放），会话已注销");
        }
        Map<String, Object> out = tokenPair(toAuthUser(user), sid);
        Map<String, Object> newPayload = tokenService.verifySignature(String.valueOf(out.get("refreshToken")));
        if (newPayload != null) {
            db.update("UPDATE sys_refresh_token SET replaced_by=? WHERE jti=?",
                    String.valueOf(newPayload.get("jti")), jti);
        }
        db.update("UPDATE sys_session SET last_seen=? WHERE sid=?", LocalDateTime.now(), sid);
        return out;
    }

    private Map<String, Object> tokenPair(AuthUser au) {
        return tokenPair(au, null);
    }

    private Map<String, Object> tokenPair(AuthUser au, String sid) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessToken", tokenService.accessToken(au.getUserId(), au.getUsername(), au.getRoleCode(), au.getTenantId(), sid));
        String refresh = tokenService.refreshToken(au.getUserId(), au.getUsername(), au.getRoleCode(), au.getTenantId(), sid);
        out.put("refreshToken", refresh);
        Map<String, Object> rp = tokenService.verifySignature(refresh);
        if (rp != null && sid != null) {
            long exp = ((Number) rp.getOrDefault("exp", 0L)).longValue();
            try {
                db.insert("INSERT INTO sys_refresh_token(jti,sid,username,user_id,tenant_id,expire_at,create_time) "
                                + "VALUES(?,?,?,?,?,?,?)",
                        String.valueOf(rp.get("jti")), sid, au.getUsername(), au.getUserId(),
                        au.getTenantId(), exp, LocalDateTime.now());
            } catch (Exception ignore) {
                // 极端并发下主键冲突：已存在即视为已登记
            }
        }
        out.put("tokenType", "Bearer");
        out.put("expiresIn", tokenService.getTokenMinutes() * 60);
        out.put("refreshExpiresIn", tokenService.getRefreshDays() * 24 * 60 * 60);
        out.put("user", profile(au));
        return out;
    }

    public AuthUser loadByToken(String token) {
        Map<String, Object> payload = tokenService.verify(token, JwtService.TYP_ACCESS);
        if (payload == null) return null;
        if (isRevoked(String.valueOf(payload.get("jti")))) return null;
        Object sid = payload.get("sid");
        if (sid != null && !sessionActive(String.valueOf(sid))) return null;
        String username = String.valueOf(payload.get("sub"));
        // 鉴权结果缓存：原本每个请求要查"黑名单 + 用户 + 角色权限"3 次库，
        // 高并发下这是主要的数据库压力来源；缓存后读请求 0 次鉴权查询。
        AuthUser cached = cachedUser(username);
        if (cached != null) return cached;
        Map<String, Object> user = db.one("SELECT * FROM sys_user WHERE username=?", username);
        if (user == null || !"启用".equals(user.get("status"))) return null;
        AuthUser au = toAuthUser(user);
        userCache.put(username, new Object[]{au, System.currentTimeMillis() + USER_CACHE_MS});
        return au;
    }

    private AuthUser cachedUser(String username) {
        Object[] hit = userCache.get(username);
        if (hit == null) return null;
        if (((Long) hit[1]) < System.currentTimeMillis()) {
            userCache.remove(username, hit);
            return null;
        }
        return (AuthUser) hit[0];
    }

    /** 用户/角色/权限变更后必须调用，避免旧权限在缓存窗口内继续生效。 */
    public void invalidateCaches() {
        userCache.clear();
        permCache.clear();
        permExpire.clear();
    }

    /** 登出：把当前令牌的 jti 写入黑名单，令牌立即失效。 */
    public void logout(String token) {
        Map<String, Object> payload = tokenService.verifySignature(token);
        if (payload == null) return;
        String jti = String.valueOf(payload.get("jti"));
        if (jti == null || "null".equals(jti) || jti.isBlank()) return;
        long exp = ((Number) payload.getOrDefault("exp", 0L)).longValue();
        try {
            db.update("INSERT INTO sys_token_blacklist(jti,username,expire_at,create_time) VALUES(?,?,?,?)",
                    jti, String.valueOf(payload.get("sub")), exp, LocalDateTime.now());
        } catch (Exception ignore) {
            // 重复登出不报错
        }
        // 立即在本进程生效（不必等 10s 与库对齐）
        revokedJti.add(jti);
        // 关键修复：登出同时吊销会话，使 refreshToken 及其派生的所有 accessToken 一并失效
        Object sid = payload.get("sid");
        if (sid != null && !String.valueOf(sid).isBlank()) {
            revokeSession(String.valueOf(sid));
        }
        // 顺手清理过期黑名单，避免表无限增长
        db.update("DELETE FROM sys_token_blacklist WHERE expire_at < ?", System.currentTimeMillis() / 1000 - 60);
    }

    /** 吊销会话：会话状态置 REVOKED，该 sid 下未使用的 refreshToken 全部吊销，并清本地缓存。 */
    public void revokeSession(String sid) {
        if (sid == null || sid.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();
        db.update("UPDATE sys_session SET status='REVOKED',revoked_at=? WHERE sid=? AND revoked_at IS NULL", now, sid);
        db.update("UPDATE sys_refresh_token SET revoked_at=? WHERE sid=? AND revoked_at IS NULL", now, sid);
        sessionActive.remove(sid);
    }

    /** 会话是否活跃（本地缓存 5s，跨实例登出后最多 5s 生效）。 */
    private boolean sessionActive(String sid) {
        if (sid == null || sid.isBlank()) return true;
        long now = System.currentTimeMillis();
        Long until = sessionActive.get(sid);
        if (until != null && until > now) return true;
        Map<String, Object> row = db.one("SELECT status,revoked_at FROM sys_session WHERE sid=?", sid);
        if (row == null) return false;
        boolean active = row.get("revokedAt") == null && "ACTIVE".equals(String.valueOf(row.get("status")));
        if (active) sessionActive.put(sid, now + SESSION_CACHE_MS);
        else sessionActive.remove(sid);
        return active;
    }

    private boolean isRevoked(String jti) {
        if (jti == null || "null".equals(jti) || jti.isBlank()) return false;
        if (revokedJti.contains(jti)) return true;
        long now = System.currentTimeMillis();
        if (now - revokedSyncedAt > REVOKE_SYNC_MS) {
            synchronized (this) {
                if (now - revokedSyncedAt > REVOKE_SYNC_MS) {
                    try {
                        Set<String> fresh = new HashSet<>();
                        for (Map<String, Object> r : db.list(
                                "SELECT jti FROM sys_token_blacklist WHERE expire_at > ?",
                                System.currentTimeMillis() / 1000)) {
                            fresh.add(String.valueOf(r.get("jti")));
                        }
                        revokedJti.retainAll(fresh);
                        revokedJti.addAll(fresh);
                    } catch (Exception e) {
                        // 黑名单同步失败时降级为"仅内存判断"，不阻塞请求
                    }
                    revokedSyncedAt = now;
                }
            }
            return revokedJti.contains(jti);
        }
        return false;
    }

    private AuthUser toAuthUser(Map<String, Object> user) {
        String roleCode = String.valueOf(user.get("roleCode"));
        Set<String> perms = permsOf(roleCode);
        return new AuthUser(Db.longVal(user.get("id"), 0L), String.valueOf(user.get("username")),
                String.valueOf(user.get("realName")), roleCode,
                Db.longVal(user.get("tenantId"), 1L), perms);
    }

    public Set<String> permsOf(String roleCode) {
        long now = System.currentTimeMillis();
        Set<String> cached = permCache.get(roleCode);
        Long exp = permExpire.get(roleCode);
        if (cached != null && exp != null && exp > now) {
            return cached;
        }
        Map<String, Object> role = db.one("SELECT perms FROM sys_role WHERE code=?", roleCode);
        Set<String> set = new HashSet<>();
        if (role != null && role.get("perms") != null) {
            for (String p : String.valueOf(role.get("perms")).split(",")) {
                if (!p.isBlank()) set.add(p.trim());
            }
        }
        Set<String> out = Collections.unmodifiableSet(set);
        permCache.put(roleCode, out);
        permExpire.put(roleCode, now + PERM_CACHE_MS);
        return out;
    }

    public Map<String, Object> profile(AuthUser u) {
        if (u == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", u.getUserId());
        out.put("username", u.getUsername());
        out.put("realName", u.getRealName());
        out.put("roleCode", u.getRoleCode());
        Map<String, Object> role = db.one("SELECT name FROM sys_role WHERE code=?", u.getRoleCode());
        out.put("roleName", role == null ? u.getRoleCode() : role.get("name"));
        out.put("tenantId", u.getTenantId());
        Map<String, Object> tenant = db.one("SELECT * FROM sys_tenant WHERE id=?", u.getTenantId());
        if (tenant != null) {
            out.put("tenantName", tenant.get("name"));
            out.put("tenantPath", tenant.get("tenantPath"));
            out.put("industryCode", tenant.get("industryCode"));
        }
        out.put("permissions", new ArrayList<>(u.getPerms()));
        out.put("dataScopes", db.list("SELECT * FROM sys_data_scope WHERE role_code=?", u.getRoleCode()));
        return out;
    }

    /** 用户自助改密：必须校验原口令。 */
    public void changePassword(String username, String oldPwd, String newPwd) {
        assertPasswordPolicy(newPwd);
        Map<String, Object> user = db.one("SELECT * FROM sys_user WHERE username=?", username);
        if (user == null || !matches(oldPwd, String.valueOf(user.get("password")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原口令不正确");
        }
        if (matches(newPwd, String.valueOf(user.get("password")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新口令不能与原口令相同");
        }
        db.update("UPDATE sys_user SET password=? WHERE username=?", hash(newPwd), username);
    }

    /** 管理员重置他人口令（调用方负责鉴权）。 */
    public void resetPassword(long userId, String newPwd) {
        assertPasswordPolicy(newPwd);
        Map<String, Object> user = db.one("SELECT id FROM sys_user WHERE id=?", userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        db.update("UPDATE sys_user SET password=? WHERE id=?", hash(newPwd), userId);
    }
}
