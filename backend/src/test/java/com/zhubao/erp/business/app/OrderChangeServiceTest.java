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
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void lostDraftResponseReplaysTheSameChange() {
        var first = service.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-lost-0001", receiver);
        var second = service.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-lost-0001", receiver);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(service.list("MD-CHANGE-001", receiver)).hasSize(1);

        AuthUserAccount other = account("另一收货", RoleCode.RECEIVER);
        assertThatThrownBy(() -> service.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-lost-0001", other))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("跨账号");
        assertThatThrownBy(() -> service.draft("MD-CHANGE-001", "", "改为另一要求", "2026-11-01", "change-lost-0001", receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThat(service.list("MD-CHANGE-001", receiver)).hasSize(1);
        assertThat(service.present(first.id(), receiver).newRequirement()).isEqualTo("改为外圈刻字");
    }

    @Test
    void applyCannotRewriteTheBaselineConfirmedByFinance() {
        var first = service.draft("MD-CHANGE-001", "", "第一次", "2026-11-01", "change-base-a001", receiver);
        var second = service.draft("MD-CHANGE-001", "", "第二次", "2026-11-02", "change-base-b001", receiver);
        service.submitFinance(first.id(), receiver);
        service.submitFinance(second.id(), receiver);
        approve(service, first.id());
        approve(service, second.id());
        service.apply(first.id(), "change-base-apply-a", receiver);
        assertThatThrownBy(() -> service.apply(second.id(), "change-base-apply-b", 1, receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能改写财务确认时固定的基准版本");
        assertThat(service.present(second.id(), receiver).status()).isEqualTo(OrderChangeStatus.BUSINESS_APPROVED);
    }

    @Test
    void secondChangeInheritsTheEffectiveRouteInsteadOfTheOriginalOrder() {
        documents.saveMotherSplitState(new MotherSplitState(
                "MD-CHANGE-001", MotherSplitState.KEEP, "VC-1", "收货员", "2026-10-08T00:00:00"
        ));
        InMemoryOrderRequirementRepository requirements = new InMemoryOrderRequirementRepository();
        OrderChangeService effect = effectService(requirements, null);
        var first = effect.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-route-1001", "雕刻、镶嵌", 0, "", receiver);
        effect.submitFinance(first.id(), receiver);
        approve(effect, first.id());
        effect.apply(first.id(), "change-route-apply", receiver);
        var second = effect.draft("MD-CHANGE-001", "", "", "2026-12-01", "change-route-2001", null, null, "", receiver);
        assertThat(second.oldRequirement()).isEqualTo("改为外圈刻字");
        assertThat(second.newRequirement()).isEqualTo("改为外圈刻字");
        assertThat(second.oldRoute()).isEqualTo("雕刻、镶嵌");
        assertThat(second.newRoute()).isEqualTo("雕刻、镶嵌");
        assertThat(second.newDueDate()).isEqualTo("2026-12-01");
        assertThat(second.expectedVersion()).isEqualTo(first.versionNo());
    }

    @Test
    void newRouteChecksEveryAffectedProcess() {
        AuthUserAccount carver = new AuthUserAccount(
                "雕刻工", "hash", "雕刻工", RoleCode.OPERATOR, DataScope.ALL, true, true,
                Instant.now(), 0, null, List.of(), false, false, List.of("雕刻")
        );
        assertThatThrownBy(() -> service.draft(
                "MD-CHANGE-001", "", "改路线", "2026-11-03", "change-route-deny", "雕刻、抛光", 0, "", carver))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("抛光");
    }

    @Test
    void concurrentDraftsWithTheSameRequestShareOneStoredId() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> left = pool.submit(() -> service.draft(
                    "MD-CHANGE-001", "", "并发改单", "2026-11-04", "change-race-0001", receiver).id());
            Future<String> right = pool.submit(() -> service.draft(
                    "MD-CHANGE-001", "", "并发改单", "2026-11-04", "change-race-0001", receiver).id());
            assertThat(left.get(10, TimeUnit.SECONDS)).isEqualTo(right.get(10, TimeUnit.SECONDS));
            assertThat(service.list("MD-CHANGE-001", receiver)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reworkRoundCanBeReportedWithoutReplacingTheOriginalHandoff() {
        InMemoryBusinessDocumentRepository localDocuments = new InMemoryBusinessDocumentRepository();
        InMemoryStockLedgerRepository stocks = new InMemoryStockLedgerRepository();
        StockLedgerService ledger = new StockLedgerService(stocks);
        BusinessOperationService operations = new BusinessOperationService(ledger, null, localDocuments);
        operations.confirmInbound(new ConfirmInboundCommand(
                "MD-ROUND-001", 1001L, 1001L, "AU9999", "2026-10-08", "顺丰", "SF-ROUND-001", "",
                "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                new BigDecimal("10"), "件", new BigDecimal("20"), "何琳", "", "原单",
                "内壁刻字", "2026-10-20", "恒信珠宝"
        ));
        localDocuments.saveMotherSplitState(new MotherSplitState(
                "MD-ROUND-001", MotherSplitState.KEEP, "VC-R", "收货员", "2026-10-08T00:00:00"
        ));
        InMemoryOrderRequirementRepository requirements = new InMemoryOrderRequirementRepository();
        MemoryBusinessWorkUnit work = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        OrderChangeService changes = new OrderChangeService(
                new InMemoryOrderChangeRepository(), localDocuments, work, null, null, null, requirements);
        ProcessStationService stations = new ProcessStationService(
                localDocuments, ledger, operations, new ScanCodeParser(), null, work, requirements);
        AuthUserAccount carver = new AuthUserAccount(
                "雕刻工", "hash", "雕刻工", RoleCode.OPERATOR, DataScope.ALL, true, true,
                Instant.now(), 0, null, List.of(), false, false, List.of("雕刻")
        );
        stations.report("MD-ROUND-001", "雕刻", carver, "round-origin-report");
        assertThat(localDocuments.listProcessStationHandoffs("MD-ROUND-001")).hasSize(1);

        var draft = changes.draft("MD-ROUND-001", "", "外圈重刻", "2026-12-01", "round-draft-0001", null, null, "雕刻", receiver);
        changes.submitFinance(draft.id(), receiver);
        approve(changes, draft.id());
        changes.apply(draft.id(), "round-apply-0001", receiver);
        String roundCode = requirements.listByMother("MD-ROUND-001").get(0).roundWorkOrderCode();

        var reported = stations.report(roundCode, "雕刻", carver, "round-rework-report");
        assertThat(reported.orderCode()).isEqualTo(roundCode);
        assertThat(localDocuments.listProcessStationHandoffs("MD-ROUND-001")).hasSize(1);
        assertThat(localDocuments.listProcessStationHandoffs(roundCode)).singleElement()
                .satisfies(handoff -> assertThat(handoff.processName()).isEqualTo("雕刻"));
        assertThat(ledger.ledgers()).anyMatch(entry -> roundCode.equals(entry.documentCode()));
        assertThatThrownBy(() -> stations.report("MD-ROUND-001", "雕刻", carver, "round-origin-again"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经交过");

        QualityRequirementService quality = new QualityRequirementService(
                localDocuments, ledger, operations, new ScanCodeParser(), null, work, requirements);
        AuthUserAccount inspector = account("质检员", RoleCode.QUALITY_INSPECTOR);
        var view = quality.lookup(roundCode, inspector);
        assertThat(view.orderCode()).isEqualTo(roundCode);
        assertThat(view.processingRequirement()).isEqualTo("外圈重刻");
        assertThat(view.motherOrderCode()).isEqualTo("MD-ROUND-001");
    }

    @Test
    void applyKeepsExistingHandoffAndRequiresFinanceConfirmation() {
        var draft = service.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-draft-001", receiver);
        assertThatThrownBy(() -> service.apply(draft.id(), "change-apply-001", receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务审核后才能生效");
        service.submitFinance(draft.id(), receiver);
        assertThat(service.present(draft.id(), receiver).status()).isEqualTo(OrderChangeStatus.PENDING_REVIEW);
        assertThatThrownBy(() -> service.apply(draft.id(), "change-apply-001", receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务审核后才能生效");
        assertThatThrownBy(() -> service.review(draft.id(), true, "自己通过", receiver))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权审核改单");
        assertThatThrownBy(() -> service.confirmFinance(draft.id(), true, null, null, "金额不变", receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不再确认单价");
        approve(service, draft.id());
        var applied = service.apply(draft.id(), "change-apply-001", receiver);
        assertThat(applied.status()).isEqualTo(OrderChangeStatus.APPLIED);
        assertThat(applied.amountUnchanged()).isFalse();
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
        approve(effect, draft.id());
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
        approve(effect, first.id());
        effect.apply(first.id(), "change-stale-apply-a", receiver);
        effect.submitFinance(second.id(), receiver);
        approve(effect, second.id());
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
        assertThatThrownBy(() -> scoped.confirmFinance(draft.id(), false, new BigDecimal("70"), new BigDecimal("8"),
                new BigDecimal("-30"), BigDecimal.ZERO, "FIN-X", "雕刻", "", "减额", finance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不再确认单价");
        assertThat(scoped.present(draft.id(), limited).customerUnitPrice()).isEqualByComparingTo("0");
        assertThat(scoped.present(draft.id(), limited).receivableCode()).isEmpty();
        assertThat(scoped.present(draft.id(), finance).customerUnitPrice()).isEqualByComparingTo("0");
        assertThat(scoped.timeline("MD-CHANGE-001", limited)).noneMatch(line -> line.contains("客户单价") || line.contains("FIN-X"));
        assertThat(scoped.timeline("MD-CHANGE-001", finance)).noneMatch(line -> line.contains("客户单价:70"));
        AuthUserAccount carver = new AuthUserAccount(
                "雕刻工", "hash", "雕刻工", RoleCode.OPERATOR, DataScope.ALL, true, true,
                Instant.now(), 0, null, List.of(), false, false, List.of("雕刻")
        );
        assertThatThrownBy(() -> scoped.draft("MD-CHANGE-001", "", "改抛光", "2026-11-02", "change-process", null, 0, "抛光", carver))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void applicantCannotReviewOwnChangeAndRejectionDoesNotApply() {
        var draft = service.draft("MD-CHANGE-001", "", "改为外圈刻字", "2026-11-01", "change-self-0001", receiver);
        service.submitFinance(draft.id(), receiver);
        AuthUserAccount leader = account("收货员", RoleCode.WORKSHOP_LEADER);
        assertThatThrownBy(() -> service.review(draft.id(), true, "同名通过", leader))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("申请人不能审核自己的改单");
        AuthUserAccount other = account("另一主管", RoleCode.WORKSHOP_LEADER);
        var rejected = service.review(draft.id(), false, "路线不对", other);
        assertThat(rejected.status()).isEqualTo(OrderChangeStatus.REJECTED);
        assertThatThrownBy(() -> service.apply(draft.id(), "change-self-apply", receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务审核后才能生效");
        assertThat(service.present(draft.id(), receiver).status()).isEqualTo(OrderChangeStatus.REJECTED);
    }

    private void approve(OrderChangeService target, String id) {
        target.review(id, true, "要求、路线和交期通过", account("车间主管", RoleCode.WORKSHOP_LEADER));
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
