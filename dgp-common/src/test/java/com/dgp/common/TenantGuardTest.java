package com.dgp.common;

import com.dgp.security.AuthContext;
import com.dgp.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TenantGuardTest {

    @BeforeEach
    void setUp() {
        TenantGuard.setStrict(true);
        TenantGuard.resetTableCacheForTest(Set.of("md_table", "qa_rule", "sec_audit_log"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        TenantGuard.resetTableCacheForTest(null);
    }

    @Test
    void injectsTenantIdForPlainSelect() {
        AuthContext.set(user("dev", "ROLE_DATA_DEV", 2L));
        String sql = TenantGuard.rewrite("SELECT * FROM md_table ORDER BY id");
        assertTrue(sql.toLowerCase().contains("tenant_id = 2"));
        assertTrue(sql.toLowerCase().contains("where"));
    }

    @Test
    void adminBypassesInjection() {
        AuthContext.set(user("admin", "ROLE_GOV_ADMIN", 1L));
        String sql = TenantGuard.rewrite("SELECT * FROM md_table");
        assertEquals("SELECT * FROM md_table", sql);
    }

    @Test
    void nonAdminCannotUseTenantOff() {
        AuthContext.set(user("dev", "ROLE_DATA_DEV", 2L));
        assertThrows(BizException.class,
                () -> TenantGuard.rewrite("SELECT /*tenant:off*/ * FROM md_table"));
    }

    @Test
    void adminCanUseTenantOff() {
        AuthContext.set(user("admin", "ROLE_GOV_ADMIN", 1L));
        String sql = TenantGuard.rewrite("SELECT /*tenant:off*/ * FROM md_table");
        assertFalse(sql.contains("tenant:off"));
    }

    @Test
    void strictRejectsComplexSqlWithoutTenant() {
        AuthContext.set(user("dev", "ROLE_DATA_DEV", 2L));
        assertThrows(BizException.class,
                () -> TenantGuard.rewrite("SELECT a.* FROM md_table a JOIN qa_rule b ON a.id=b.table_id"));
    }

    @Test
    void allowsComplexSqlWithExplicitTenant() {
        AuthContext.set(user("dev", "ROLE_DATA_DEV", 2L));
        String sql = "SELECT a.* FROM md_table a JOIN qa_rule b ON a.id=b.table_id WHERE a.tenant_id=2";
        assertEquals(sql, TenantGuard.rewrite(sql));
    }

    private static AuthUser user(String name, String role, long tenantId) {
        return new AuthUser(1L, name, name, role, tenantId, Set.of());
    }
}
