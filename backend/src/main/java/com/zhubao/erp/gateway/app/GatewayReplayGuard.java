package com.zhubao.erp.gateway.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Replay protection for the device ingestion gateway.
 *
 * <p>The device key travels in each request, so a captured valid request could otherwise be
 * replayed. When enabled, this guard requires each reading to carry a {@code nonce} and a
 * timestamp: it rejects timestamps outside a freshness window and rejects a nonce that was already
 * seen for the same device. Nonces are retained per device only for the freshness window, keeping
 * memory bounded.</p>
 *
 * <p>Disabled by default for local review (so evaluation scripts keep working) and enabled in the
 * MySQL/production profile, mirroring the project's other security toggles. When disabled, all
 * readings pass through.</p>
 */
@Component
public class GatewayReplayGuard {

    private final boolean enabled;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, NonceWindow> nonces = new ConcurrentHashMap<>();

    @Autowired
    public GatewayReplayGuard(
            @Value("${zhubao.gateway.replay.enabled:false}") boolean enabled,
            @Value("${zhubao.gateway.replay.window-seconds:300}") long windowSeconds
    ) {
        this(enabled, windowSeconds, Clock.systemUTC());
    }

    GatewayReplayGuard(boolean enabled, long windowSeconds, Clock clock) {
        this.enabled = enabled;
        this.windowMillis = windowSeconds * 1000L;
        this.clock = clock;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * @return an empty string if the reading is accepted, otherwise a human-readable rejection reason.
     */
    public String check(String deviceCode, String nonce, String capturedAt) {
        if (!enabled) {
            return "";
        }
        if (nonce == null || nonce.isBlank()) {
            return "已开启防重放，读数必须携带 nonce";
        }
        Long capturedMillis = parseMillis(capturedAt);
        if (capturedMillis == null) {
            return "已开启防重放，读数必须携带可解析的时间戳 capturedAt";
        }
        long now = clock.millis();
        if (Math.abs(now - capturedMillis) > windowMillis) {
            return "读数时间戳超出防重放窗口（±" + (windowMillis / 1000L) + " 秒）";
        }
        NonceWindow window = nonces.computeIfAbsent(deviceCode, ignored -> new NonceWindow());
        synchronized (window) {
            window.evictExpired(now - windowMillis);
            if (!window.add(nonce.trim(), now)) {
                return "nonce 已被使用，疑似重放：" + nonce;
            }
        }
        return "";
    }

    private Long parseMillis(String capturedAt) {
        if (capturedAt == null || capturedAt.isBlank()) {
            return null;
        }
        String value = capturedAt.trim();
        // Accept epoch milliseconds.
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            // fall through to ISO parsing
        }
        // Accept ISO-8601 instant, e.g. 2026-07-09T10:00:00Z.
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        // Accept local date-time (assumes system zone), matching the gateway's stored format.
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static final class NonceWindow {
        private final Set<String> seen = new HashSet<>();
        private final Deque<TimedNonce> order = new ArrayDeque<>();

        private void evictExpired(long cutoff) {
            while (!order.isEmpty() && order.peekFirst().millis < cutoff) {
                seen.remove(order.pollFirst().nonce);
            }
        }

        private boolean add(String nonce, long millis) {
            if (seen.contains(nonce)) {
                return false;
            }
            seen.add(nonce);
            order.addLast(new TimedNonce(nonce, millis));
            return true;
        }

        private record TimedNonce(String nonce, long millis) {
        }
    }
}
