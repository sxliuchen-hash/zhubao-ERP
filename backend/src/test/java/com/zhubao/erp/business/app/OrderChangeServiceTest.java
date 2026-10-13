package com.zhubao.erp.business.app;

import com.zhubao.erp.business.app.HandoffSaveProbe;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OrderChangeStatus;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryOrderChangeRepository;
import com.zhubao.erp.business.infra.InMemoryOrderRequirementRepository;
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

    @Test
    void nextStationLookupReadsAppliedRequirementWithoutRewritingHistory() {
        documents.saveMotherSplitState(new MotherSplitState(
                "MD-CHANGE-001", MotherSplitState.KEEP, "VC-1", "收货员", "2026-10-08T00:00:00"
        ));
        InMemoryOrderRequirementRepository requirements = new InMemoryOrderRequirementRepository();
        OrderChangeService effect = effectService(requirements, null);
        var draft = effect.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-effect-001", null, 0, "雕刻", receiver);
        effect.submitFinance(draft.id(), receiver);
        effect.confirmFinance(draft.id(), true, null, null, "金额不变", finance);
        var applied = effect.apply(draft.id(), "change-effect-apply", receiver);
        assertThat(applied.status()).isEqualTo(OrderChangeStatus.APPLIED);
        assertThat(documents.findMotherOrderByCode("MD-CHANGE-001").orElseThrow().processingRequirement()).isEqualTo("内壁刻字");
        assertThat(documents.listProcessStationHandoffs("MD-CHANGE-001")).hasSize(1);
        assertThat(requirements.listByMother("MD-CHANGE-001")).singleElement()
                .satisfies(round -> assertThat(round.processName()).isEqualTo("雕刻"));
        var station = new ProcessStationService(
                documents, new StockLedgerService(new InMemoryStockLedgerRepository()), null, new ScanCodeParser(),
                null, null, HandoffSaveProbe.NONE, requirements
        );
        AuthUserAccount leader = account("组长", RoleCode.WORKSHOP_LEADER);
        assertThat(station.lookup("MD-CHANGE-001", leader).processingRequirement()).isEqualTo("改为外圈刻字");
        assertThat(station.lookup("MD-CHANGE-001", leader).dueDate()).isEqualTo("2026-11-01");
    }

    @Test
    void staleExpectedVersionIsRejectedAndReplayDoesNotApplyTwice() {
        InMemoryOrderRequirementRepository requirements = new InMemoryOrderRequirementRepository();
        OrderChangeService effect = effectService(requirements, null);
        var first = effect.draft("MD-CHANGE-001", "", "第一次", "2026-11-01", "change-stale-a", receiver);
        var second = effect.draft("MD-CHANGE-001", "", "第二次", "2026-12-01", "change-stale-b", null, 0, "", receiver);
        effect.submitFinance(first.id(), receiver);
        effect.confirmFinance(first.id(), true, null, null, "金额不变", finance);
        effect.apply(first.id(), "change-stale-apply-a", receiver);
        effect.submitFinance(second.id(), receiver);
        effect.confirmFinance(second.id(), true, null, null, "金额不变", finance);
        assertThatThrownBy(() -> effect.apply(second.id(), "change-stale-apply-b", 0, receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本已变化");
        assertThat(effect.apply(first.id(), "change-stale-apply-a", receiver).status()).isEqualTo(OrderChangeStatus.APPLIED);
        assertThat(requirements.current("MD-CHANGE-001", "").orElseThrow().requirementText()).isEqualTo("第一次");
    }

    @Test
    void otherCustomerAndNonFinanceCannotSeeForeignOrderOrAmounts() {
        new BusinessOperationService(new StockLedgerService(new InMemoryStockLedgerRepository()), null, documents)
                .confirmInbound(new ConfirmInboundCommand(
                        "MD-OTHER", 2002L, 2002L, "AU9999", "2026-10-08", "顺丰", "SF-OTHER", "",
                        "别人", "别人", "戒托", "", null, "", "RAW-CUSTOMER",
                        new BigDecimal("1"), "件", new BigDecimal("2"), "别人", "", "原单",
                        "别人的要求", "2026-10-20", "另一客户"
                ));
        BusinessDataAccess access = new BusinessDataAccess(documents, new StockLedgerService(new InMemoryStockLedgerRepository()));
        OrderChangeService scoped = new OrderChangeService(
                new InMemoryOrderChangeRepository(), documents,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()),
                null, access, null, new InMemoryOrderRequirementRepository()
        );
        AuthUserAccount limited = new AuthUserAccount(
                "范围员", "hash", "范围员", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, true, true,
                Instant.now(), 0, null, List.of("1001"), false, false, List.of()
        );
        assertThatThrownBy(() -> scoped.timeline("MD-OTHER", limited)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> scoped.draft("MD-OTHER", "", "越权", "2026-11-01", "change-cross", limited))
                .isInstanceOf(PermissionDeniedException.class);
        var draft = scoped.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-money-view", limited);
        scoped.submitFinance(draft.id(), limited);
        scoped.confirmFinance(draft.id(), false, new BigDecimal("70"), new BigDecimal("8"),
                new BigDecimal("-30"), BigDecimal.ZERO, "FIN-X", "雕刻", "", "减额", finance);
        assertThat(scoped.present(draft.id(), limited).customerUnitPrice()).isEqualByComparingTo("0");
        assertThat(scoped.present(draft.id(), limited).receivableCode()).isEmpty();
        assertThat(scoped.present(draft.id(), finance).customerUnitPrice()).isEqualByComparingTo("70");
        assertThat(scoped.timeline("MD-CHANGE-001", limited)).noneMatch(line -> line.contains("客户单价") || line.contains("FIN-X"));
        assertThat(scoped.timeline("MD-CHANGE-001", finance)).anyMatch(line -> line.contains("客户单价:70"));
        AuthUserAccount carver = new AuthUserAccount(
                "雕刻工", "hash", "雕刻工", RoleCode.OPERATOR, DataScope.ALL, true, true,
                Instant.now(), 0, null, List.of(), false, false, List.of("雕刻")
        );
        assertThatThrownBy(() -> scoped.draft("MD-CHANGE-001", "", "改抛光", "2026-11-02", "change-process", null, 0, "抛光", carver))
                .isInstanceOf(PermissionDeniedException.class);
    }

    private OrderChangeService effectService(InMemoryOrderRequirementRepository requirements, BusinessDataAccess access) {
        return new OrderChangeService(
                new InMemoryOrderChangeRepository(),
                documents,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()),
                null,
                access,
                null,
                requirements
        );
    }

    private AuthUserAccount account(String operator, RoleCode role) {
        return new AuthUserAccount(
                operator, "hash", operator, role, DataScope.ALL, true, true,
                Instant.now(), 0, null, List.of(), false, false, List.of()
        );
    }
}
