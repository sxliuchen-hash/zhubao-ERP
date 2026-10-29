package com.zhubao.erp.gateway.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.gateway.domain.ConsumeReadingCommand;
import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.domain.ReadingConsumption;
import com.zhubao.erp.gateway.domain.RegisterGatewayDeviceCommand;
import com.zhubao.erp.gateway.error.DeviceAuthenticationException;
import com.zhubao.erp.gateway.error.GatewayRateLimitException;
import com.zhubao.erp.gateway.error.GatewayReplayException;
import com.zhubao.erp.gateway.domain.SubmitDeviceReadingCommand;
import com.zhubao.erp.gateway.infra.InMemoryDeviceReadingRepository;
import com.zhubao.erp.gateway.infra.InMemoryGatewayDeviceRepository;
import com.zhubao.erp.gateway.infra.InMemoryReadingConsumptionRepository;
import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceGatewayServiceTest {

    private static final String DEVICE_KEY = "unit-test-device-key";

    private DeviceGatewayService service;

    @BeforeEach
    void setUp() {
        PasswordHasher passwordHasher = new PasswordHasher();
        InMemoryGatewayDeviceRepository deviceRepository =
                devices(passwordHasher);
        InMemoryDeviceReadingRepository readingRepository = new InMemoryDeviceReadingRepository();
        InMemoryReadingConsumptionRepository consumptionRepository = new InMemoryReadingConsumptionRepository();
        stockLedgerService = new StockLedgerService(new InMemoryStockLedgerRepository());
        businessOperationService = new BusinessOperationService(stockLedgerService, null, null, com.zhubao.erp.business.BusinessWorkFixture.unit());
        service = new DeviceGatewayService(
                deviceRepository, readingRepository, consumptionRepository,
                new DeviceReadingNormalizer(), passwordHasher,
                new GatewayRateLimiter(1000, 60), new GatewayReplayGuard(false, 300),
                businessOperationService);
    }

    private static InMemoryGatewayDeviceRepository devices(PasswordHasher hasher) {
        var repository = new InMemoryGatewayDeviceRepository();
        for (String code : java.util.List.of("SCALE-01", "SCAN-PC-01", "PRN-LABEL-01", "CAM-UNPACK-01")) {
            var types = switch (code) {
                case "SCALE-01" -> java.util.List.of(com.zhubao.erp.gateway.domain.DeviceReadingType.SCALE_WEIGHT, com.zhubao.erp.gateway.domain.DeviceReadingType.HEARTBEAT);
                case "SCAN-PC-01" -> java.util.List.of(com.zhubao.erp.gateway.domain.DeviceReadingType.SCAN_EVENT, com.zhubao.erp.gateway.domain.DeviceReadingType.HEARTBEAT);
                case "PRN-LABEL-01" -> java.util.List.of(com.zhubao.erp.gateway.domain.DeviceReadingType.PRINT_ACK, com.zhubao.erp.gateway.domain.DeviceReadingType.HEARTBEAT);
                default -> java.util.List.of(com.zhubao.erp.gateway.domain.DeviceReadingType.VIDEO_CLIP_MARK, com.zhubao.erp.gateway.domain.DeviceReadingType.HEARTBEAT);
            };
            repository.save(new com.zhubao.erp.gateway.domain.GatewayDevice(code, "测试设备", types, hasher.hash(DEVICE_KEY), true, "测试工位", "人工录入", "2026-10-28"));
        }
        return repository;
    }

    private StockLedgerService stockLedgerService;
    private BusinessOperationService businessOperationService;

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

        ReadingConsumption consumption = service.consume(bridgeConsume(reading.readingCode(), "WEIGH_HANDOVER", "子工单", "ZD-20260706-001", "打包称重"), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false));

        assertThat(consumption.consumptionCode()).isEqualTo("RC-" + reading.readingCode());
        assertThat(consumption.operator()).isEqualTo("周凯");
        assertThat(consumption.role()).isEqualTo("OPERATOR");
        assertThat(consumption.businessRefCode()).isEqualTo("ZD-20260706-001");
        assertThat(service.recentConsumptions(10)).hasSize(1);
    }

    @Test
    void rejectsIntentThatDoesNotMatchReadingType() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));

        assertThatThrownBy(() -> service.consume(bridgeConsume(reading.readingCode(), "SCAN_TO_REPORT", "", "", ""), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能用于");
    }

    @Test
    void rejectsConsumingUnknownReading() {
        assertThatThrownBy(() -> service.consume(bridgeConsume("DR-NOPE", "DISMISS", "", "", ""), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到读数");
    }

    @Test
    void blockedReadingCanOnlyBeDismissed() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "garbled"));
        assertThat(reading.status()).isEqualTo(DeviceReadingStatus.BLOCKED);

        assertThatThrownBy(() -> service.consume(bridgeConsume(reading.readingCode(), "WEIGH_HANDOVER", "", "", ""), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能忽略");

        ReadingConsumption dismissed = service.consume(bridgeConsume(reading.readingCode(), "DISMISS", "", "", "无效报文"), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false));
        assertThat(dismissed.intent().name()).isEqualTo("DISMISS");
    }

    @Test
    void rejectsDoubleConsumption() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));
        service.consume(bridgeConsume(reading.readingCode(), "WEIGH_HANDOVER", "", "", ""), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false));

        assertThatThrownBy(() -> service.consume(bridgeConsume(reading.readingCode(), "WEIGH_HANDOVER", "", "", ""), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false)))
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
        DeviceGatewayService limitedService = new DeviceGatewayService(
                devices(passwordHasher),
                new InMemoryDeviceReadingRepository(),
                new InMemoryReadingConsumptionRepository(),
                new DeviceReadingNormalizer(),
                passwordHasher,
                new GatewayRateLimiter(2, 60),
                new GatewayReplayGuard(false, 300),
                new BusinessOperationService(new StockLedgerService(new InMemoryStockLedgerRepository()), null, null, com.zhubao.erp.business.BusinessWorkFixture.unit()));

        limitedService.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));
        limitedService.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.7g"));

        assertThatThrownBy(() -> limitedService.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.8g")))
                .isInstanceOf(GatewayRateLimitException.class)
                .hasMessageContaining("频率超过限制");

        // A different device is not affected by another device's quota.
        assertThat(limitedService.ingest(command("SCAN-PC-01", "SCAN_EVENT", "ZD-1")).status()).isNotNull();
    }

    @Test
    void enforcesReplayProtectionWhenEnabled() {
        PasswordHasher passwordHasher = new PasswordHasher();
        DeviceGatewayService guardedService = new DeviceGatewayService(
                devices(passwordHasher),
                new InMemoryDeviceReadingRepository(),
                new InMemoryReadingConsumptionRepository(),
                new DeviceReadingNormalizer(),
                passwordHasher,
                new GatewayRateLimiter(1000, 60),
                new GatewayReplayGuard(true, 300),
                new BusinessOperationService(new StockLedgerService(new InMemoryStockLedgerRepository()), null, null, com.zhubao.erp.business.BusinessWorkFixture.unit()));

        String timestamp = java.time.Instant.now().toString();
        SubmitDeviceReadingCommand first = new SubmitDeviceReadingCommand(
                "SCALE-01", DEVICE_KEY, "SCALE_WEIGHT", "88.6g", "", "", timestamp, "nonce-a");
        assertThat(guardedService.ingest(first).status()).isEqualTo(DeviceReadingStatus.READY);

        SubmitDeviceReadingCommand replay = new SubmitDeviceReadingCommand(
                "SCALE-01", DEVICE_KEY, "SCALE_WEIGHT", "88.6g", "", "", timestamp, "nonce-a");
        assertThatThrownBy(() -> guardedService.ingest(replay))
                .isInstanceOf(GatewayReplayException.class)
                .hasMessageContaining("疑似重放");

        SubmitDeviceReadingCommand missingNonce = new SubmitDeviceReadingCommand(
                "SCALE-01", DEVICE_KEY, "SCALE_WEIGHT", "88.6g", "", "", timestamp, null);
        assertThatThrownBy(() -> guardedService.ingest(missingNonce))
                .isInstanceOf(GatewayReplayException.class)
                .hasMessageContaining("必须携带 nonce");
    }

    @Test
    void fallsBackToReadingBusinessRefWhenCommandOmitsIt() {
        DeviceReading reading = service.ingest(new SubmitDeviceReadingCommand(
                "SCAN-PC-01", DEVICE_KEY, "SCAN_EVENT", "WO|ZD-20260706-009|PROC=POLISH", "子工单", "ZD-20260706-009", ""));

        ReadingConsumption consumption = service.consume(bridgeConsume(reading.readingCode(), "SCAN_TO_REPORT", "", "", ""), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false));

        assertThat(consumption.businessRefCode()).isEqualTo("ZD-20260706-009");
    }

    @Test
    void 未提供真实账号的设备交工不能绕过岗位校验() {
        // 先建立库存事实，拒绝无真实账号的设备交工。
        seedWip("MD-GW-001", "ZD-GW-001", "MAT-18K-RING", 1001L, "WIP-LINE-01");
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "ST,GS,+000123.45,g"));

        ConsumeReadingCommand consume = new ConsumeReadingCommand(
                reading.readingCode(), "WEIGH_HANDOVER", "子工单", "ZD-GW-001", "称重联动报工",
                1001L, 1001L, "MD-GW-001", "ZD-GW-001", "MAT-18K-RING",
                "WIP-LINE-01", "SEMI-FINISHED", new BigDecimal("10"), "抛光");

        int before = stockLedgerService.ledgers().size();
        assertThatThrownBy(() -> service.consume(consume, OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须登录并经过岗位校验");
        assertThat(stockLedgerService.ledgers()).hasSize(before);
        assertThat(service.recentConsumptions(10)).isEmpty();
    }

    @Test
    void weighHandoverWithoutPostingContextStaysPureBridgeRecord() {
        DeviceReading reading = service.ingest(command("SCALE-01", "SCALE_WEIGHT", "88.6g"));

        ReadingConsumption consumption = service.consume(bridgeConsume(reading.readingCode(), "WEIGH_HANDOVER", "", "", ""), OPERATOR, new com.zhubao.erp.security.domain.AuthUserAccount("operator", "", OPERATOR.operator(), OPERATOR.role(), OPERATOR.dataScope(), true, false));

        assertThat(consumption.posted()).isFalse();
        assertThat(consumption.ledgerCode()).isEmpty();
    }

    private void seedWip(String motherOrder, String workOrder, String material, long customerId, String warehouse) {
        stockLedgerService.transfer(new StockTransferCommand(
                "test_seed",
                workOrder,
                null,
                new StockDimension(OwnerType.CUSTOMER, customerId, customerId, motherOrder, workOrder,
                        material, warehouse, StockStatus.WIP),
                new BigDecimal("100"),
                new BigDecimal("500.00"),
                "seed wip"));
    }

    private SubmitDeviceReadingCommand command(String deviceCode, String type, String payload) {
        return new SubmitDeviceReadingCommand(deviceCode, DEVICE_KEY, type, payload, "", "", "");
    }

    private static ConsumeReadingCommand bridgeConsume(
            String readingCode, String intent, String refType, String refCode, String note) {
        return new ConsumeReadingCommand(
                readingCode, intent, refType, refCode, note,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void 报工成功而消费保存失败时复用读数请求不会重复交工() {
        var documents = new com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository();
        var unit = com.zhubao.erp.business.BusinessWorkFixture.unit();
        var operations = new BusinessOperationService(stockLedgerService, null, documents, unit);
        operations.confirmInbound(new com.zhubao.erp.business.domain.ConfirmInboundCommand(
                "MD-GATEWAY", 1001L, 1001L, "MAT", "2026-10-29", "", "", "", "收货", "收货", "戒托", "", null, "",
                "RAW", new BigDecimal("10"), "件", new BigDecimal("20"), "收货", "", "", "要求", "2026-11-30", "客户甲", "gateway-inbound-001"));
        new com.zhubao.erp.business.app.MotherOrderSplitService(documents, stockLedgerService).keep("MD-GATEWAY", "收货");
        var station = new com.zhubao.erp.business.app.ProcessStationService(documents, stockLedgerService, operations,
                new com.zhubao.erp.scan.app.ScanCodeParser(), null, unit);
        var consumptions = org.mockito.Mockito.spy(new InMemoryReadingConsumptionRepository());
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            if (first.getAndSet(false)) throw new IllegalStateException("消费保存失败");
            return invocation.callRealMethod();
        }).when(consumptions).save(org.mockito.ArgumentMatchers.any());
        var hasher = new PasswordHasher();
        var gateway = new DeviceGatewayService(devices(hasher), new InMemoryDeviceReadingRepository(), consumptions,
                new DeviceReadingNormalizer(), hasher, new GatewayRateLimiter(1000, 60), new GatewayReplayGuard(false, 300), operations, station);
        var account = new com.zhubao.erp.security.domain.AuthUserAccount("worker", "", "雕刻工", RoleCode.OPERATOR,
                DataScope.SELF, true, false).withAllowedProcesses(java.util.List.of("雕刻"));
        var reading = gateway.ingest(command("SCALE-01", "SCALE_WEIGHT", "20g"));
        var consume = new ConsumeReadingCommand(reading.readingCode(), "WEIGH_HANDOVER", "母单", "MD-GATEWAY", "交工",
                1001L, 1001L, "MD-GATEWAY", "MD-GATEWAY", "MAT", "RAW", "RAW", new BigDecimal("10"), "雕刻");
        assertThatThrownBy(() -> gateway.consume(consume, OPERATOR, account)).hasMessage("消费保存失败");
        int afterReport = stockLedgerService.ledgers().size();
        assertThat(documents.listProcessStationHandoffs("MD-GATEWAY")).hasSize(1);
        ReadingConsumption retried = gateway.consume(consume, OPERATOR, account);
        assertThat(retried.posted()).isTrue();
        assertThat(stockLedgerService.ledgers()).hasSize(afterReport);
        assertThat(documents.listProcessStationHandoffs("MD-GATEWAY")).hasSize(1);
        assertThat(gateway.recentConsumptions(10)).hasSize(1);
        assertThatThrownBy(() -> gateway.consume(consume, OPERATOR, null)).hasMessageContaining("必须登录");
    }
}
