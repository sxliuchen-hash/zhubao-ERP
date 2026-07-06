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
    void resolvesWorkOrderCodeForPwaReport() {
        ScanResolveResponse response = parser.resolve("JG|WO|CC-20260706-001-JL-ZD001", "pwa_report");

        assertThat(response.objectType()).isEqualTo(ScanObjectType.WORK_ORDER);
        assertThat(response.code()).isEqualTo("CC-20260706-001-JL-ZD001");
        assertThat(response.nextAction()).isEqualTo("report");
        assertThat(response.allowed()).isTrue();
    }

    @Test
    void rejectsUnknownQrContent() {
        assertThatThrownBy(() -> parser.resolve("bad-content", "pwa_report"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未识别");
    }
}
