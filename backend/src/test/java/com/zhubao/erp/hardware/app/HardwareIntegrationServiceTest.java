package com.zhubao.erp.hardware.app;

import com.zhubao.erp.hardware.domain.HardwareDeviceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HardwareIntegrationServiceTest {

    @Test
    void exposesHardwareFallbackAndHealthBaseline() {
        HardwareIntegrationService service = new HardwareIntegrationService();

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
    }
}
