package com.sewlect.common.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    boolean tryConsume(String subject, String operationKey, int limit, Duration window);
}