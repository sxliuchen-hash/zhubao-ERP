package com.zhubao.erp.gateway.app;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.gateway.domain.ConsumeReadingCommand;
import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.domain.DeviceReadingType;
import com.zhubao.erp.gateway.domain.GatewayDeviceRepository;
import com.zhubao.erp.gateway.infra.InMemoryDeviceReadingRepository;
import com.zhubao.erp.gateway.infra.InMemoryReadingConsumptionRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GatewayStationPostingTest {

    private final BusinessOperationService operations = mock(BusinessOperationService.class);
    private final ProcessStationService stations = mock(ProcessStationService.class);
    private final InMemoryDeviceReadingRepository readings = new InMemoryDeviceReadingRepository();
    private final InMemoryReadingConsumptionRepository consumptions = new InMemoryReadingConsumptionRepository();
    private final DeviceGatewayService gateway = new DeviceGatewayService(mock(GatewayDeviceRepository.class), readings,
            consumptions, new DeviceReadingNormalizer(), new PasswordHasher(), new GatewayRateLimiter(1000, 60),
            new GatewayReplayGuard(false, 300), operations, stations);
    private final AuthUserAccount account = new AuthUserAccount("worker", "hash", "会话抛光员", RoleCode.OPERATOR,
            DataScope.SELF, true, true).withAllowedProcesses(List.of("抛光"));
    private final OperatorContext context = new OperatorContext("请求伪造操作人", RoleCode.ADMIN, DataScope.ALL);

    @Test
    void postingUsesStationAndPreservesDeviceWeightAndAuditMetadata() {
        seedReading();
        when(stations.reportProduction(any(), eq(account), any())).thenReturn(new ProductionReportResult("ZD-GATEWAY", "STK-STATION",
                "WIP", "SEMI", StockStatus.WIP, StockStatus.SEMI_FINISHED,
                new BigDecimal("10"), new BigDecimal("123.45"), "抛光", account.operator(), "DR-STATION"));

        var consumed = gateway.consume(command(), context, account);

        ArgumentCaptor<ReportProductionCommand> captured = ArgumentCaptor.forClass(ReportProductionCommand.class);
        verify(stations).reportProduction(captured.capture(), eq(account), eq("gw-report-DR-STATION"));
        assertThat(captured.getValue().weight()).isEqualByComparingTo("123.45");
        assertThat(captured.getValue().machineNo()).isEqualTo("SCALE-STATION");
        assertThat(captured.getValue().handoffCode()).isEqualTo("DR-STATION");
        assertThat(captured.getValue().operator()).isEqualTo(account.operator());
        assertThat(consumed.operator()).isEqualTo(account.operator());
        assertThat(consumed.ledgerCode()).isEqualTo("STK-STATION");
        verifyNoInteractions(operations);
    }

    @Test
    void stationRuleRejectionLeavesReadingUnconsumed() {
        seedReading();
        when(stations.reportProduction(any(), eq(account), any())).thenThrow(new BusinessException("上一道工序还没交"));

        assertThatThrownBy(() -> gateway.consume(command(), context, account))
                .isInstanceOf(BusinessException.class).hasMessageContaining("上一道工序还没交");
        assertThat(consumptions.findRecent(10)).isEmpty();
        verifyNoInteractions(operations);
    }

    private void seedReading() {
        readings.save(new DeviceReading("DR-STATION", "SCALE-STATION", DeviceReadingType.SCALE_WEIGHT, "123.45g",
                "123.45", "g", "", DeviceReadingStatus.READY, "", "子工单", "ZD-GATEWAY", "", "2041-01-01T08:00:00"));
    }

    private ConsumeReadingCommand command() {
        return new ConsumeReadingCommand("DR-STATION", "WEIGH_HANDOVER", "子工单", "ZD-GATEWAY", "称重交付",
                1001L, 1001L, "MD-GATEWAY", "ZD-GATEWAY", "AU9999", "WIP", "SEMI", new BigDecimal("10"), "抛光");
    }
}
