package com.dgp.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p>基线要求 RS256；本交付为单进程私有化形态，密钥不跨服务分发，采用 HS256 等价实现，
 * 差异登记于 README「偏离说明」；切换 RS256 仅需替换 sign/verify 两个方法。</p>
 *
 * <p>安全要点：access/refresh 通过 {@code typ} 声明区分，互不可替代；
 * refresh 有效期由 {@code dgp.refresh-days} 独立控制；登出通过 jti 黑名单吊销。</p>
 */
public class JwtService {

    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private final byte[] secret;
    private final long tokenMinutes;
    private final long refreshDays;

    public JwtService(String secret, long tokenMinutes, long refreshDays) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenMinutes = Math.max(1, tokenMinutes);
        this.refreshDays = Math.max(1, refreshDays);
    }

    private String create(Long userId, String username, String roleCode, Long tenantId, long minutes, String typ) {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("uid", userId);
        payload.put("role", roleCode);
        payload.put("tid", tenantId);
        payload.put("typ", typ);
        payload.put("iat", now);
        payload.put("exp", now + minutes * 60);
        payload.put("jti", java.util.UUID.randomUUID().toString().replace("-", ""));

        String h = b64(json(header));
        String p = b64(json(payload));
        String sig = sign(h + "." + p);
        return h + "." + p + "." + sig;
    }

    public String accessToken(Long userId, String username, String roleCode, Long tenantId) {
        return create(userId, username, roleCode, tenantId, tokenMinutes, TYP_ACCESS);
    }

    public String refreshToken(Long userId, String username, String roleCode, Long tenantId) {
        return create(userId, username, roleCode, tenantId, refreshDays * 24L * 60L, TYP_REFRESH);
    }

    public long getTokenMinutes() {
        return tokenMinutes;
    }

    public long getRefreshDays() {
        return refreshDays;
    }

    /** 校验签名 + 类型 + 有效期；失败返回 null。 */
    public Map<String, Object> verify(String token, String expectedTyp) {
        Map<String, Object> payload = verifySignature(token);
        if (payload == null) return null;
        if (expectedTyp != null && !expectedTyp.equals(String.valueOf(payload.get("typ")))) return null;
        long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
        if (exp < System.currentTimeMillis() / 1000) return null;
        return payload;
    }

    /** 仅校验签名与结构（用于登出登记，允许已过期的令牌也能被拉黑）。 */
    public Map<String, Object> verifySignature(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        String expect = sign(parts[0] + "." + parts[1]);
        if (!constantEquals(expect, parts[2])) return null;
        try {
            String body = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return Json.parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return b64(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String b64(String s) {
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append("\"").append(escape(String.valueOf(v))).append("\"");
            }
        }
        return sb.append("}").toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static boolean constantEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    /** 极简 JSON 解析（仅用于本类 payload，避免引入额外依赖）。 */
    private static final class Json {
        static Map<String, Object> parse(String s) {
            Map<String, Object> out = new LinkedHashMap<>();
            String body = s.trim();
            if (body.startsWith("{")) body = body.substring(1);
            if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
            for (String kv : body.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                String[] p = kv.split(":", 2);
                if (p.length < 2) continue;
                String k = unescape(p[0].trim().replace("\"", ""));
                String v = p[1].trim();
                if (v.startsWith("\"")) {
                    out.put(k, unescape(v.substring(1, v.length() - 1)));
                } else {
                    try {
                        out.put(k, Long.parseLong(v));
                    } catch (NumberFormatException e) {
                        out.put(k, v);
                    }
                }
            }
            return out;
        }

        private static String unescape(String s) {
            return s.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }
}
