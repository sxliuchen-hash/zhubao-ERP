package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.infra.InMemoryBusinessEvidenceLinkRepository;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.finance.app.CustomerDeliveryBillingService;
import com.zhubao.erp.finance.app.EmployeeTargetSettlementService;
import com.zhubao.erp.finance.app.ProductionEmployeeClosureLookup;
import com.zhubao.erp.finance.domain.*;
import com.zhubao.erp.finance.infra.InMemoryCustomerDeliveryBillRepository;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryOutsourcingFeeBasisRepository;
import com.zhubao.erp.inventory.app.*;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 普通加工从原收货走到客户账单、员工工资和公司材料成本。
 * 工资按最终成品数量和定额工价核算，返工不改变工资。外协费用按任务选填，不生成应付。
 */
class ProcessingFullLifecycleTest {

    @Test
    void 五百颗经分袋拍照外协损耗部分质检后分批出账并分开核算材料与工资() {
        Chain chain = new Chain();
        assertThat(chain.mother().quantity()).isEqualByComparingTo("500");
        assertThat(chain.mother().evidenceCode()).isEqualTo("原来料照片");
        assertThat(chain.photos.findAttachmentByCode("P-MOTHER")).isPresent();

        var opened = chain.execution.start("z01-start-01", chain.person("开单"), chain.target, List.of("CUT", "POLISH_BEAD", "BLANK"));
        chain.execution.start("z01-start-01", chain.person("开单"), chain.target, List.of("CUT", "POLISH_BEAD", "BLANK"));
        assertThat(chain.bags.listByTarget(chain.target)).hasSize(1);
        assertThatThrownBy(() -> chain.execution.start("z01-start-02", chain.person("开单"), chain.target, List.of("CUT")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("本单已有执行袋，请使用现有袋办理");
        int ledgersAfterStart = chain.stock.ledgers().size();
        assertThatThrownBy(() -> chain.unit.execute(new CommandCall("z01-old-qc1", "qc", "FREEZE", "MOTHER_ORDER", chain.target, "freeze"), session -> {
            chain.legacy().freezeQualityStock(new FreezeQualityStockCommand(
                    "QF", chain.target, null, 7L, null, "牙骨", "WH", "WH", n("1"), n("1"), "切料", "qc", "复核", "P-MOTHER", ""), session);
            return "done";
        })).hasMessageContaining("本单已按袋加工");
        assertThat(chain.stock.ledgers()).hasSize(ledgersAfterStart);
        assertThat(chain.qty(StockStatus.WIP)).isEqualByComparingTo("500");

        ActivityBag root = opened.bags().get(0).bag();
        assertThatThrownBy(() -> chain.report("z01-cut-bad", root, "CUT", "500", "P-MISSING", chain.person("cutter")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有有效的现场照片");
        assertThat(chain.reportRows.listByTarget(chain.target)).isEmpty();
        assertThat(chain.bag(root.id()).version()).isEqualTo(root.version());

        ActivityBag cut = chain.report("z01-cut-500", root, "CUT", "500", "P-CUT", chain.person("cutter"));
        var cutReplay = chain.reports.report(new WholeBagReportCommand(
                "z01-cut-500", cut.id(), 1, "CUT", QuantityInput.manual(n("500")), "P-CUT", null), chain.person("cutter"));
        assertThat(cutReplay.quantity()).isEqualByComparingTo("500");
        assertThat(chain.reportRows.listByTarget(chain.target)).hasSize(1);

        String[] firstSplit = chain.splits.split("z01-split-a", "开单", cut.id(), List.of(n("300"), n("200"))).split(",");
        assertThat(chain.splits.split("z01-split-a", "开单", cut.id(), List.of(n("300"), n("200")))).isEqualTo(String.join(",", firstSplit));
        ActivityBag sent = chain.bag(firstSplit[0]);
        ActivityBag house = chain.bag(firstSplit[1]);
        assertThat(sent.quantity()).isEqualByComparingTo("300");
        assertThat(house.quantity()).isEqualByComparingTo("200");
        assertThat(chain.bag(cut.id()).closed()).isTrue();

        chain.sources.register("z01-party01", OutsourcingCounterpart.OUTSOURCING_PARTY, "WX-Z01-01", "甲抛光", "clerk-01");
        OutsourcingTask task = chain.sources.openTask(
                "z01-task-01", "WX-Z01-01", chain.target, "POLISH_BEAD", sent.id(), OwnerType.CUSTOMER, "7", "clerk-01");
        OutsourcingDispatch dispatch = chain.dispatches.dispatch("z01-dsp-300", "clerk-01", task.taskId(), sent.id(), sent.version(), sent.quantity());
        OutsourcingDispatch dispatchReplay = chain.dispatches.dispatch("z01-dsp-300", "clerk-01", task.taskId(), sent.id(), sent.version(), sent.quantity());
        assertThat(dispatchReplay.dispatchId()).isEqualTo(dispatch.dispatchId());
        assertThat(chain.dispatchRows.listByTask(task.taskId())).hasSize(1);
        assertThatThrownBy(() -> chain.report("z01-out-rpt", chain.bag(dispatch.sentBagId()), "POLISH_BEAD", "300", "P-BLOCKED", chain.person("polisher")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("本袋仍在外协处，收回前不能登记厂内报工");
        assertThat(chain.reportRows.listByTarget(chain.target)).hasSize(1);

        OutsourcingReturn returned = chain.returns.receive(
                "z01-ret-300", "clerk-01", dispatch.dispatchId(), dispatch.version(), n("280"), n("0"), n("20"), null);
        OutsourcingReturn returnReplay = chain.returns.receive(
                "z01-ret-300", "clerk-01", dispatch.dispatchId(), dispatch.version(), n("280"), n("0"), n("20"), null);
        assertThat(returnReplay.returnId()).isEqualTo(returned.returnId());
        var balance = chain.returns.balance(dispatch.dispatchId());
        assertThat(balance.received()).isEqualByComparingTo("280");
        assertThat(balance.confirmedLoss()).isEqualByComparingTo("20");
        assertThat(balance.stillOut()).isEqualByComparingTo("0");
        assertThat(balance.received().add(balance.confirmedLoss()).add(balance.stillOut())).isEqualByComparingTo("300");
        assertThat(chain.qty(StockStatus.WIP)).isEqualByComparingTo("480");
        assertThat(chain.stock.balances().stream().filter(row -> row.dimension().stockStatus() == StockStatus.WIP).findFirst().orElseThrow().weight())
                .isEqualByComparingTo("10");
        assertThat(chain.stock.balances().stream().filter(row -> row.dimension().stockStatus() == StockStatus.WIP).findFirst().orElseThrow().weightPending()).isTrue();
        assertThat(chain.stock.ledgers().stream().filter(row -> "outsourcing_loss".equals(row.documentType())).findFirst().orElseThrow().weight()).isNull();

        var completion = chain.completions.confirm("z01-cmp-300", "clerk-01", returned.returnId());
        var completionReplay = chain.completions.confirm("z01-cmp-300", "clerk-01", returned.returnId());
        assertThat(completionReplay.completionId()).isEqualTo(completion.completionId());
        assertThatThrownBy(() -> chain.completions.confirm("z01-cmp-301", "clerk-01", returned.returnId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("外协完成量已记录");
        assertThat(chain.fees.findByCompletion(completion.completionId()).orElseThrow().quantity()).isEqualByComparingTo("280");
        assertThat(chain.finance.listPayables()).isEmpty();
        assertThat(chain.finance.listReceivables()).isEmpty();

        ActivityBag received = chain.report("z01-blank-out", chain.bag(returned.receivedBagId()), "BLANK", "280", "P-BLANK-OUT", chain.person("blanker"));
        String[] secondSplit = chain.splits.split("z01-split-b", "开单", received.id(), List.of(n("180"), n("100"))).split(",");
        ActivityBag firstLeaf = chain.bag(secondSplit[0]);
        ActivityBag second = chain.bag(secondSplit[1]);
        chain.inspect("z01-qc-part", firstLeaf, "100", "0");
        assertThatThrownBy(() -> chain.releases.release("z01-rel-bad", firstLeaf.id(), firstLeaf.version(), firstLeaf.quantity(), chain.person("qc")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("放行数量不能超过已检合格数量");
        chain.inspect("z01-qc-rest", chain.bag(firstLeaf.id()), "80", "0");
        ActivityBag weighedFirst = chain.weigh("z01-wt-180", chain.bag(firstLeaf.id()), "4");
        assertThatThrownBy(() -> chain.execution.finish("z01-fin-bad", chain.person("warehouse"), weighedFirst.id(), weighedFirst.version()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("质量尚未放行");
        assertThat(chain.qty(StockStatus.FINISHED)).isEqualByComparingTo("0");
        ActivityBag releasedFirst = chain.release(weighedFirst, "z01-rel-180");
        chain.execution.finish("z01-fin-180", chain.person("warehouse"), releasedFirst.id(), releasedFirst.version());
        String firstDelivery = chain.ship(chain.bag(releasedFirst.id()), "z01-ship-180");
        var firstBill = chain.bill(firstDelivery, "z01-bill-180", "180");
        var firstBillReplay = chain.bill(firstDelivery, "z01-bill-180", "180");
        assertThat(firstBillReplay.billId()).isEqualTo(firstBill.billId());
        assertThat(firstBill.totalAmount()).isEqualByComparingTo("360");
        assertThatThrownBy(() -> chain.bill(firstDelivery, "z01-bill-181", "180"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一已确认发货单只能有一张初始客户账单");
        assertThat(chain.finance.listReceivables()).hasSize(1);
        assertThatThrownBy(() -> chain.settle("z01-wage-early"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("加工目标尚未闭合，不能核算工资");
        assertThat(chain.finance.listPayrollLines()).isEmpty();

        second = chain.weigh("z01-wt-100", second, "3");
        chain.inspect("z01-qc-100", second, "100", "0");
        second = chain.release(chain.bag(second.id()), "z01-rel-100");
        chain.execution.finish("z01-fin-100", chain.person("warehouse"), second.id(), second.version());
        String secondDelivery = chain.ship(chain.bag(second.id()), "z01-ship-100");
        assertThat(chain.bill(secondDelivery, "z01-bill-100", "100").totalAmount()).isEqualByComparingTo("200");
        assertThatThrownBy(() -> chain.settle("z01-wage-mid"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("加工目标尚未闭合，不能核算工资");

        house = chain.report("z01-polish-in", house, "POLISH_BEAD", "200", "P-POLISH", chain.person("polisher"));
        house = chain.report("z01-blank-in", house, "BLANK", "200", "P-BLANK-IN", chain.person("blanker"));
        chain.inspect("z01-qc-200", house, "200", "0");
        house = chain.weigh("z01-wt-200", chain.bag(house.id()), "3");
        house = chain.release(house, "z01-rel-200");
        chain.execution.finish("z01-fin-200", chain.person("warehouse"), house.id(), house.version());
        String thirdDelivery = chain.ship(chain.bag(house.id()), "z01-ship-200");
        assertThat(chain.bill(thirdDelivery, "z01-bill-200", "200").totalAmount()).isEqualByComparingTo("400");

        assertThat(chain.outboundQuantity()).isEqualByComparingTo("480");
        assertThat(chain.qty(StockStatus.SHIPPED)).isEqualByComparingTo("480");
        assertThat(chain.qty(StockStatus.WIP)).isEqualByComparingTo("0");
        assertThat(chain.qty(StockStatus.FINISHED)).isEqualByComparingTo("0");
        assertThat(chain.qty(StockStatus.RAW_WAITING)).isEqualByComparingTo("0");
        assertThat(chain.receivableTotal()).isEqualByComparingTo("960");
        int customerLedgers = chain.stock.ledgers().size();

        chain.consumeCompanyMaterial();
        assertThat(chain.receivableTotal()).isEqualByComparingTo("960");
        assertThat(chain.finance.listPayables()).isEmpty();
        assertThat(chain.stock.ledgers()).hasSize(customerLedgers);

        var closure = chain.lookup().find(chain.target).orElseThrow();
        assertThat(closure.closure()).isEqualTo(EmployeeClosureKind.CLOSED_NORMAL);
        assertThat(closure.lossQuantity()).isEqualByComparingTo("20");
        assertThat(closure.outsourcingQuantity()).isEqualByComparingTo("280");
        assertThat(closure.employeeQuantity()).isEqualByComparingTo("1180");
        assertThat(closure.workEvidenceComplete()).isTrue();
        var wages = chain.settle("z01-wage-ok");
        var wageReplay = chain.settle("z01-wage-ok");
        assertThat(wageReplay).extracting(PayrollLine::code).containsExactlyElementsOf(wages.stream().map(PayrollLine::code).toList());
        assertThat(wages).hasSize(3);
        assertThat(wages.stream().map(PayrollLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("160.00");
        assertThat(wages).allMatch(line -> line.lossDeduction().signum() == 0);
        assertThatThrownBy(() -> chain.settle("z01-wage-again"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该目标已经核算过工资");
        assertThat(chain.finance.listPayrollLines()).hasSize(3);
        assertThat(chain.finance.listPayables()).allMatch(row -> "工资".equals(row.kind()));
        assertThat(chain.finance.listPayables().stream().map(Payable::amount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("160.00");
        assertThat(chain.receivableTotal()).isEqualByComparingTo("960");
        assertThat(chain.mother().quantity()).isEqualByComparingTo("500");
        assertThat(chain.mother().evidenceCode()).isEqualTo("原来料照片");
        assertThat(chain.photos.findAttachmentByCode("P-MOTHER").orElseThrow().status()).isEqualTo("已上传");
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private static final class Chain {
        final String target = "MD-20261102-9101";
        final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        final InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        final ActivityBagService bagService = new ActivityBagService(bags);
        final InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        final InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        final InMemoryQuantityDispositionRepository dispositionRows = new InMemoryQuantityDispositionRepository();
        final InMemoryWholeBagReportRepository reportRows = new InMemoryWholeBagReportRepository();
        final InMemoryPortionInspectionRepository inspections = new InMemoryPortionInspectionRepository();
        final InMemoryPortionReleaseRepository holds = new InMemoryPortionReleaseRepository();
        final InMemoryReworkTaskRepository rework = new InMemoryReworkTaskRepository();
        final InMemoryDeliveryDraftRepository deliveries = new InMemoryDeliveryDraftRepository();
        final InMemoryOutsourcingSourceRepository sourceRows = new InMemoryOutsourcingSourceRepository();
        final InMemoryOutsourcingDispatchRepository dispatchRows = new InMemoryOutsourcingDispatchRepository();
        final InMemoryOutsourcingReturnRepository returnRows = new InMemoryOutsourcingReturnRepository();
        final InMemoryOutsourcingCompletionRepository completionRows = new InMemoryOutsourcingCompletionRepository();
        final InMemoryOutsourcingFeeBasisRepository fees = new InMemoryOutsourcingFeeBasisRepository();
        final InMemoryDocumentManagementRepository photos = new InMemoryDocumentManagementRepository();
        final InMemoryBusinessEvidenceLinkRepository links = new InMemoryBusinessEvidenceLinkRepository();
        final MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        final StockLedgerService stock = new StockLedgerService(new InMemoryStockLedgerRepository());
        final PermissionGuard guard = new PermissionGuard();
        final InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        final InMemoryCustomerDeliveryBillRepository bills = new InMemoryCustomerDeliveryBillRepository();
        final BagSplitService splits = new BagSplitService(bags, bagService, unit, portions, portions, inspections, rework, holds);
        final OutsourcingDispatchService dispatches = new OutsourcingDispatchService(sourceRows, dispatchRows, returnRows, bags, splits, unit);
        final OutsourcingDeliveryInHouseCheck inHouse = new OutsourcingDeliveryInHouseCheck(dispatches);
        final OutsourcingSourceService sources = new OutsourcingSourceService(sourceRows, bags, routes, unit);
        final OutsourcingReturnService returns = new OutsourcingReturnService(dispatchRows, returnRows, sourceRows, bags, splits, unit, stock);
        final OutsourcingCompletionService completions = new OutsourcingCompletionService(
                returnRows, dispatchRows, sourceRows, completionRows, fees, bags, routes, portions, portions, holds, unit);
        final ProcessAvailabilityQuery availability = new ProcessAvailabilityQuery(bags, routes, portions, guard, dispositionRows, inHouse);
        final WholeBagReportService reports = new WholeBagReportService(bags, availability, portions, portions, reportRows, photos, links, unit);
        final WeighingService weighing = new WeighingService(bags, new InMemoryBagWeightRepository(), unit);
        final PortionInspectionService inspection = new PortionInspectionService(bags, inspections, guard, unit);
        final PortionReleaseService releases = new PortionReleaseService(bags, inspections, holds, routes, portions, weighing, guard, unit);
        final ProcessingStockFlow flow = new ProcessingStockFlow(new DocumentDeliveryStockPlaceLookup(documents), stock, documents);
        final ProductionExecutionService execution = new ProductionExecutionService(
                documents, bags, bagService, routes, availability, releases, inspections, holds, weighing, portions,
                dispositionRows, deliveries, inHouse, flow, unit);
        final DeliveryDraftService drafts = new DeliveryDraftService(
                deliveries, bags, new DocumentProcessingTargetCustomer(documents), inHouse, unit, documents);
        final DeliveryConfirmationService confirmation = new DeliveryConfirmationService(
                deliveries, bags, new DocumentDeliveryStockPlaceLookup(documents), inHouse, stock, documents, unit,
                holds, routes, portions, dispositionRows, inspections);
        final CustomerDeliveryBillingService billing = new CustomerDeliveryBillingService(
                deliveries, new DocumentDeliveryStockPlaceLookup(documents), bills, finance, (actor, id) -> { }, unit);

        Chain() {
            new BusinessOperationService(stock, null, documents, unit).confirmInbound(new ConfirmInboundCommand(
                    target, 7L, null, "牙骨", "2026-11-02", "顺丰", "SF-Z01", "客户", "库管", "库管",
                    "8mm珠料", "登记表", 1, "1", "WH", n("500"), "颗", n("10"), "库管", "原来料照片",
                    "收货", "切料后取胚", "2026-11-30", "客户甲", "z01-inbound"));
            photo("P-MOTHER");
            photo("P-CUT");
            photo("P-BLANK-OUT");
            photo("P-POLISH");
            photo("P-BLANK-IN");
        }

        OperatorContext person(String account) {
            return new OperatorContext(account, RoleCode.ADMIN, DataScope.ALL, false, "", List.of(), List.of(), account, "", "");
        }

        MotherOrderRecord mother() {
            return documents.findMotherOrderByCode(target).orElseThrow();
        }

        ActivityBag bag(String id) {
            return bags.find(id).orElseThrow();
        }

        ActivityBag report(String request, ActivityBag bag, String step, String quantity, String photo, OperatorContext actor) {
            reports.report(new WholeBagReportCommand(
                    request, bag.id(), bag.version(), step, QuantityInput.manual(n(quantity)), photo, null), actor);
            return bag(bag.id());
        }

        void inspect(String request, ActivityBag bag, String pass, String reworkQty) {
            inspection.record(request, bag.id(), bag.version(), bag.requirementVersion(),
                    n(pass), n(reworkQty), n("0"), n("0"), List.of(), person("qc"));
        }

        ActivityBag weigh(String request, ActivityBag bag, String weight) {
            weighing.supplement(request, bag.id(), bag.version(), n(weight), person("warehouse"));
            return bag(bag.id());
        }

        ActivityBag release(ActivityBag bag, String request) {
            releases.release(request, bag.id(), bag.version(), bag.quantity(), person("qc"));
            return bag(bag.id());
        }

        String ship(ActivityBag bag, String request) {
            var sheet = drafts.open(request + "-draft", "warehouse", 7L, "", "分批实发",
                    List.of(new DeliverySourceSelection(bag.id(), bag.version(), target, "颗")));
            confirmation.confirm(request + "-ship", "warehouse", sheet.draft().deliveryId(), 1);
            confirmation.confirm(request + "-ship", "warehouse", sheet.draft().deliveryId(), 1);
            assertThat(documents.listOutboundOrders().stream().filter(row -> row.code().startsWith(sheet.draft().deliveryId())).count()).isEqualTo(1);
            return sheet.draft().deliveryId();
        }

        CustomerDeliveryBill bill(String deliveryId, String request, String quantity) {
            return billing.confirm(person("finance"), request, deliveryId, List.of(new CustomerChargeLine(
                    "加工费", target, CustomerChargeMethod.QUANTITY, "颗", n(quantity), n("2"),
                    null, null, null, null, "本次实发")), null, null);
        }

        List<PayrollLine> settle(String request) {
            return new EmployeeTargetSettlementService(lookup(), finance, actor -> { }, unit).settle(person("finance"), request, target, 7L, List.of(
                    new EmployeeWorkShare("cutter", "CUT", n("480"), n("0.10"), false),
                    new EmployeeWorkShare("polisher", "POLISH_BEAD", n("200"), n("0.20"), false),
                    new EmployeeWorkShare("blanker", "BLANK", n("480"), n("0.15"), false)));
        }

        ProductionEmployeeClosureLookup lookup() {
            return new ProductionEmployeeClosureLookup(
                    bags, routes, portions, rework, sourceRows, dispatchRows, returnRows, dispositionRows, documents, reportRows, completionRows);
        }

        BusinessOperationService legacy() {
            return new BusinessOperationService(stock, null, documents, unit, null, bags);
        }

        BigDecimal qty(StockStatus status) {
            return stock.balances().stream()
                    .filter(row -> row.dimension().stockStatus() == status)
                    .filter(row -> target.equals(row.dimension().motherOrderCode()))
                    .map(StockBalance::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal outboundQuantity() {
            return documents.listOutboundOrders().stream()
                    .filter(row -> target.equals(row.motherOrderCode()))
                    .map(OutboundOrderRecord::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal receivableTotal() {
            return finance.listReceivables().stream().map(ReceivableLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        void consumeCompanyMaterial() {
            InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
            InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
            InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
            OperatorContext keeper = person("库管");
            FactoryItem pin = new FactoryItemService(items, unit, guard).register(
                    "z01-item-01", keeper, FactoryItemCategory.ACCESSORY, "扣针", "小号", "颗", null, null);
            var received = new CompanyMaterialReceiptService(items, stocks, finance, unit, guard).receive(
                    "z01-mat-recv", keeper, pin.itemId(), n("100"), null, "颗", "WH-01", "2026-11-02",
                    CompanyMaterialSourceKind.OPENING, "", null, null);
            CompanyMaterialIssue issued = new CompanyMaterialIssueService(items, stocks, issues, unit, guard).issue(
                    "z01-mat-issue", keeper, pin.itemId(), received.batchCode(), "WH-01", "WS-01",
                    n("100"), null, "颗", target);
            CompanyMaterialConsumptionService service = new CompanyMaterialConsumptionService(stocks, issues, unit, guard);
            assertThatThrownBy(() -> service.confirm(
                    "z01-mat-bad1", keeper, issued.issueId(), 1, n("10"), n("10"), n("10"), n("0"), null, target))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("耗用、退回和留下必须等于尚未结清的领用");
            assertThat(issues.find(issued.issueId()).orElseThrow().consumedQuantity()).isEqualByComparingTo("0");
            assertThat(stocks.balance(pin.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("100");
            CompanyMaterialConsumption closed = service.confirm(
                    "z01-mat-use1", keeper, issued.issueId(), 1, n("70"), n("20"), n("10"), n("0"), n("35.00"), target);
            CompanyMaterialConsumption replay = service.confirm(
                    "z01-mat-use1", keeper, issued.issueId(), 1, n("70"), n("20"), n("10"), n("0"), n("35.00"), target);
            assertThat(replay.consumptionId()).isEqualTo(closed.consumptionId());
            assertThat(closed.amountState()).isEqualTo(CompanyMaterialAmountState.KNOWN);
            assertThat(closed.amount()).isEqualByComparingTo("35.00");
            assertThat(closed.consumedQuantity().add(closed.returnedQuantity()).add(closed.keptQuantity())).isEqualByComparingTo("100");
            assertThat(stocks.balance(pin.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("10");
            assertThat(stocks.balance(pin.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("20");
            assertThat(stocks.balance(pin.itemId(), received.batchCode(), "WS-01").orElseThrow().weight().state())
                    .isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        }

        void photo(String code) {
            photos.saveAttachment(new EvidenceAttachment(
                    code, "DOC", "来料母单", target, code + ".png", "image/png", "local://" + code,
                    "hot", "PWA", "已上传", "a", "hash-" + code, "2026-11-02", "", "green", EvidenceCodes.MOTHER_ORDER));
        }
    }
}
