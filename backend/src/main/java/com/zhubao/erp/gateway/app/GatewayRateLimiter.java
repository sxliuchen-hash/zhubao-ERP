package com.zhubao.erp.gateway.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Sliding-window rate limiter for the device ingestion gateway.
 *
 * <p>The ingestion endpoint is authenticated by device key rather than human RBAC, so it needs its
 * own abuse protection: a misconfigured low-cost bridge (or a leaked key) could otherwise flood the
 * endpoint. This limiter caps readings per device within a rolling window and is safe for
 * concurrent access.</p>
 */
@Component
public class GatewayRateLimiter {

    private final int maxReadings;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Deque<Long>> timestamps = new ConcurrentHashMap<>();

    @Autowired
    public GatewayRateLimiter(
            @Value("${zhubao.gateway.rate-limit.max-readings:120}") int maxReadings,
            @Value("${zhubao.gateway.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this(maxReadings, windowSeconds, Clock.systemUTC());
    }

    GatewayRateLimiter(int maxReadings, long windowSeconds, Clock clock) {
        this.maxReadings = maxReadings;
        this.windowMillis = windowSeconds * 1000L;
        this.clock = clock;
    }

    /**
     * @return {@code true} if the reading is allowed, {@code false} if the device exceeded its quota.
     */
    public boolean tryAcquire(String deviceCode) {
        long now = clock.millis();
        Deque<Long> window = timestamps.computeIfAbsent(deviceCode, ignored -> new ArrayDeque<>());
        synchronized (window) {
            long cutoff = now - windowMillis;
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.pollFirst();
            }
            if (window.size() >= maxReadings) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    public int maxReadings() {
        return maxReadings;
    }

    /** Test/inspection helper: current window count for a device. */
    Map<String, Integer> windowSizes() {
        Map<String, Integer> sizes = new ConcurrentHashMap<>();
        timestamps.forEach((device, window) -> {
            synchronized (window) {
                sizes.put(device, window.size());
            }
        });
        return sizes;
    }
}
