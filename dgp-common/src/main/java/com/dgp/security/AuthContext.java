package com.dgp.security;

/** 请求级登录上下文。 */
public final class AuthContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static String username() {
        AuthUser u = HOLDER.get();
        return u == null ? "anonymous" : u.getUsername();
    }

    public static Long tenantId() {
        AuthUser u = HOLDER.get();
        return u == null ? 1L : u.getTenantId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
