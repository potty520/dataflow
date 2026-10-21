package com.dgp.service;

import com.dgp.common.*;
import com.dgp.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放通道 /open/v1/{apiPath}：AK/SK 认证、配额与限流、脱敏输出，与内部 /api/v1 隔离。
 *
 * <p>修正点：原实现 `secret != null && !equals` 让"只传 X-App-Code、不传 Secret"直接通过鉴权；
 * OAuth2 token 无过期校验。现在两条通道都必须完成完整凭证校验。</p>
 */
@RestController
@RequestMapping("/open")
public class OpenApiController {

    private static final long TOKEN_TTL_SECONDS = 7200L;

    private final Db db;
    private final ServiceExecutionService executor;

    @Value("${dgp.token-minutes:30}")
    private long tokenMinutes;

    public OpenApiController(Db db, ServiceExecutionService executor) {
        this.db = db;
        this.executor = executor;
    }

    @PostMapping("/oauth2/token")
    public Result<Object> token(@RequestBody Map<String, Object> b) {
        String appCode = Req.str(b.get("appCode"));
        String secret = Req.str(b.get("appSecret"));
        if (Req.blank(appCode) || Req.blank(secret)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "appCode 与 appSecret 均为必填");
        }
        Map<String, Object> app = db.one("SELECT * FROM svc_app WHERE app_code=? AND status='ENABLED'", appCode);
        if (app == null || !JwtService.constantEquals(secret, String.valueOf(app.get("appSecret")))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "应用凭证无效");
        }
        String token = "AT-" + Ids.uuid();
        db.insert("INSERT INTO sys_async_task(task_id,task_type,status,result,start_time) VALUES(?,?,?,?,?)",
                token, "OAUTH_TOKEN", "SUCCESS", appCode, LocalDateTime.now());
        return Result.ok(M.of("access_token", token, "token_type", "Bearer",
                "expires_in", TOKEN_TTL_SECONDS, "app_code", appCode));
    }

    @GetMapping("/v1/**")
    public Result<Object> get(HttpServletRequest req) {
        return invoke(req, null);
    }

    @PostMapping("/v1/**")
    public Result<Object> post(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        return invoke(req, body);
    }

    private Result<Object> invoke(HttpServletRequest req, Map<String, Object> body) {
        String uri = req.getRequestURI();
        int idx = uri.indexOf("/open/v1");
        String apiPath = idx < 0 ? uri : uri.substring(idx + "/open/v1".length());
        Map<String, Object> api = db.one("SELECT * FROM svc_api WHERE api_path=?", apiPath);
        if (api == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "开放接口不存在：" + apiPath);
        }
        String appCode = resolveAppCode(req);
        Map<String, Object> app = db.one("SELECT * FROM svc_app WHERE app_code=? AND status='ENABLED'", appCode);
        if (app == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "应用不存在或已停用");
        }
        List<Map<String, Object>> black = db.list("SELECT * FROM svc_blacklist WHERE ip=? AND status='ENABLED'",
                com.dgp.security.AuthInterceptor.clientIp(req));
        if (!black.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN, "来源 IP 已被加入黑名单");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (v.length > 0) params.put(k, v[0]);
        });
        if (body != null) {
            params.putAll(body);
        }
        Map<String, Object> result = executor.execute(api, params, appCode, com.dgp.security.AuthInterceptor.clientIp(req), false);
        return Result.ok(result);
    }

    /** 解析调用方应用：Bearer AT-* 走 OAuth2 令牌，否则必须同时提供 X-App-Code 与 X-App-Secret。 */
    private String resolveAppCode(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer AT-")) {
            String token = auth.substring(7);
            Map<String, Object> row = db.one(
                    "SELECT result, start_time FROM sys_async_task WHERE task_id=? AND task_type='OAUTH_TOKEN'", token);
            if (row == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "令牌无效");
            }
            Object st = row.get("startTime");
            if (st instanceof LocalDateTime time
                    && ChronoUnit.SECONDS.between(time, LocalDateTime.now()) > TOKEN_TTL_SECONDS) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "令牌已过期");
            }
            return String.valueOf(row.get("result"));
        }
        String appCode = req.getHeader("X-App-Code");
        String secret = req.getHeader("X-App-Secret");
        if (appCode == null || secret == null || secret.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少 X-App-Code / X-App-Secret 或 Bearer Token");
        }
        Map<String, Object> app = db.one("SELECT app_secret FROM svc_app WHERE app_code=? AND status='ENABLED'", appCode);
        // 缺失、空值、错误一律拒绝（原实现 secret==null 时跳过校验）
        if (app == null || !JwtService.constantEquals(secret, String.valueOf(app.get("appSecret")))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "应用凭证无效");
        }
        return appCode;
    }
}
