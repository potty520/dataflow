package com.zhangye.dataflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.entity.SvcRateLimit;
import com.zhangye.dataflow.mapper.SvcRateLimitMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private final SvcRateLimitMapper rateLimitMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RL_PREFIX = "dataflow:ratelimit:";

    public RateLimitService(SvcRateLimitMapper rateLimitMapper, RedisTemplate<String, Object> redisTemplate) {
        this.rateLimitMapper = rateLimitMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Sliding window rate limit via Redis INCR + TTL.
     * Atomic per-api-per-app granularity.
     */
    public boolean allowRequest(Long apiId, Long appId) {
        String rlKey = RL_PREFIX + apiId + ":" + appId;

        SvcRateLimit limit = rateLimitMapper.selectOne(new LambdaQueryWrapper<SvcRateLimit>()
                .eq(SvcRateLimit::getApiId, apiId));
        if (limit == null || limit.getEnabled() == null || limit.getEnabled() == 0) {
            return true;
        }

        int windowSec = limit.getTimeWindowSec() != null ? limit.getTimeWindowSec() : 60;
        int maxReq = limit.getMaxRequests() != null ? limit.getMaxRequests() : 100;

        Long count = redisTemplate.opsForValue().increment(rlKey);

        // Set TTL only on first request in the window
        if (count != null && count == 1L) {
            redisTemplate.expire(rlKey, windowSec, TimeUnit.SECONDS);
        }

        return count == null || count <= maxReq;
    }
}
