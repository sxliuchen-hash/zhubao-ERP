package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.OrderChangeStatus;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryOrderChangeRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderChangeServiceTest {

    private InMemoryBusinessDocumentRepository documents;
    private OrderChangeService service;
    private AuthUserAccount receiver;
    private AuthUserAccount finance;

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        StockLedgerService ledger = new StockLedgerService(new InMemoryStockLedgerRepository());
        new BusinessOperationService(ledger, null, documents).confirmInbound(new ConfirmInboundCommand(
                "MD-CHANGE-001", 1001L, 1001L, "AU9999", "2026-10-08", "顺丰", "SF-CHANGE-001", "",
                "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                new BigDecimal("10"), "件", new BigDecimal("20"), "何琳", "", "原单",
                "内壁刻字", "2026-10-20", "恒信珠宝"
        ));
        documents.saveProcessStationHandoff(new ProcessStationHandoff(
                "MD-CHANGE-001", "雕刻", "雕刻工", "STK-1", "2026-10-09T10:00:00"
        ));
        service = new OrderChangeService(
                new InMemoryOrderChangeRepository(),
                documents,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository())
        );
        receiver = account("收货员", RoleCode.RECEIVER);
        finance = account("财务员", RoleCode.FINANCE_STATISTICIAN);
    }

    @Test
    void applyKeepsExistingHandoffAndRequiresFinanceConfirmation() {
        var draft = service.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-draft-001", receiver);
        assertThatThrownBy(() -> service.apply(draft.id(), "change-apply-001", receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("财务确认后才能生效");
        service.submitFinance(draft.id(), receiver);
        assertThatThrownBy(() -> service.confirmFinance(draft.id(), true, null, null, "金额不变", receiver))
                .isInstanceOf(PermissionDeniedException.class);
        service.confirmFinance(draft.id(), true, null, null, "金额不变", finance);
        var applied = service.apply(draft.id(), "change-apply-001", receiver);
        assertThat(applied.status()).isEqualTo(OrderChangeStatus.APPLIED);
        assertThat(applied.amountUnchanged()).isTrue();
        assertThat(documents.listProcessStationHandoffs("MD-CHANGE-001")).hasSize(1);
        assertThat(service.apply(draft.id(), "change-apply-001", receiver).status()).isEqualTo(OrderChangeStatus.APPLIED);
        assertThat(service.timeline("MD-CHANGE-001", receiver)).anyMatch(line -> line.contains("改为外圈刻字"));
        assertThat(new ScanCodeParser().labelVersion("JG|mother|MD-CHANGE-001")).isEmpty();
        assertThat(new ScanCodeParser().labelVersion("JG|mother|MD-CHANGE-001|2|LBL1")).isEqualTo("2");
        assertThat(new ScanCodeParser().resolve("JG|mother|MD-CHANGE-001|2|LBL1", "lookup").code()).isEqualTo("MD-CHANGE-001");
    }

    private AuthUserAccount account(String operator, RoleCode role) {
        return new AuthUserAccount(
                operator, "hash", operator, role, DataScope.ALL, true, true,
                Instant.now(), 0, null, List.of(), false, false, List.of()
        );
    }
}
