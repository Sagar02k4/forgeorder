package com.sagar.forgeorder.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final Duration WINDOW_DURATION = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String rateLimitKey) {
        Long currentCount = redisTemplate.opsForValue().increment(rateLimitKey);

        if (currentCount != null && currentCount == 1L) {
            redisTemplate.expire(rateLimitKey, WINDOW_DURATION);
        }

        return currentCount != null && currentCount <= MAX_REQUESTS_PER_WINDOW;
    }
}