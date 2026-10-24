package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.MasterDataService;
import com.zhubao.erp.business.app.MobileIntakeService;
import com.zhubao.erp.business.domain.ProcessingRequirementClauses;
import com.zhubao.erp.business.domain.RequirementTemplate;
import com.zhubao.erp.business.domain.RequirementText;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.business.infra.InMemoryMotherRequirementSourceRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDocumentKinds;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockDocumentRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.tuple;

class MaterialStockServiceTest {

    private InMemoryStockLedgerRepository ledgerRepository;
    private StockLedgerService ledger;
    private InMemoryBusinessDocumentRepository orders;
    private InMemoryStockDocumentRepository stockDocuments;
    private InMemoryMotherRequirementSourceRepository sources;
    private MaterialStockService service;
    private AuthUserAccount keeper;
    private MasterDataService catalog;

    @BeforeEach
    void setUp() {
        ledgerRepository = new InMemoryStockLedgerRepository();
        ledger = new StockLedgerService(ledgerRepository);
        orders = new InMemoryBusinessDocumentRepository();
        stockDocuments = new InMemoryStockDocumentRepository();
        sources = new InMemoryMotherRequirementSourceRepository();
        catalog = new MasterDataService(new InMemoryMasterDataRepository());
        service = service(new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        keeper = account("库管员", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL, List.of());
    }

    @Test
    void 寄存标签只读取真实批次并遵守客户范围() {
        service.receivePrep(prepReceipt(1001L, "甲", List.of(new StockDesk.LineInput("AU9999", "克", BigDecimal.TEN, BigDecimal.TEN, "")), true, "batch-label-001"), keeper);
        var batch = onlyBatch("prep");
        assertThat(service.batchLabelSource(batch.batchCode(), keeper).documentCode()).isEqualTo(batch.documentCode());
        assertThatThrownBy(() -> service.batchLabelSource("MD-FAKE", keeper)).hasMessageContaining("真实寄存批次");
        var outsider = account("其他客户库管", RoleCode.WAREHOUSE_KEEPER, DataScope.CUSTOMER_RANGE, List.of("1002"));
        assertThatThrownBy(() -> service.batchLabelSource(batch.batchCode(), outsider)).isInstanceOf(PermissionDeniedException.class);
        var labels = new com.zhubao.erp.label.app.LabelService(); labels.setStockDocuments(stockDocuments);
        assertThat(labels.generateStockBatchLabel(batch.batchCode()).primaryCode()).isEqualTo(batch.batchCode());
        assertThat(labels.generateStockBatchLabel(batch.batchCode()).qrContent()).isEqualTo("STOCK|batch|" + batch.batchCode());
        catalog.saveMaterial(new com.zhubao.erp.business.domain.MaterialProfile("AU9999", "足金", "", "", true, List.of("DARK"), ""));
        var editions = new com.zhubao.erp.label.infra.InMemoryLabelStorageEditionRepository(); labels.setStorageEditions(editions);
        var editionService = new com.zhubao.erp.label.app.LabelStorageEditionService(editions, orders, catalog, labels, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), stockDocuments);
        editionService.adopt(batch.batchCode(), "batch-adopt-001", 0, "核对保存要求", keeper.operator(), "");
        assertThat(labels.generateStockBatchLabel(batch.batchCode()).qrContent()).isEqualTo("STOCK|batch|" + batch.batchCode() + "|S1");
        assertThat(labels.generateStockBatchLabel(batch.batchCode()).storageSources().get(0).marks()).containsExactly("光");
    }

    @Test
    void companyIssueAndOriginalReturnKeepDecimalsAndTrace() {
        String receipt = service.receiveCompany(companyReceipt("10.500", true, "company-recv-1"), keeper);
        StockDesk.BatchCard batch = onlyBatch("company");
        String issue = service.issueCompany(companyIssue(batch, "4.250", "公司领用", "张三", "补货", "company-issue-1"), keeper);
        service.issueCompany(companyIssue(batch, "4.250", "公司领用", "张三", "补货", "company-issue-1"), keeper);
        service.returnCompany(companyReturn(batch, issue, "1.125", "company-back-1"), keeper);

        StockDesk.BatchCard after = onlyBatch("company");
        assertThat(after.availableQuantity()).isEqualTo("7.375");
        assertThat(after.issuedQuantity()).isEqualTo("3.125");
        assertThat(after.quantityUnit()).isEqualTo("克");
        assertThat(after.ownerLabel()).isEqualTo("公司自有");
        assertThat(service.movements(new StockDesk.Query("company", null, "", batch.batchCode(), "", "", "", "", "", ""), keeper))
                .extracting(StockDesk.MovementRow::kindLabel)
                .contains("公司料入库", "公司料领用", "公司料退回");
        assertThat(receipt).startsWith("GS-");
        assertThat(ledger.ledgers()).filteredOn(entry -> "company_issue".equals(entry.documentType())).hasSize(1);
        assertThat(ledger.ledgers()).noneMatch(entry -> "inbound_confirm".equals(entry.documentType()));
    }

