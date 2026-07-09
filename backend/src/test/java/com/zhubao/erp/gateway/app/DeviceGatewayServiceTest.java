package com.zhubao.erp.gateway.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.gateway.domain.ConsumeReadingCommand;
import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.domain.ReadingConsumption;
import com.zhubao.erp.gateway.domain.RegisterGatewayDeviceCommand;
import com.zhubao.erp.gateway.error.DeviceAuthenticationException;
import com.zhubao.erp.gateway.error.GatewayRateLimitException;
import com.zhubao.erp.gateway.domain.SubmitDeviceReadingCommand;
import com.zhubao.erp.gateway.infra.InMemoryDeviceReadingRepository;
import com.zhubao.erp.gateway.infra.InMemoryGatewayDeviceRepository;
import com.zhubao.erp.gateway.infra.InMemoryReadingConsumptionRepository;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
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
        InMemoryReadingConsumptionRepository consumptionRepository = new InMemoryReadingConsumptionRepository();
        service = new DeviceGatewayService(
                deviceRepository, readingRepository, consumptionRepository,
                new DeviceReadingNormalizer(), passwordHasher,
                new GatewayRateLimiter(1000, 60));
    }

    private static final OperatorContext OPERATOR =
            new OperatorContext("周凯", RoleCode.OPERATOR, DataScope.SELF);

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

    @Test
    void consumesReadyScaleReadingIntoWeighHandover() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "ST,GS,+000123.45,g"));

        ReadingConsumption consumption = service.consume(
                new ConsumeReadingCommand(reading.readingCode(), "WEIGH_HANDOVER", "子工单", "ZD-20260706-001", "打包称重"),
                OPERATOR);

        assertThat(consumption.consumptionCode()).isEqualTo("RC-" + reading.readingCode());
        assertThat(consumption.operator()).isEqualTo("周凯");
        assertThat(consumption.role()).isEqualTo("OPERATOR");
        assertThat(consumption.businessRefCode()).isEqualTo("ZD-20260706-001");
        assertThat(service.recentConsumptions(10)).hasSize(1);
    }

    @Test
    void rejectsIntentThatDoesNotMatchReadingType() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));

        assertThatThrownBy(() -> service.consume(
                new ConsumeReadingCommand(reading.readingCode(), "SCAN_TO_REPORT", "", "", ""), OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能用于");
    }

    @Test
    void rejectsConsumingUnknownReading() {
        assertThatThrownBy(() -> service.consume(
                new ConsumeReadingCommand("DR-NOPE", "DISMISS", "", "", ""), OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到读数");
    }

    @Test
    void blockedReadingCanOnlyBeDismissed() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "garbled"));
        assertThat(reading.status()).isEqualTo(DeviceReadingStatus.BLOCKED);

        assertThatThrownBy(() -> service.consume(
                new ConsumeReadingCommand(reading.readingCode(), "WEIGH_HANDOVER", "", "", ""), OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能忽略");

        ReadingConsumption dismissed = service.consume(
                new ConsumeReadingCommand(reading.readingCode(), "DISMISS", "", "", "无效报文"), OPERATOR);
        assertThat(dismissed.intent().name()).isEqualTo("DISMISS");
    }

    @Test
    void rejectsDoubleConsumption() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));
        service.consume(new ConsumeReadingCommand(reading.readingCode(), "WEIGH_HANDOVER", "", "", ""), OPERATOR);

        assertThatThrownBy(() -> service.consume(
                new ConsumeReadingCommand(reading.readingCode(), "WEIGH_HANDOVER", "", "", ""), OPERATOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被消费");
    }

    @Test
    void registersNewDeviceAndAcceptsItsReadings() {
        service.registerDevice(new RegisterGatewayDeviceCommand(
                "SCALE-99", "二号称重台", java.util.List.of("SCALE_WEIGHT"), "new-scale-key", true, "二号台", "人工兜底"));

        DeviceReading reading = service.ingest(new SubmitDeviceReadingCommand(
                "SCALE-99", "new-scale-key", "SCALE_WEIGHT", "55.5g", "", "", ""));
        assertThat(reading.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(service.devices()).anyMatch(device -> device.deviceCode().equals("SCALE-99"));
    }

    @Test
    void requiresKeyWhenCreatingDevice() {
        assertThatThrownBy(() -> service.registerDevice(new RegisterGatewayDeviceCommand(
                "SCALE-98", "三号台", java.util.List.of("SCALE_WEIGHT"), "", true, "", "")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须提供设备密钥");
    }

    @Test
    void disabledDeviceRejectsReadings() {
        service.setDeviceEnabled("SCALE-01", false);
        assertThatThrownBy(() -> service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g")))
                .isInstanceOf(DeviceAuthenticationException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void rotatingKeyInvalidatesOldKey() {
        service.rotateDeviceKey("SCALE-01", "rotated-key-value");

        assertThatThrownBy(() -> service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g")))
                .isInstanceOf(DeviceAuthenticationException.class);
        DeviceReading reading = service.ingest(new SubmitDeviceReadingCommand(
                "SCALE-01", "rotated-key-value", "SCALE_WEIGHT", "88.6g", "", "", ""));
        assertThat(reading.status()).isEqualTo(DeviceReadingStatus.READY);
    }

    @Test
    void updatingDeviceKeepsKeyWhenBlank() {
        service.registerDevice(new RegisterGatewayDeviceCommand(
                "SCALE-01", "称重台改名", java.util.List.of("SCALE_WEIGHT", "HEARTBEAT"), "", true, "打包台", "兜底"));

        // Original demo key still works because a blank key preserves the hash.
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));
        assertThat(reading.status()).isEqualTo(DeviceReadingStatus.READY);
        assertThat(service.devices())
                .filteredOn(device -> device.deviceCode().equals("SCALE-01"))
                .allMatch(device -> device.deviceName().equals("称重台改名"));
    }

    @Test
    void enforcesRateLimitPerDevice() {
        PasswordHasher passwordHasher = new PasswordHasher();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zhubao.gateway.demo-device-key", DEVICE_KEY);
        DeviceGatewayService limitedService = new DeviceGatewayService(
                new InMemoryGatewayDeviceRepository(passwordHasher, environment),
                new InMemoryDeviceReadingRepository(),
                new InMemoryReadingConsumptionRepository(),
                new DeviceReadingNormalizer(),
                passwordHasher,
                new GatewayRateLimiter(2, 60));

        limitedService.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));
        limitedService.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.7g"));

        assertThatThrownBy(() -> limitedService.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.8g")))
                .isInstanceOf(GatewayRateLimitException.class)
                .hasMessageContaining("频率超过限制");

        // A different device is not affected by another device's quota.
        assertThat(limitedService.ingest(command("SCAN-PC-01", "SCAN_EVENT", "ZD-1")).status()).isNotNull();
    }

    @Test
    void fallsBackToReadingBusinessRefWhenCommandOmitsIt() {
        DeviceReading reading = service.ingest(new SubmitDeviceReadingCommand(
                "SCAN-PC-01", DEVICE_KEY, "SCAN_EVENT", "WO|ZD-20260706-009|PROC=POLISH", "子工单", "ZD-20260706-009", ""));

        ReadingConsumption consumption = service.consume(
                new ConsumeReadingCommand(reading.readingCode(), "SCAN_TO_REPORT", "", "", ""), OPERATOR);

        assertThat(consumption.businessRefCode()).isEqualTo("ZD-20260706-009");
    }

    private SubmitDeviceReadingCommand command(String deviceCode, String type, String payload) {
        return new SubmitDeviceReadingCommand(deviceCode, DEVICE_KEY, type, payload, "", "", "");
    }
}
