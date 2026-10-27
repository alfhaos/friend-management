package com.apr.aprbackendassignment.util;

import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRateLimiterConfigurationTest extends IntegrationTestSupport {

    @Test
    void existingRedisRateLimitKeepsItsFirstConfiguration() {
        RRateLimiter limiter = redisson.getRateLimiter("test:rate-limit:configuration");

        assertThat(limiter.trySetRate(RateType.OVERALL, 1, 60, RateIntervalUnit.SECONDS)).isTrue();
        assertThat(limiter.trySetRate(RateType.OVERALL, 2, 60, RateIntervalUnit.SECONDS)).isFalse();

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }
}
