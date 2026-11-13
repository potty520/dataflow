package com.dgp.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * 默认关闭 Redis 自动配置；设置 dgp.redis.enabled=true 时启用（生产/compose）。
 */
@Configuration
@ConditionalOnProperty(name = "dgp.redis.enabled", havingValue = "false", matchIfMissing = true)
@EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
public class RedisOptionalConfig {
}
