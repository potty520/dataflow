package com.dgp.security;

import java.util.Set;

/** 当前请求的登录主体。 */
public class AuthUser {
    private final Long userId;
    private final String username;
    private final String realName;
    private final String roleCode;
    private final Long tenantId;
    private final Set<String> perms;

    public AuthUser(Long userId, String username, String realName, String roleCode, Long tenantId, Set<String> perms) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.roleCode = roleCode;
        this.tenantId = tenantId;
        this.perms = perms;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRealName() {
        return realName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Set<String> getPerms() {
        return perms;
    }

    public boolean isAdmin() {
        return "ROLE_GOV_ADMIN".equals(roleCode) || perms.contains("*");
    }

    public boolean has(String perm) {
        return isAdmin() || perms.contains(perm);
    }
}
