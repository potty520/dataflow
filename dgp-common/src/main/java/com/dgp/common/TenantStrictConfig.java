package com.dgp.common;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/** 将 dgp.tenant.strict 配置同步到 TenantGuard。 */
@Component
@ConditionalOnClass(name = "org.springframework.stereotype.Component")
public class TenantStrictConfig {

    @Value("${dgp.tenant.strict:true}")
    private boolean strict;

    @PostConstruct
    public void apply() {
        TenantGuard.setStrict(strict);
    }
}
