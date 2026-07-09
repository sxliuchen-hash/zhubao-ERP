package com.zhubao.erp.gateway.app;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayReplayGuardTest {

    @Test
    void disabledGuardAcceptsEverything() {
        GatewayReplayGuard guard = new GatewayReplayGuard(false, 300);
        assertThat(guard.check("SCALE-01", null, null)).isEmpty();
        assertThat(guard.check("SCALE-01", null, null)).isEmpty();
    }

    @Test
    void requiresNonceAndTimestampWhenEnabled() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        GatewayReplayGuard guard = new GatewayReplayGuard(true, 300, fixedClock(now));

        assertThat(guard.check("SCALE-01", null, isoAt(now.get()))).contains("必须携带 nonce");
        assertThat(guard.check("SCALE-01", "n1", null)).contains("必须携带可解析的时间戳");
    }

    @Test
    void acceptsFreshNonceAndRejectsReplay() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        GatewayReplayGuard guard = new GatewayReplayGuard(true, 300, fixedClock(now));
        String timestamp = String.valueOf(now.get());

        assertThat(guard.check("SCALE-01", "nonce-1", timestamp)).isEmpty();
        assertThat(guard.check("SCALE-01", "nonce-1", timestamp)).contains("疑似重放");
        // A different device may reuse the same nonce value.
        assertThat(guard.check("SCAN-PC-01", "nonce-1", timestamp)).isEmpty();
    }

    @Test
    void rejectsStaleTimestamp() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        GatewayReplayGuard guard = new GatewayReplayGuard(true, 300, fixedClock(now));
        long staleMillis = now.get() - 301_000L;

        assertThat(guard.check("SCALE-01", "nonce-stale", String.valueOf(staleMillis)))
                .contains("超出防重放窗口");
    }

    @Test
    void releasesNonceAfterWindowElapses() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        GatewayReplayGuard guard = new GatewayReplayGuard(true, 10, fixedClock(now));

        assertThat(guard.check("SCALE-01", "nonce-x", String.valueOf(now.get()))).isEmpty();
        // Advance beyond the window; the old nonce is evicted and can be accepted again with a fresh timestamp.
        now.addAndGet(11_000L);
        assertThat(guard.check("SCALE-01", "nonce-x", String.valueOf(now.get()))).isEmpty();
    }

    @Test
    void acceptsIsoInstantTimestamp() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        GatewayReplayGuard guard = new GatewayReplayGuard(true, 300, fixedClock(now));

        assertThat(guard.check("SCALE-01", "nonce-iso", isoAt(now.get()))).isEmpty();
    }

    private static String isoAt(long millis) {
        return Instant.ofEpochMilli(millis).toString();
    }

    private static Clock fixedClock(AtomicLong millis) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis.get());
            }

            @Override
            public long millis() {
                return millis.get();
            }
        };
    }
}