    @Test
    void companyOverviewKeepsSeparateUnitsAndMaterialFilter() {
        service.receiveCompany(companyReceipt("10.500", true, "view-gold"), keeper);
        service.receiveCompany(new StockDesk.ReceiptInput(null, "", "WH", "采购到货", "2026-10-21", "仓管", "", true,
                List.of(new StockDesk.LineInput("STONE", "颗", new BigDecimal("20"), new BigDecimal("1.250"), "石")),
                "view-stone"), keeper);

        StockDesk.Overview overview = service.overview(query("company"), keeper);
        assertThat(overview.quantities()).extracting(StockDesk.UnitTotal::unit, StockDesk.UnitTotal::quantity)
                .containsExactlyInAnyOrder(tuple("克", "10.5"), tuple("颗", "20"));
        assertThat(overview.knownWeight()).isEqualTo("11.75");
        assertThat(service.cards(new StockDesk.Query("company", null, "AU9999", "", "", "", "", "", "", ""), keeper))
                .extracting(StockDesk.BatchCard::materialCode).containsExactly("AU9999");
        StockDesk.BatchCard gold = service.cards(new StockDesk.Query("company", null, "AU9999", "", "", "", "", "", "", ""), keeper).get(0);
        assertThat(service.movements(new StockDesk.Query("company", null, "", gold.batchCode(), "", "", "", "", "", ""), keeper))
                .extracting(StockDesk.MovementRow::kindLabel).contains("公司料入库");
        assertThat(service.document(gold.documentCode(), keeper).lines()).singleElement()
                .satisfies(line -> assertThat(line.quantityUnit()).isEqualTo("克"));
    }

