package com.iflytek.skillhub.ratelimit;

public interface RateLimiter {

    boolean tryAcquire(String key, int limit, int windowSeconds);
}
