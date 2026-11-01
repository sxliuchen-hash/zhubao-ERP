package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.*;
import com.zhubao.erp.document.domain.*;
import com.zhubao.erp.document.infra.*;
import com.zhubao.erp.finance.app.*;
import com.zhubao.erp.finance.domain.*;
import com.zhubao.erp.finance.infra.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 使用真实收货、报工、损耗、复称、质检、成品和发货服务，不预先伪造可发成品。 */
class ProductionExecutionLifecycleTest {
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static OperatorContext worker(String name) { return new OperatorContext(name,RoleCode.ADMIN,DataScope.ALL,false,"",List.of(),List.of(),name,"",""); }
    @Test void 数量未损耗但实称重量变化时末袋转出后不留下零数量虚重() {
        var f=new Fixture();
        var opened=f.execution.start("weight-start",worker("a"),f.target,List.of("CUT")); var bag=opened.bags().get(0).bag();
        f.reports.report(new WholeBagReportCommand("weight-cut",bag.id(),bag.version(),"CUT",QuantityInput.manual(n("500")),"ATT",null),worker("a"));
        f.finishAndShip(bag.id(),"weight-changed","9.4");
        assertThat(f.stock.balances()).filteredOn(row -> row.dimension().stockStatus()==StockStatus.WIP).singleElement()
                .satisfies(row -> { assertThat(row.quantity()).isZero(); assertThat(row.weight()).isZero(); });
        assertThat(f.stock.ledgers()).filteredOn(row -> "stock_reweigh".equals(row.documentType())).singleElement()
                .satisfies(row -> assertThat(row.weight()).isEqualByComparingTo("0.6"));
    }
    @Test void 五百颗来料报工损耗后四百八十颗形成出库且客户按实发员工按各道实作() {
        var f=new Fixture();
        assertThat(f.execution.read(f.target,worker("a")).bags()).isEmpty();
        var opened=f.execution.start("execution-start",worker("a"),f.target,List.of("CUT","BLANK"));
        var bag=opened.bags().get(0).bag();
        var legacy=new BusinessOperationService(f.stock,null,f.documents,f.unit,null,f.bags);
        assertThatThrownBy(() -> f.unit.execute(new com.zhubao.erp.work.domain.CommandCall("legacy-freeze","qc","FREEZE","MOTHER_ORDER",f.target,"freeze"),session -> {
            legacy.freezeQualityStock(new FreezeQualityStockCommand("QF",f.target,null,7L,null,"牙骨","WH","WH",n("1"),n("1"),"切料","qc","复核","ATT",""),session); return "done";
        })).hasMessageContaining("本单已按袋加工");
        f.reports.report(new WholeBagReportCommand("execution-cut",bag.id(),bag.version(),"CUT",QuantityInput.manual(n("500")),"ATT",null),worker("a"));
        bag=f.bags.find(bag.id()).orElseThrow();
        var disposed=f.dispositions.dispose(new QuantityDispositionCommand("execution-blank",bag.id(),bag.version(),n("480"),n("20"),n("0"),n("0"),n("0"),"ATT",StockMeasurement.unknown(),null,n("500")),worker("b"));
        var result=f.bags.find(disposed.handoffBagId()).orElseThrow();
        assertThat(f.stock.balances().stream().filter(row -> row.dimension().equals(f.flow.dimension(f.target,StockStatus.WIP))).findFirst().orElseThrow().quantity()).isEqualByComparingTo("480");
        assertThatThrownBy(() -> f.execution.finish("execution-no-weight",worker("warehouse"),result.id(),result.version())).hasMessageContaining("实际重量");
        f.weighing.supplement("execution-weigh",result.id(),result.version(),n("9.4"),worker("warehouse"));
        var weighed=f.bags.find(result.id()).orElseThrow();
        f.inspection.record("execution-inspect",weighed.id(),weighed.version(),weighed.requirementVersion(),n("480"),n("0"),n("0"),n("0"),List.of(),worker("qc"));
        f.release.release("execution-release",weighed.id(),weighed.version(),n("480"),worker("qc"));
        f.execution.finish("execution-finish",worker("warehouse"),weighed.id(),weighed.version());
        f.execution.finish("execution-finish",worker("warehouse"),weighed.id(),weighed.version());
        assertThat(f.stock.balances()).filteredOn(row -> row.dimension().stockStatus()==StockStatus.FINISHED).singleElement().satisfies(row -> { assertThat(row.quantity()).isEqualByComparingTo("480"); assertThat(row.weight()).isEqualByComparingTo("9.4"); });
        var creation=new DeliveryDraftService(f.deliveries,f.bags,new DocumentProcessingTargetCustomer(f.documents),id -> true,f.unit,f.documents);
        var sheet=creation.open("execution-draft", "warehouse",7L,"顺丰","本次实发",List.of(new DeliverySourceSelection(weighed.id(),weighed.version(),f.target,"颗")));
        var confirmation=new DeliveryConfirmationService(f.deliveries,f.bags,new DocumentDeliveryStockPlaceLookup(f.documents),id -> true,f.stock,f.documents,f.unit,f.holds,f.routes,f.portions,f.dispositionRows,f.inspections);
        confirmation.confirm("execution-ship","warehouse",sheet.draft().deliveryId(),1);
        assertThat(f.documents.listOutboundOrders()).singleElement().satisfies(row -> assertThat(row.quantity()).isEqualByComparingTo("480"));
        var finance=new InMemoryFinanceRepository(); var bills=new InMemoryCustomerDeliveryBillRepository();
        var billing=new CustomerDeliveryBillingService(f.deliveries,new DocumentDeliveryStockPlaceLookup(f.documents),bills,finance,(actor,id) -> {},f.unit);
        var bill=billing.confirm(worker("finance"),"execution-bill",sheet.draft().deliveryId(),List.of(new CustomerChargeLine("加工费",f.target,CustomerChargeMethod.QUANTITY,"颗",n("480"),n("2"),null,null,null,null,"按实发")),null,null);
        assertThat(bill.totalAmount()).isEqualByComparingTo("960");
        var lookup=new ProductionEmployeeClosureLookup(f.bags,f.routes,f.portions,new InMemoryReworkTaskRepository(),f.dispositionRows,f.documents,f.reportRows,new InMemoryOutsourcingCompletionRepository());
        assertThat(lookup.find(f.target).orElseThrow().lossQuantity()).isEqualByComparingTo("20");
        var employee=new EmployeeTargetSettlementService(lookup,finance,actor -> {},f.unit);
        var wages=employee.settle(worker("finance"),"execution-wage",f.target,7L,List.of(new EmployeeWorkShare("a","CUT",n("500"),n("0.1"),false),new EmployeeWorkShare("b","BLANK",n("500"),n("0.2"),false)));
        assertThat(wages).hasSize(2);
        assertThat(wages.stream().map(PayrollLine::netAmount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("150");
        assertThat(f.documents.findMotherOrderByCode(f.target).orElseThrow().evidenceCode()).isEqualTo("原来料照片");
        assertThat(f.documents.findMotherOrderByCode(f.target).orElseThrow().quantity()).isEqualByComparingTo("500");
        assertThat(f.stock.ledgers()).filteredOn(row -> "process_loss".equals(row.documentType())).singleElement().satisfies(row -> assertThat(row.weight()).isNull());
    }
    @Test void 未交齐可先发部分并按次出账工费等到整单完成后按实际工序核对() {
        var f=new Fixture(); f.addPhoto("ATT-FIRST"); f.addPhoto("ATT-LAST"); var finance=new InMemoryFinanceRepository(); var bills=new InMemoryCustomerDeliveryBillRepository();
        var billing=new CustomerDeliveryBillingService(f.deliveries,new DocumentDeliveryStockPlaceLookup(f.documents),bills,finance,(actor,id) -> {},f.unit);
        var opened=f.execution.start("partial-start",worker("a"),f.target,List.of("CUT","BLANK")); var root=opened.bags().get(0).bag();
        f.reports.report(new WholeBagReportCommand("partial-cut",root.id(),1,"CUT",QuantityInput.manual(n("500")),"ATT",null),worker("a"));
        var split=new BagSplitService(f.bags,f.bagService,f.unit,f.portions,f.portions,f.inspections,new InMemoryReworkTaskRepository(),f.holds);
        String[] parts=split.split("partial-split","a",root.id(),List.of(n("300"),n("200"))).split(",");
        var first=f.bags.find(parts[0]).orElseThrow();
        f.reports.report(new WholeBagReportCommand("partial-blank-first",first.id(),1,"BLANK",QuantityInput.manual(n("300")),"ATT-FIRST",null),worker("b"));
        var firstDelivery=f.finishAndShip(first.id(),"first", "6");
        var firstBill=billing.confirm(worker("finance"),"partial-bill-first",firstDelivery,List.of(new CustomerChargeLine("加工费",f.target,CustomerChargeMethod.QUANTITY,"颗",n("300"),n("2"),null,null,null,null,"本次实发")),null,null);
        assertThat(firstBill.totalAmount()).isEqualByComparingTo("600");
        var lookup=new ProductionEmployeeClosureLookup(f.bags,f.routes,f.portions,new InMemoryReworkTaskRepository(),f.dispositionRows,f.documents,f.reportRows,new InMemoryOutsourcingCompletionRepository());
        assertThat(lookup.find(f.target).orElseThrow().closure()).isEqualTo(EmployeeClosureKind.NOT_CLOSED);
        var second=f.bags.find(parts[1]).orElseThrow();
        var disposed=f.dispositions.dispose(new QuantityDispositionCommand("partial-blank-last",second.id(),second.version(),n("190"),n("10"),n("0"),n("0"),n("0"),"ATT-LAST",StockMeasurement.unknown(),null,n("200")),worker("b"));
        String secondDelivery=f.finishAndShip(disposed.handoffBagId(),"second","3.5");
        assertThat(billing.confirm(worker("finance"),"partial-bill-second",secondDelivery,List.of(new CustomerChargeLine("加工费",f.target,CustomerChargeMethod.QUANTITY,"颗",n("190"),n("2"),null,null,null,null,"本次实发")),null,null).totalAmount()).isEqualByComparingTo("380");
        assertThat(finance.listReceivables()).hasSize(2);
        var employee=new EmployeeTargetSettlementService(lookup,finance,actor -> {},f.unit);
        assertThat(employee.settle(worker("finance"),"partial-settle",f.target,7L,List.of(new EmployeeWorkShare("a","CUT",n("500"),n("0.1"),false),new EmployeeWorkShare("b","BLANK",n("500"),n("0.2"),false)))).hasSize(2);
        assertThat(f.documents.listOutboundOrders()).hasSize(2);
    }

    static class Fixture {
        final String target="MD-20261102-9001";
        final InMemoryBusinessDocumentRepository documents=new InMemoryBusinessDocumentRepository();
        final InMemoryActivityBagRepository bags=new InMemoryActivityBagRepository(); final ActivityBagService bagService=new ActivityBagService(bags);
        final InMemoryRouteSnapshotRepository routes=new InMemoryRouteSnapshotRepository(); final InMemoryNodePortionReader portions=new InMemoryNodePortionReader();
        final InMemoryQuantityDispositionRepository dispositionRows=new InMemoryQuantityDispositionRepository(); final InMemoryWholeBagReportRepository reportRows=new InMemoryWholeBagReportRepository();
        final InMemoryPortionInspectionRepository inspections=new InMemoryPortionInspectionRepository(); final InMemoryPortionReleaseRepository holds=new InMemoryPortionReleaseRepository();
        final InMemoryDeliveryDraftRepository deliveries=new InMemoryDeliveryDraftRepository(); final MemoryBusinessWorkUnit unit=new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        final StockLedgerService stock=new StockLedgerService(new InMemoryStockLedgerRepository()); final PermissionGuard guard=new PermissionGuard();
        final WeighingService weighing=new WeighingService(bags,new InMemoryBagWeightRepository(),unit);
        final PortionInspectionService inspection=new PortionInspectionService(bags,inspections,guard,unit);
        final PortionReleaseService release=new PortionReleaseService(bags,inspections,holds,routes,portions,weighing,guard,unit);
        final ProcessAvailabilityQuery availability=new ProcessAvailabilityQuery(bags,routes,portions,guard,dispositionRows);
        final InMemoryDocumentManagementRepository photos=new InMemoryDocumentManagementRepository();
        final ProcessingStockFlow flow=new ProcessingStockFlow(new DocumentDeliveryStockPlaceLookup(documents),stock,documents);
        final WholeBagReportService reports=new WholeBagReportService(bags,availability,portions,portions,reportRows,photos,new InMemoryBusinessEvidenceLinkRepository(),unit);
        final QuantityDispositionService dispositions=new QuantityDispositionService(bags,bagService,routes,availability,portions,portions,dispositionRows,photos,stock,unit,flow);
        final ProductionExecutionService execution=new ProductionExecutionService(documents,bags,bagService,routes,availability,release,inspections,holds,weighing,portions,dispositionRows,deliveries,id -> true,flow,unit);
        String finishAndShip(String bagId,String request,String actualWeight) {
            var bag=bags.find(bagId).orElseThrow();
            weighing.supplement(request+"-weigh",bagId,bag.version(),n(actualWeight),worker("warehouse")); bag=bags.find(bagId).orElseThrow();
            inspection.record(request+"-inspect",bagId,bag.version(),bag.requirementVersion(),bag.quantity(),n("0"),n("0"),n("0"),List.of(),worker("qc"));
            release.release(request+"-release",bagId,bag.version(),bag.quantity(),worker("qc")); execution.finish(request+"-finished",worker("warehouse"),bagId,bag.version());
            var creation=new DeliveryDraftService(deliveries,bags,new DocumentProcessingTargetCustomer(documents),id -> true,unit,documents);
            var sheet=creation.open(request+"-delivery","warehouse",7L,"","",List.of(new DeliverySourceSelection(bagId,bag.version(),target,"颗")));
            new DeliveryConfirmationService(deliveries,bags,new DocumentDeliveryStockPlaceLookup(documents),id -> true,stock,documents,unit,holds,routes,portions,dispositionRows,inspections).confirm(request+"-shipment","warehouse",sheet.draft().deliveryId(),1);
            return sheet.draft().deliveryId();
        }
        void addPhoto(String code) {
            photos.saveAttachment(new EvidenceAttachment(code,"DOC","来料母单",target,"a.png","image/png","local://photo","hot","PWA","已上传","a","hash","2026-11-02","","green",EvidenceCodes.MOTHER_ORDER));
        }
        Fixture() {
            new BusinessOperationService(stock,null,documents,unit).confirmInbound(new ConfirmInboundCommand(target,7L,null,"牙骨","2026-11-02","顺丰","SF-EXECUTION","客户","库管","库管","8mm珠料","登记表",1,"1","WH",n("500"),"颗",n("10"),"库管","原来料照片","收货","切料后取胚","2026-11-30","客户甲","execution-inbound"));
            photos.saveAttachment(new EvidenceAttachment("ATT","DOC","来料母单",target,"a.png","image/png","local://photo","hot","PWA","已上传","a","hash","2026-11-02","","green",EvidenceCodes.MOTHER_ORDER));
        }
    }
}
