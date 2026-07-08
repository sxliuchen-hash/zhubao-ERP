package com.zhubao.erp.hardware.app;

import com.zhubao.erp.hardware.domain.HardwareDeviceStatus;
import com.zhubao.erp.hardware.infra.InMemoryHardwarePocRecordRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HardwareIntegrationServiceTest {

    @Test
    void exposesHardwareFallbackAndHealthBaseline() {
        HardwareIntegrationService service = new HardwareIntegrationService(new InMemoryHardwarePocRecordRepository());

        assertThat(service.findDevices()).hasSize(5);
        assertThat(service.fallbackRequiredCount()).isEqualTo(4);
        assertThat(service.averageHealth()).isEqualTo(70);
        assertThat(service.findDevices())
                .anySatisfy(device -> {
                    assertThat(device.code()).isEqualTo("SCAN-PC-01");
                    assertThat(device.status()).isEqualTo(HardwareDeviceStatus.ONLINE);
                });
        assertThat(service.findTasks())
                .anySatisfy(task -> assertThat(task.nextAction()).contains("人工上传"));
        assertThat(service.findPocRecords()).hasSize(4);
        assertThat(service.blockedPocCount()).isEqualTo(1);
        assertThat(service.findPocRecords())
                .anySatisfy(record -> {
                    assertThat(record.code()).isEqualTo("POC-SCAN-20260706-001");
                    assertThat(record.nextAction()).contains("弱光 POC");
                });
    }
}
