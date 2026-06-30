package com.zhangye.dataflow.service;

import com.zhangye.dataflow.entity.SysConfig;
import com.zhangye.dataflow.mapper.SysConfigMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConfigService {

    private final SysConfigMapper configMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CONFIG_HASH_KEY = "dataflow:sys_config";
    private static final String CONFIG_INVALIDATION_CHANNEL = "dataflow:config:invalidate";

    public ConfigService(SysConfigMapper configMapper, RedisTemplate<String, Object> redisTemplate) {
        this.configMapper = configMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Read config value — Redis hash first, fallback to DB.
     */
    @Cacheable(value = "sysConfig", key = "#key", unless = "#result == null || #result.isEmpty()")
    public String getConfigValue(String key) {
        Object val = redisTemplate.opsForHash().get(CONFIG_HASH_KEY, key);
        if (val != null) return val.toString();

        SysConfig config = configMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key));
        if (config != null) {
            redisTemplate.opsForHash().put(CONFIG_HASH_KEY, key, config.getConfigValue());
            return config.getConfigValue();
        }
        return "";
    }

    public String getConfigValue(String key, String defaultValue) {
        String v = getConfigValue(key);
        return v.isEmpty() ? defaultValue : v;
    }

    /**
     * Write config value — update DB + Redis, then broadcast invalidation.
     */
    @CacheEvict(value = "sysConfig", key = "#key")
    public void setConfigValue(String key, String value) {
        SysConfig config = configMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key));
        if (config != null) {
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(config);
        }
        redisTemplate.opsForHash().put(CONFIG_HASH_KEY, key, value);

        // Broadcast config change to all nodes
        redisTemplate.convertAndSend(CONFIG_INVALIDATION_CHANNEL, key);
    }

    /**
     * Batch load all configs from DB into Redis (called on startup).
     */
    public void loadAll() {
        java.util.List<SysConfig> list = configMapper.selectList(null);
        for (SysConfig c : list) {
            redisTemplate.opsForHash().put(CONFIG_HASH_KEY, c.getConfigKey(), c.getConfigValue());
        }
    }
}
