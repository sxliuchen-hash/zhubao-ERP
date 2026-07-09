package com.zhubao.erp.gateway.app;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRateLimiterTest {

    @Test
    void allowsUpToMaxWithinWindow() {
        GatewayRateLimiter limiter = new GatewayRateLimiter(3, 60);
        assertThat(limiter.tryAcquire("SCALE-01")).isTrue();
        assertThat(limiter.tryAcquire("SCALE-01")).isTrue();
        assertThat(limiter.tryAcquire("SCALE-01")).isTrue();
        assertThat(limiter.tryAcquire("SCALE-01")).isFalse();
    }

    @Test
    void tracksDevicesIndependently() {
        GatewayRateLimiter limiter = new GatewayRateLimiter(1, 60);
        assertThat(limiter.tryAcquire("SCALE-01")).isTrue();
        assertThat(limiter.tryAcquire("SCALE-01")).isFalse();
        assertThat(limiter.tryAcquire("SCAN-PC-01")).isTrue();
    }

    @Test
    void releasesQuotaAfterWindowElapses() {
        AtomicLong nowMillis = new AtomicLong(1_000_000L);
        Clock movingClock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(nowMillis.get());
            }

            @Override
            public long millis() {
                return nowMillis.get();
            }
        };
        GatewayRateLimiter limiter = new GatewayRateLimiter(1, 10, movingClock);

        assertThat(limiter.tryAcquire("SCALE-01")).isTrue();
        assertThat(limiter.tryAcquire("SCALE-01")).isFalse();

        // Advance past the 10s window.
        nowMillis.addAndGet(11_000L);
        assertThat(limiter.tryAcquire("SCALE-01")).isTrue();
    }
}
