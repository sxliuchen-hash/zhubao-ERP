package com.zhubao.erp.gateway.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.error.DeviceAuthenticationException;
import com.zhubao.erp.gateway.domain.SubmitDeviceReadingCommand;
import com.zhubao.erp.gateway.infra.InMemoryDeviceReadingRepository;
import com.zhubao.erp.gateway.infra.InMemoryGatewayDeviceRepository;
import com.zhubao.erp.security.app.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceGatewayServiceTest {

    private static final String DEVICE_KEY = "unit-test-device-key";

    private DeviceGatewayService service;

    @BeforeEach
    void setUp() {
        PasswordHasher passwordHasher = new PasswordHasher();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zhubao.gateway.demo-device-key", DEVICE_KEY);
        InMemoryGatewayDeviceRepository deviceRepository =
                new InMemoryGatewayDeviceRepository(passwordHasher, environment);
        InMemoryDeviceReadingRepository readingRepository = new InMemoryDeviceReadingRepository();
        service = new DeviceGatewayService(
                deviceRepository, readingRepository, new DeviceReadingNormalizer(), passwordHasher);
    }

    @Test
    void ingestsAuthenticatedScaleReading() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "ST,GS,+000123.45,g"));

        assertThat(reading.readingCode()).startsWith("DR-SCALE-01-");
        assertThat(reading.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(reading.normalizedValue()).isEqualTo("123.45");
        assertThat(reading.normalizedUnit()).isEqualTo("g");
        assertThat(service.recentReadings(10)).hasSize(1);
    }

    @Test
    void rejectsUnknownDevice() {
        assertThatThrownBy(() -> service.ingest(command("SCALE-99", "SCALE_WEIGHT", "88.6g")))
                .isInstanceOf(DeviceAuthenticationException.class);
    }

    @Test
    void rejectsWrongDeviceKey() {
        SubmitDeviceReadingCommand command = new SubmitDeviceReadingCommand(
                "SCALE-01", "wrong-key", "SCALE_WEIGHT", "88.6g", "", "", "");

        assertThatThrownBy(() -> service.ingest(command))
                .isInstanceOf(DeviceAuthenticationException.class);
    }

    @Test
    void rejectsReadingTypeNotAcceptedByDevice() {
        assertThatThrownBy(() -> service.ingest(command("SCALE-01", "SCAN_EVENT", "ZD-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不接受读数类型");
    }

    @Test
    void rejectsUnknownReadingType() {
        assertThatThrownBy(() -> service.ingest(command("SCALE-01", "NOT_A_TYPE", "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的读数类型");
    }

    @Test
    void summaryCountsReadingStatuses() {
        service.ingest(command("SCALE-01", "SCALE_WEIGHT", "ST,GS,+000123.45,g"));
        service.ingest(command("SCALE-01", "SCALE_WEIGHT", "US,GS,+000120.00,g"));
        service.ingest(command("SCALE-01", "SCALE_WEIGHT", "garbled"));

        var summary = service.summary();
        assertThat(summary.totalDevices()).isEqualTo(4);
        assertThat(summary.enabledDevices()).isEqualTo(4);
        assertThat(summary.readyReadings()).isEqualTo(1);
        assertThat(summary.warningReadings()).isEqualTo(1);
        assertThat(summary.blockedReadings()).isEqualTo(1);
    }

    @Test
    void filtersReadingsByDevice() {
        service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));
        service.ingest(command("SCAN-PC-01", "SCAN_EVENT", "WO|ZD-20260706-001|PROC=POLISH"));

        assertThat(service.readingsByDevice("SCAN-PC-01", 10)).hasSize(1);
        assertThat(service.readingsByDevice("SCAN-PC-01", 10).get(0).normalizedCode())
                .isEqualTo("ZD-20260706-001");
    }

    private SubmitDeviceReadingCommand command(String deviceCode, String type, String payload) {
        return new SubmitDeviceReadingCommand(deviceCode, DEVICE_KEY, type, payload, "", "", "");
    }
}