    @Test
    void customerConsignmentKeepsSeparateUnitsAndDoesNotOpenProduction() {
        List<StockDesk.LineInput> lines = List.of(
                new StockDesk.LineInput("AU9999", "克", new BigDecimal("100"), new BigDecimal("100"), "金料"),
                new StockDesk.LineInput("STONE", "颗", new BigDecimal("20"), BigDecimal.ZERO, "裸石")
        );
        // 裸石重量为零表示尚未称量。当前规则不允许把零重量正式入账，也不能填一个假重量。
        assertThatThrownBy(() -> service.receivePrep(prepReceipt(1001L, "甲", lines, true, "prep-two-post"), keeper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待核");
        assertThat(orders.listMotherOrders()).isEmpty();
        assertThat(ledger.ledgers()).isEmpty();

        service.receivePrep(prepReceipt(1001L, "甲", lines, false, "prep-two-pending"), keeper);
        List<StockDesk.BatchCard> cards = service.cards(query("prep"), keeper);
        assertThat(cards).extracting(StockDesk.BatchCard::quantityUnit).containsExactlyInAnyOrder("克", "颗");
        assertThat(cards).extracting(StockDesk.BatchCard::bookQuantity).containsExactlyInAnyOrder("100", "20");
        assertThat(cards).allMatch(StockDesk.BatchCard::pending);
        assertThat(orders.listMotherOrders()).isEmpty();
        assertThat(ledger.ledgers()).isEmpty();
        StockDesk.Overview overview = service.overview(query("prep"), keeper);
        assertThat(overview.pendingCount()).isEqualTo(2);
        assertThat(overview.quantities()).isEmpty();
    }

    @Test
    void openingMotherFromPrepMovesStockOnceAndKeepsSavedRequirement() {
        var unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        var operations = new BusinessOperationService(ledger, null, orders, unit);
        var intakes = new MobileIntakeService(operations, orders, unit);
        service = new MaterialStockService(ledger, stockDocuments, orders, unit, operations, intakes, sources, new PermissionGuard(), null, null, null, catalog);
        catalog.saveMaterial(new com.zhubao.erp.business.domain.MaterialProfile("AU9999", "足金", "", "", true, List.of("COLD", "VACUUM"), "测试保存条件"));
        service.receivePrep(prepReceipt(1001L, "甲", List.of(
                new StockDesk.LineInput("AU9999", "克", new BigDecimal("100"), new BigDecimal("100"), "")
        ), true, "prep-open-recv"), keeper);
        StockDesk.BatchCard batch = onlyBatch("prep");
        RequirementTemplate basic = catalog.templates(false).stream()
                .filter(template -> "常规珠型加工".equals(template.name())).findFirst().orElseThrow();
        String requirement = RequirementText.compose(basic.steps(), "孔径 2 毫米");
        catalog.saveMaterial(new com.zhubao.erp.business.domain.MaterialProfile("AU9999", "足金", "", "", true, List.of("DARK"), "新说明"));
        String mother = service.openOrder(new StockDesk.OpenOrderInput(1001L, "甲", "WH", batch.batchCode(), "AU9999", "克",
                new BigDecimal("30"), new BigDecimal("30"), "珠子", requirement, "2026-11-01",
                basic.templateCode(), basic.version(), "prep-open-1"), keeper);

        assertThat(orders.listReceiptLines(mother).get(0).storageRequirements().conditions()).containsExactly("COLD", "VACUUM");
        assertThat(onlyBatch("prep").availableQuantity()).isEqualTo("70");
        assertThat(quantity(OwnerType.CUSTOMER, "", mother, StockStatus.RAW_WAITING)).isEqualByComparingTo("30");
        assertThat(quantity(OwnerType.CUSTOMER, batch.batchCode(), "", StockStatus.RAW_WAITING).add(new BigDecimal("30")))
                .isEqualByComparingTo("100");
        assertThat(ledger.ledgers()).noneMatch(entry -> "inbound_confirm".equals(entry.documentType()));
        assertThat(orders.findMotherOrderByCode(mother).orElseThrow().processingRequirement()).isEqualTo(requirement);
        assertThat(orders.listAllProcessStationHandoffs()).isEmpty();
        assertThat(sources.find(mother).orElseThrow().savedRequirement()).isEqualTo(requirement);

        catalog.saveTemplate(basic.templateCode(), "常规珠型加工", List.of("开粗", "抛光"), 1, basic.version(), "");
        assertThat(orders.findMotherOrderByCode(mother).orElseThrow().processingRequirement()).isEqualTo(requirement);
        assertThat(catalog.templates(true).stream().filter(template -> template.templateCode().equals(basic.templateCode()))
                .findFirst().orElseThrow().version()).isEqualTo(basic.version() + 1);

        service.returnToCustomer(new StockDesk.CustomerReturnInput(1001L, "WH", batch.batchCode(), "AU9999",
                new BigDecimal("10"), new BigDecimal("10"), "客户自取", "当面交还", "", "prep-out-1"), keeper);
        assertThat(onlyBatch("prep").availableQuantity()).isEqualTo("60");
        assertThat(ledger.balances()).noneMatch(balance -> balance.dimension().stockStatus() == StockStatus.SHIPPED);
        assertThat(stockDocuments.list()).anyMatch(document -> StockDocumentKinds.PREP_CUSTOMER_RETURN.equals(document.kind()));
    }

    @Test
    void sameRequestReturnsTheSameDocumentAndRejectsChangedContent() {
        service.receiveCompany(companyReceipt("3", true, "company-replay"), keeper);
        service.receiveCompany(companyReceipt("3", true, "company-replay"), keeper);
        assertThat(ledger.ledgers()).filteredOn(entry -> "company_receipt".equals(entry.documentType())).hasSize(1);
        AuthUserAccount other = account("另一人", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL, List.of());
        assertThatThrownBy(() -> service.receiveCompany(companyReceipt("3", true, "company-replay"), other))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("跨账号");
        assertThatThrownBy(() -> service.receiveCompany(companyReceipt("4", true, "company-replay"), keeper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
    }

    @Test
    void concurrentIssuesCannotOverdraw() throws Exception {
        service.receiveCompany(companyReceipt("10", true, "company-race-recv"), keeper);
        StockDesk.BatchCard batch = onlyBatch("company");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = pool.submit(() -> raceIssue(batch, "race-issue-a", start));
            Future<String> second = pool.submit(() -> raceIssue(batch, "race-issue-b", start));
            start.countDown();
            int ok = 0;
            int rejected = 0;
            for (Future<String> future : List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    ok++;
                } catch (Exception ex) {
                    assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
                    rejected++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            assertThat(quantity(OwnerType.COMPANY, batch.batchCode(), "", StockStatus.RAW_WAITING)).isEqualByComparingTo("4");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedMultiLineReceiptRollsBackEveryLine() {
        MaterialStockService failing = service(new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
            throw new BusinessException("中间一行失败");
        }));
        assertThatThrownBy(() -> failing.receivePrep(prepReceipt(1001L, "甲", List.of(
                new StockDesk.LineInput("AU9999", "克", new BigDecimal("1"), new BigDecimal("1"), ""),
                new StockDesk.LineInput("STONE", "颗", new BigDecimal("2"), BigDecimal.ZERO, "")
        ), true, "prep-fail-lines"), keeper)).isInstanceOf(BusinessException.class);
        assertThat(stockDocuments.list()).isEmpty();
        assertThat(ledger.balances()).isEmpty();
    }

    @Test
    void permissionsKeepGenericTransferAwayFromWarehouseAndHideOtherCustomers() {
        PermissionGuard guard = new PermissionGuard();
        assertThat(guard.allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER))
                .contains(PermissionCode.STOCK_COMPANY_RECEIVE, PermissionCode.STOCK_PREP_CUSTOMER_RETURN)
                .doesNotContain(PermissionCode.INVENTORY_LEDGER_TRANSFER, PermissionCode.STOCK_CORRECT);
        assertThat(guard.allowedPermissionsFor(RoleCode.OPERATOR)).doesNotContain(PermissionCode.STOCK_COMPANY_RECEIVE);
        assertThat(guard.allowedPermissionsFor(RoleCode.BUSINESS_MANAGER)).doesNotContain(PermissionCode.STOCK_PREP_RECEIVE);
        AuthUserAccount operator = account("操作工", RoleCode.OPERATOR, DataScope.ALL, List.of());
        assertThatThrownBy(() -> service.overview(query("company"), operator)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.receiveCompany(companyReceipt("1", true, "denied-company"), operator))
                .isInstanceOf(PermissionDeniedException.class);

        service.receivePrep(prepReceipt(1001L, "甲", List.of(
                new StockDesk.LineInput("AU9999", "克", BigDecimal.ONE, BigDecimal.ONE, "")
        ), true, "scope-customer-a"), keeper);
        service.receivePrep(prepReceipt(1002L, "乙", List.of(
                new StockDesk.LineInput("AU9999", "克", new BigDecimal("2"), new BigDecimal("2"), "")
        ), true, "scope-customer-b"), keeper);
        AuthUserAccount limited = account("甲的库管", RoleCode.WAREHOUSE_KEEPER, DataScope.CUSTOMER_RANGE, List.of("1001"));
        assertThat(service.cards(query("prep"), limited)).extracting(StockDesk.BatchCard::customerId).containsExactly(1001L);
        assertThatThrownBy(() -> service.cards(new StockDesk.Query("prep", 1002L, "", "", "", "", "", "", "", ""), limited))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void freezeCorrectionAndMissingSourceStayExplicit() {
        service.receiveCompany(companyReceipt("10", true, "hold-recv"), keeper);
        StockDesk.BatchCard batch = onlyBatch("company");
        service.freeze(new StockDesk.HoldInput("company", null, "WH", batch.batchCode(), "AU9999",
                new BigDecimal("2"), new BigDecimal("2"), "", "hold-freeze-1"), keeper);
        assertThatThrownBy(() -> service.issueCompany(companyIssue(batch, "9", "领用", "张三", "试", "hold-issue-big"), keeper))
                .isInstanceOf(BusinessException.class);
        service.issueCompany(companyIssue(batch, "8", "领用", "张三", "试", "hold-issue-ok"), keeper);
        assertThat(onlyBatch("company").availableQuantity()).isEqualTo("0");
        assertThat(onlyBatch("company").frozenQuantity()).isEqualTo("2");

        AuthUserAccount admin = account("管理员", RoleCode.ADMIN, DataScope.ALL, List.of());
        assertThatThrownBy(() -> service.correct(new StockDesk.CorrectionInput("company", null, "WH", "NO-SOURCE", "AU9999", "克",
                BigDecimal.ONE, BigDecimal.ONE, StockDocumentKinds.OUT, "历史缺口", "correct-missing"), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有收货来源");
        assertThatThrownBy(() -> service.correct(new StockDesk.CorrectionInput("company", null, "WH", batch.batchCode(), "AU9999", "克",
                BigDecimal.ONE, BigDecimal.ONE, StockDocumentKinds.OUT, "调错", "correct-keeper"), keeper))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void templateSentenceStaysOneClauseAndDisableDoesNotRewriteHistory() {
        String sentence = RequirementText.compose(List.of("开粗", "车形（车成珠型）", "抛光"), "孔径 2 毫米");
        assertThat(ProcessingRequirementClauses.split(sentence)).containsExactly("先开粗，再车形（车成珠型），最后抛光", "孔径 2 毫米");
        String replaced = RequirementText.compose(List.of("开粗", "车形（车成珠型）", "雕刻", "抛光"), "孔径 2 毫米");
        assertThat(replaced).contains("雕刻").doesNotContain("先开粗，再车形（车成珠型），最后抛光\n先");
        assertThat(ProcessingRequirementClauses.split(replaced).get(1)).isEqualTo("孔径 2 毫米");

        RequirementTemplate created = catalog.saveTemplate("", "试作模板", List.of("开粗", "抛光"), 3, 0, "tpl-create-1");
        assertThat(catalog.saveTemplate("", "试作模板", List.of("开粗", "抛光"), 3, 0, "tpl-create-1").templateCode())
                .isEqualTo(created.templateCode());
        assertThatThrownBy(() -> catalog.saveTemplate("", "另一种", List.of("抛光"), 4, 0, "tpl-create-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
        assertThatThrownBy(() -> catalog.saveTemplate(created.templateCode(), "试作模板", List.of("抛光"), 3, 0, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");
        RequirementTemplate disabled = catalog.disableTemplate(created.templateCode(), created.version());
        assertThat(disabled.enabled()).isFalse();
        assertThat(catalog.disableTemplate(created.templateCode(), created.version()).templateCode()).isEqualTo(created.templateCode());
        assertThat(catalog.templates(false)).extracting(RequirementTemplate::templateCode).doesNotContain(created.templateCode());
    }

    private String raceIssue(StockDesk.BatchCard batch, String requestId, CountDownLatch start) throws InterruptedException {
        start.await();
        return service.issueCompany(companyIssue(batch, "6", "并发", "张三", "试", requestId), keeper);
    }

    private MaterialStockService service(MemoryBusinessWorkUnit unit) {
        BusinessOperationService operations = new BusinessOperationService(ledger, null, orders);
        MobileIntakeService intakes = new MobileIntakeService(operations, orders, unit, null, ledger);
        return new MaterialStockService(ledger, stockDocuments, orders, unit, operations, intakes, sources, new PermissionGuard());
    }

    private StockDesk.BatchCard onlyBatch(String scope) {
        return service.cards(query(scope), keeper).stream().filter(card -> !card.pending()).findFirst().orElseThrow();
    }

    private StockDesk.Query query(String scope) {
        return new StockDesk.Query(scope, null, "", "", "", "", "", "", "", "");
    }

    private StockDesk.ReceiptInput companyReceipt(String amount, boolean postNow, String requestId) {
        BigDecimal value = new BigDecimal(amount);
        return new StockDesk.ReceiptInput(null, "", "WH", "采购到货", "2026-10-21", "仓管", "", postNow,
                List.of(new StockDesk.LineInput("AU9999", "克", value, value, "来料单")), requestId);
    }

    private StockDesk.IssueInput companyIssue(StockDesk.BatchCard batch, String amount, String recipient, String person, String purpose, String requestId) {
        BigDecimal value = new BigDecimal(amount);
        return new StockDesk.IssueInput(null, batch.warehouseCode(), batch.batchCode(), batch.materialCode(), batch.quantityUnit(),
                value, value, person, purpose, "", requestId);
    }

    private StockDesk.ReturnInput companyReturn(StockDesk.BatchCard batch, String source, String amount, String requestId) {
        BigDecimal value = new BigDecimal(amount);
        return new StockDesk.ReturnInput(null, batch.warehouseCode(), batch.batchCode(), batch.materialCode(), source, 1, value, value, requestId);
    }

    private StockDesk.ReceiptInput prepReceipt(Long customerId, String name, List<StockDesk.LineInput> lines, boolean postNow, String requestId) {
        return new StockDesk.ReceiptInput(customerId, name, "WH", "客户寄存", "2026-10-21", "仓管", "", postNow, lines, requestId);
    }

    private BigDecimal quantity(OwnerType owner, String batch, String mother, StockStatus status) {
        return ledger.balances().stream()
                .filter(balance -> balance.dimension().ownerType() == owner)
                .filter(balance -> batch.equals(balance.dimension().batchCode()))
                .filter(balance -> mother.equals(balance.dimension().motherOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == status)
                .map(balance -> balance.quantity())
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private AuthUserAccount account(String operator, RoleCode role, DataScope scope, List<String> customers) {
        return new AuthUserAccount("user", "hash", operator, role, scope, true, true,
                Instant.now(), 0, null, customers, false, false, List.of());
    }
}
