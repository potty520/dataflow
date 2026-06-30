package com.zhangye.dataflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }

    public static Long getCurrentUserId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    public static Long getCurrentTenantId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getTenantId() : null;
    }

    public static String getCurrentUsername() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }
}
