package com.zhubao.erp.scan.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.scan.domain.ScanObjectType;
import com.zhubao.erp.scan.domain.ScanResolveResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanCodeParserTest {

    private final ScanCodeParser parser = new ScanCodeParser();

    @Test
    void resolvesWorkOrderLabelForPwaReport() {
        ScanResolveResponse response = parser.resolve("JG|work_order|CC-20260706-001-JL-ZD001", "pwa_report");

        assertThat(response.objectType()).isEqualTo(ScanObjectType.WORK_ORDER);
        assertThat(response.code()).isEqualTo("CC-20260706-001-JL-ZD001");
        assertThat(response.nextAction()).isEqualTo("report");
        assertThat(response.allowed()).isTrue();
    }

    @Test
    void keepsLegacyWorkOrderToken() {
        ScanResolveResponse response = parser.resolve("JG|WO|CC-OLD-001", "pwa_report");

        assertThat(response.objectType()).isEqualTo(ScanObjectType.WORK_ORDER);
        assertThat(response.nextAction()).isEqualTo("report");
    }

    @Test
    void resolvesMotherLabelAndCourierNumber() {
        assertThat(parser.resolve("JG|mother|MD-20261008-001", "lookup").objectType())
                .isEqualTo(ScanObjectType.MOTHER_ORDER);
        ScanResolveResponse waybill = parser.resolve("sf 1048329001", "lookup");
        assertThat(waybill.objectType()).isEqualTo(ScanObjectType.EXPRESS_NO);
        assertThat(waybill.code()).isEqualTo("SF1048329001");
        assertThat(waybill.nextAction()).isEqualTo("intake");
        assertThat(parser.resolve("https://example.com/sf?mailNo=YT1234567890", "lookup").code())
                .isEqualTo("YT1234567890");
    }

    @Test
    void rejectsUnknownQrContent() {
        assertThatThrownBy(() -> parser.resolve("bad-content", "pwa_report"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未识别");
        assertThatThrownBy(() -> parser.resolve("HELLOWORLD", "lookup"))
                .isInstanceOf(BusinessException.class);
    }
}
