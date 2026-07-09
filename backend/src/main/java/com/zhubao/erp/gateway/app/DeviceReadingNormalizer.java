package com.zhubao.erp.gateway.app;

import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.domain.DeviceReadingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapts raw payloads from low-cost hardware into structured, normalized values.
 *
 * <p>The design goal is to accept the plainest possible output that a cheap device / bridge can
 * produce, so no vendor SDK is required. Each reading type has its own tolerant parser that
 * degrades to {@link DeviceReadingStatus#WARNING} or {@link DeviceReadingStatus#BLOCKED} instead
 * of throwing, keeping the raw payload available for manual recovery.</p>
 */
@Component
public class DeviceReadingNormalizer {

    // Matches common electronic-scale frames such as "ST,GS,+000123.45,g", "US,GS,+000123.45,g" or a bare "123.45g".
    private static final Pattern SCALE_FRAME = Pattern.compile(
            "^(?<stability>ST|US)?[,\\s]*(?:GS|NT)?[,\\s]*(?<sign>[+-]?)(?<value>\\d+(?:\\.\\d+)?)[,\\s]*(?<unit>kg|g|ct|oz)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_WEIGHT = Pattern.compile(
            "^(?<sign>[+-]?)(?<value>\\d+(?:\\.\\d+)?)[,\\s]*(?<unit>kg|g|ct|oz)?$",
            Pattern.CASE_INSENSITIVE);

    public NormalizationResult normalize(DeviceReadingType type, String rawPayload) {
        String payload = rawPayload == null ? "" : rawPayload.trim();
        if (payload.isEmpty()) {
            return blocked("空载荷", "", "");
        }
        return switch (type) {
            case SCALE_WEIGHT -> normalizeScale(payload);
            case SCAN_EVENT -> normalizeScan(payload);
            case PRINT_ACK -> normalizePrintAck(payload);
            case VIDEO_CLIP_MARK -> normalizeVideoMark(payload);
            case HEARTBEAT -> new NormalizationResult(
                    DeviceReadingStatus.READY, "alive", "", "", "设备心跳正常");
        };
    }

    private NormalizationResult normalizeScale(String payload) {
        Matcher matcher = SCALE_FRAME.matcher(payload);
        boolean stable = true;
        String value = null;
        String unit = null;
        if (matcher.matches()) {
            String stability = matcher.group("stability");
            stable = stability == null || "ST".equalsIgnoreCase(stability);
            value = matcher.group("sign") + matcher.group("value");
            unit = matcher.group("unit");
        } else {
            Matcher bare = BARE_WEIGHT.matcher(payload);
            if (bare.matches()) {
                value = bare.group("sign") + bare.group("value");
                unit = bare.group("unit");
            }
        }
        if (value == null) {
            return blocked("无法解析称重报文，需人工读数", "", "");
        }
        BigDecimal weight;
        try {
            weight = new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return blocked("称重数值格式非法：" + value, "", "");
        }
        if (weight.signum() <= 0) {
            return new NormalizationResult(
                    DeviceReadingStatus.WARNING, weight.toPlainString(), normalizeUnit(unit), "",
                    "称重结果非正数，需人工确认");
        }
        String normalizedUnit = normalizeUnit(unit);
        if (!stable) {
            return new NormalizationResult(
                    DeviceReadingStatus.WARNING, weight.toPlainString(), normalizedUnit, "",
                    "读数未稳定(US)，建议等待稳定后重发或人工确认");
        }
        return new NormalizationResult(
                DeviceReadingStatus.READY, weight.toPlainString(), normalizedUnit, "",
                "称重读数已稳定");
    }

    private NormalizationResult normalizeScan(String payload) {
        // Accept both the pipe-delimited business format "WO|ZD-...|PROC=POLISH" and a bare code.
        String code = payload;
        int firstPipe = payload.indexOf('|');
        if (firstPipe >= 0) {
            String[] parts = payload.split("\\|");
            if (parts.length >= 2 && !parts[1].isBlank()) {
                code = parts[1].trim();
            }
        }
        if (code.isBlank()) {
            return blocked("扫码载荷为空，需人工输入编码", "", "");
        }
        return new NormalizationResult(
                DeviceReadingStatus.READY, "", "", code, "扫码编码已解析");
    }

    private NormalizationResult normalizePrintAck(String payload) {
        String upper = payload.toUpperCase();
        boolean ok = upper.startsWith("OK") || upper.contains("SUCCESS");
        int pipe = payload.indexOf('|');
        String labelCode = pipe >= 0 && pipe + 1 < payload.length() ? payload.substring(pipe + 1).trim() : "";
        if (ok) {
            return new NormalizationResult(
                    DeviceReadingStatus.READY, "OK", "", labelCode, "标签打印成功回执");
        }
        return new NormalizationResult(
                DeviceReadingStatus.WARNING, "FAIL", "", labelCode,
                "打印回执非成功状态，需补打或人工打印 PDF 标签");
    }

    private NormalizationResult normalizeVideoMark(String payload) {
        // Expected: "START|<channel>" or "END|<channel>".
        String[] parts = payload.split("\\|");
        String mark = parts[0].trim().toUpperCase();
        String channel = parts.length >= 2 ? parts[1].trim() : "";
        if (!"START".equals(mark) && !"END".equals(mark)) {
            return blocked("视频标记需为 START 或 END", "", channel);
        }
        return new NormalizationResult(
                DeviceReadingStatus.READY, mark, "", channel, "视频截取标记已记录");
    }

    private String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "g";
        }
        return unit.toLowerCase();
    }

    private NormalizationResult blocked(String message, String unit, String code) {
        return new NormalizationResult(DeviceReadingStatus.BLOCKED, "", unit, code, message);
    }

    /**
     * Structured normalization outcome. {@code value} carries numeric-like results (weight, mark),
     * {@code code} carries identifier-like results (scanned code, label code, channel).
     */
    public record NormalizationResult(
            DeviceReadingStatus status,
            String value,
            String unit,
            String code,
            String message
    ) {
    }
}
