package com.dgp.common;

/**
 * 网关 → 下游服务的内部身份头约定。
 * 下游只信任带正确 HMAC 签名的头，浏览器直连无效。
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String REAL_NAME = "X-Real-Name";
    public static final String ROLE = "X-Role";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String PERMS = "X-Perms";
    public static final String SESSION_ID = "X-Session-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String GATEWAY_SIGN = "X-Gateway-Sign";
    public static final String GATEWAY_TS = "X-Gateway-Ts";
    public static final String INTERNAL_SKIP_AUTH = "X-Internal-Public";
    /** 调度服务调用 core 内部接口时的共享令牌。 */
    public static final String SCHEDULER_TOKEN = "X-Scheduler-Token";

    private GatewayHeaders() {
    }

    /** 参与 HMAC 的规范串：uid|username|role|tenant|ts */
    public static String signPayload(String userId, String username, String role, String tenantId, String ts) {
        return String.join("|",
                nullToEmpty(userId),
                nullToEmpty(username),
                nullToEmpty(role),
                nullToEmpty(tenantId),
                nullToEmpty(ts));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
