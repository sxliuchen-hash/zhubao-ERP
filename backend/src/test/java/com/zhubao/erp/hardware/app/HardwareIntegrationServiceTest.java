package com.zhubao.erp.hardware.app;

import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import com.zhubao.erp.hardware.infra.InMemoryHardwarePocRecordRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class HardwareIntegrationServiceTest {
    @Test
    void 空库没有固定设备或现场记录() {
        var service = new HardwareIntegrationService(new InMemoryHardwarePocRecordRepository(), new InMemoryDocumentManagementRepository());
        assertThat(service.findPocRecords()).isEmpty();
        assertThat(service.findPocEvidence()).isEmpty();
        assertThat(service.blockedPocCount()).isZero();
    }

    @Test
    void 缺少原始证据的现场记录不能显示已验证() {
        var record = new HardwarePocRecord("POC-TEST", "TEST", "现场称重", "2026-10-28", "阻断", "", "待复测", "核验人", "补证据");
        var service = new HardwareIntegrationService(() -> List.of(record), new InMemoryDocumentManagementRepository());
        assertThat(service.blockedPocCount()).isEqualTo(1);
        assertThat(service.findPocEvidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.pocCode()).isEqualTo(record.code());
            assertThat(evidence.status()).isEqualTo("待补证据");
            assertThat(evidence.missingEvidence()).isEqualTo(1);
            assertThat(evidence.latestUploadedAt()).isEmpty();
        });
    }
}
