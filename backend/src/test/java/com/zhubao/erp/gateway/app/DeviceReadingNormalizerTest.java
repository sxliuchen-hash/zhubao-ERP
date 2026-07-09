package com.zhubao.erp.gateway.app;

import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.domain.DeviceReadingType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceReadingNormalizerTest {

    private final DeviceReadingNormalizer normalizer = new DeviceReadingNormalizer();

    @Test
    void parsesStableScaleFrame() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.SCALE_WEIGHT, "ST,GS,+000123.45,g");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(result.value()).isEqualTo("123.45");
        assertThat(result.unit()).isEqualTo("g");
    }

    @Test
    void flagsUnstableScaleFrameAsWarning() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.SCALE_WEIGHT, "US,GS,+000120.00,g");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.WARNING);
        assertThat(result.value()).isEqualTo("120.00");
    }

    @Test
    void parsesBareWeightPayload() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.SCALE_WEIGHT, "88.6g");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(result.value()).isEqualTo("88.6");
        assertThat(result.unit()).isEqualTo("g");
    }

    @Test
    void blocksUnparseableScalePayload() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.SCALE_WEIGHT, "error");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.BLOCKED);
        assertThat(result.value()).isEmpty();
    }

    @Test
    void extractsCodeFromPipeDelimitedScan() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.SCAN_EVENT, "WO|ZD-20260706-001|PROC=POLISH");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(result.code()).isEqualTo("ZD-20260706-001");
    }

    @Test
    void acceptsBareScanCode() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.SCAN_EVENT, "CC-20260706-001-JL");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(result.code()).isEqualTo("CC-20260706-001-JL");
    }

    @Test
    void marksFailedPrintAckAsWarning() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.PRINT_ACK, "FAIL|PDF-LABEL-MD-20260706-001");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.WARNING);
        assertThat(result.code()).isEqualTo("PDF-LABEL-MD-20260706-001");
    }

    @Test
    void acceptsVideoStartMark() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.VIDEO_CLIP_MARK, "START|ch01");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(result.value()).isEqualTo("START");
        assertThat(result.code()).isEqualTo("ch01");
    }

    @Test
    void blocksInvalidVideoMark() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.VIDEO_CLIP_MARK, "PAUSE|ch01");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.BLOCKED);
    }

    @Test
    void heartbeatIsAlwaysReady() {
        DeviceReadingNormalizer.NormalizationResult result =
                normalizer.normalize(DeviceReadingType.HEARTBEAT, "ping");

        assertThat(result.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(result.value()).isEqualTo("alive");
    }
}
