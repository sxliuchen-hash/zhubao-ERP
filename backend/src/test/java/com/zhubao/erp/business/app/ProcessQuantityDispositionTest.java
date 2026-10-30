package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.ProcessAvailableQuantity;
import com.zhubao.erp.business.domain.QuantityDispositionCommand;
import com.zhubao.erp.business.domain.QuantityDispositionResult;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryQuantityDispositionRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
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

class ProcessQuantityDispositionTest {

    @Test
    void 五百颗按损耗依次交到四百六十五且原袋数量不改() {
        Fixture fixture = fixture(new BigDecimal("500"), StockMeasurement.known(new BigDecimal("7.260")));
        QuantityDispositionResult cut = fixture.dispose("disp-c03-01", fixture.bag(), "480", "20", "0", "0", "0");
        assertThat(cut.stepClosed()).isTrue();
        assertThat(cut.batchHanded()).isTrue();
        assertThat(cut.loss()).isEqualByComparingTo("20");
        assertThat(fixture.bags().find(fixture.bag().id()).orElseThrow().quantity()).isEqualByComparingTo("500");
        assertThat(fixture.bags().find(fixture.bag().id()).orElseThrow().weight().value()).isEqualByComparingTo("7.260");
        ActivityBag blankBag = fixture.bags().find(cut.handoffBagId()).orElseThrow();
        assertThat(blankBag.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(fixture.available(blankBag).availableQuantity()).isEqualByComparingTo("480");
        assertThat(fixture.available(blankBag).stepId()).isEqualTo("BLANK");

        QuantityDispositionResult blank = fixture.dispose("disp-c03-02", blankBag, "470", "10", "0", "0", "0");
        ActivityBag drillBag = fixture.bags().find(blank.handoffBagId()).orElseThrow();
        assertThat(fixture.available(drillBag).availableQuantity()).isEqualByComparingTo("470");
        assertThat(fixture.available(drillBag).stepId()).isEqualTo("DRILL");

        QuantityDispositionResult drill = fixture.dispose("disp-c03-03", drillBag, "465", "5", "0", "0", "0");
        ActivityBag output = fixture.bags().find(drill.handoffBagId()).orElseThrow();
        assertThat(fixture.available(output).availableQuantity()).isEqualByComparingTo("465");
        assertThat(fixture.available(output).stepId()).isEqualTo("STRING");
        assertThat(fixture.service().dispose(command("disp-c03-03", drillBag, "465", "5", "0", "0", "0"), operator()).handoffBagId())
                .isEqualTo(drill.handoffBagId());
        assertThat(fixture.stocks().ledgers()).isEmpty();
    }

    @Test
    void 上道还有余量时下道处理完已到数量也不关闭整道() {
        Fixture fixture = fixture(new BigDecimal("500"), StockMeasurement.unknown());
        QuantityDispositionResult cut = fixture.dispose("disp-c03-11", fixture.bag(), "100", "0", "0", "0", "0");
        assertThat(cut.stepClosed()).isFalse();
        assertThat(cut.unprocessed()).isEqualByComparingTo("400");
        ActivityBag next = fixture.bags().find(cut.handoffBagId()).orElseThrow();
        QuantityDispositionResult blank = fixture.dispose("disp-c03-12", next, "100", "0", "0", "0", "0");
        assertThat(blank.arrivedPortionFinished()).isTrue();
        assertThat(blank.stepClosed()).isFalse();
        assertThat(fixture.available(fixture.bags().find(cut.remainderBagId()).orElseThrow()).availableQuantity())
                .isEqualByComparingTo("400");
    }

    @Test
    void 待核不是损耗且全损不产生正数交出() {
        Fixture pending = fixture(new BigDecimal("500"), StockMeasurement.unknown());
        QuantityDispositionResult held = pending.dispose("disp-c03-21", pending.bag(), "480", "0", "0", "0", "20");
        assertThat(held.loss()).isEqualByComparingTo("0");
        assertThat(held.pending()).isEqualByComparingTo("20");
        assertThat(held.stepClosed()).isFalse();
        assertThat(pending.available(pending.bags().find(held.pendingBagId()).orElseThrow()).availableQuantity())
                .isEqualByComparingTo("0");
        assertThat(pending.stocks().ledgers()).isEmpty();

        Fixture lost = fixture(new BigDecimal("500"), StockMeasurement.unknown());
        QuantityDispositionResult all = lost.dispose("disp-c03-22", lost.bag(), "0", "500", "0", "0", "0");
        assertThat(all.handoff()).isEqualByComparingTo("0");
        assertThat(all.handoffBagId()).isEmpty();
        assertThat(all.stepClosed()).isTrue();
        assertThat(all.weightPending()).isTrue();
        assertThat(lost.bagService().activeLeaves(lost.bag().targetCode())).isEmpty();
        assertThat(lost.bags().find(lost.bag().id()).orElseThrow().quantity()).isEqualByComparingTo("500");
        assertThat(lost.stocks().ledgers()).isEmpty();
    }

    @Test
    void 已称损耗扣在制且退回和报废不写成收货冲正() {
        Fixture fixture = fixture(new BigDecimal("500"), StockMeasurement.unknown());
        StockDimension wip = new StockDimension(
                OwnerType.CUSTOMER, 1001L, 1001L, fixture.bag().targetCode(), fixture.bag().targetCode(),
                "牙骨", "WIP-1", StockStatus.WIP);
        fixture.unit().execute(new CommandCall("disp-seed1", "操作工", "SEED", "MOTHER_ORDER", fixture.bag().targetCode(), "seed"),
                session -> {
                    fixture.stocks().transfer(new StockTransferCommand(
                            "seed", fixture.bag().id(), null, wip, new BigDecimal("500"), new BigDecimal("1.000"), "期初"), session);
                    return "SEEDED";
                });
        QuantityDispositionResult loss = fixture.service().dispose(new QuantityDispositionCommand(
                "disp-c03-31", fixture.bag().id(), fixture.bag().version(), new BigDecimal("480"), new BigDecimal("20"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "ATT-1",
                StockMeasurement.known(new BigDecimal("0.200")), wip), operator());
        assertThat(loss.weightPending()).isFalse();
        assertThat(loss.stockDocumentType()).isEqualTo("process_loss");
        assertThat(fixture.ledger().findBalance(wip).quantity()).isEqualByComparingTo("480");
        assertThat(fixture.ledger().findBalance(wip).weight()).isEqualByComparingTo("0.800");
        assertThat(fixture.stocks().ledgers()).filteredOn(entry -> "process_loss".equals(entry.documentType()))
                .allSatisfy(entry -> assertThat(entry.sourceLedgerCode()).isNull());

        Fixture scrapFixture = fixture(new BigDecimal("10"), StockMeasurement.unknown());
        StockDimension scrapWip = new StockDimension(
                OwnerType.CUSTOMER, 1001L, 1001L, scrapFixture.bag().targetCode(), scrapFixture.bag().targetCode(),
                "牙骨", "WIP-1", StockStatus.WIP);
        scrapFixture.unit().execute(new CommandCall("disp-seed2", "操作工", "SEED", "MOTHER_ORDER", scrapFixture.bag().targetCode(), "seed"),
                session -> {
                    scrapFixture.stocks().transfer(new StockTransferCommand(
                            "seed", scrapFixture.bag().id(), null, scrapWip, new BigDecimal("10"), new BigDecimal("1.000"), "期初"), session);
                    return "SEEDED";
                });
        QuantityDispositionResult scrap = scrapFixture.service().dispose(new QuantityDispositionCommand(
                "disp-c03-32", scrapFixture.bag().id(), 1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4"),
                BigDecimal.ZERO, BigDecimal.ZERO, "ATT-1", StockMeasurement.known(new BigDecimal("0.400")), scrapWip), operator());
        assertThat(scrap.stockDocumentType()).isEqualTo("process_scrap");
        StockDimension scrapStock = new StockDimension(
                scrapWip.ownerType(), scrapWip.ownerCustomerId(), scrapWip.customerId(), scrapWip.motherOrderCode(),
                scrapWip.workOrderCode(), scrapWip.materialCode(), scrapWip.warehouseCode(), StockStatus.SCRAP);
        assertThat(scrapFixture.ledger().findBalance(scrapStock).quantity()).isEqualByComparingTo("4");
        assertThat(scrapFixture.stocks().ledgers()).extracting(StockLedgerEntry::documentType).contains("process_scrap");

        Fixture returnFixture = fixture(new BigDecimal("6"), StockMeasurement.unknown());
        StockDimension returnWip = new StockDimension(
                OwnerType.CUSTOMER, 1001L, 1001L, returnFixture.bag().targetCode(), returnFixture.bag().targetCode(),
                "牙骨", "WIP-1", StockStatus.WIP);
        returnFixture.unit().execute(new CommandCall("disp-seed3", "操作工", "SEED", "MOTHER_ORDER", returnFixture.bag().targetCode(), "seed"),
                session -> {
                    returnFixture.stocks().transfer(new StockTransferCommand(
                            "seed", returnFixture.bag().id(), null, returnWip, new BigDecimal("6"), new BigDecimal("1.000"), "期初"), session);
                    return "SEEDED";
                });
        QuantityDispositionResult returned = returnFixture.service().dispose(new QuantityDispositionCommand(
                "disp-c03-33", returnFixture.bag().id(), 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("6"), BigDecimal.ZERO, "ATT-1", StockMeasurement.known(new BigDecimal("1.000")), returnWip), operator());
        assertThat(returned.stockDocumentType()).isEqualTo("process_return");
        assertThat(returnFixture.ledger().findBalance(returnWip).quantity()).isEqualByComparingTo("0");
        assertThat(returnFixture.stocks().ledgers()).extracting(StockLedgerEntry::documentType)
                .doesNotContain("prep_customer_return", "inbound_reversal", "stock_correction");
    }

    @Test
    void 超过可处理量或没有照片时不关闭原袋() {
        Fixture fixture = fixture(new BigDecimal("500"), StockMeasurement.unknown());
        assertThatThrownBy(() -> fixture.dispose("disp-c03-41", fixture.bag(), "501", "0", "0", "0", "0"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("处置数量超过当前可处理量");
        assertThatThrownBy(() -> fixture.service().dispose(new QuantityDispositionCommand(
                "disp-c03-42", fixture.bag().id(), 1, new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "", StockMeasurement.unknown(), null), operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有有效的现场照片");
        assertThat(fixture.bags().find(fixture.bag().id()).orElseThrow().closed()).isFalse();
        assertThat(fixture.bags().find(fixture.bag().id()).orElseThrow().quantity()).isEqualByComparingTo("500");
    }

    private static OperatorContext operator() {
        return new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL);
    }

    private static QuantityDispositionCommand command(String requestId, ActivityBag bag, String handoff, String loss,
                                                      String scrap, String returned, String pending) {
        return new QuantityDispositionCommand(
                requestId, bag.id(), bag.version(), new BigDecimal(handoff), new BigDecimal(loss),
                new BigDecimal(scrap), new BigDecimal(returned), new BigDecimal(pending),
                "ATT-1", StockMeasurement.unknown(), null);
    }

    private static Fixture fixture(BigDecimal quantity, StockMeasurement.Weight weight) {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        InMemoryQuantityDispositionRepository dispositions = new InMemoryQuantityDispositionRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        InMemoryStockLedgerRepository ledger = new InMemoryStockLedgerRepository();
        StockLedgerService stocks = new StockLedgerService(ledger);
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        ActivityBagService bagService = new ActivityBagService(bags);
        ActivityBag bag = bagService.open("MD-20261030-0301", "牙骨", "REQ-1", "LINE-1", quantity, weight, null);
        routes.insert(new RouteSnapshot("RS-1", BusinessKind.PROCESSING, bag.targetCode(), 1,
                List.of("CUT", "BLANK", "DRILL", "STRING"), true), null);
        documents.saveAttachment(new EvidenceAttachment(
                "ATT-1", "DOC-ATT-1", "来料母单", bag.targetCode(), "a.jpg", "image/jpeg",
                "local://a", "hot", "PWA 拍照上传", "已上传", "操作工", "sha256:a",
                "2026-10-30 10:00:00", "无", "green", EvidenceCodes.MOTHER_ORDER));
        ProcessAvailabilityQuery query = new ProcessAvailabilityQuery(bags, routes, portions, new PermissionGuard(), dispositions);
        QuantityDispositionService service = new QuantityDispositionService(
                bags, bagService, routes, query, portions, portions, dispositions, documents, stocks, unit);
        return new Fixture(bags, bagService, bag, portions, stocks, ledger, unit, query, service);
    }

    private record Fixture(
            InMemoryActivityBagRepository bags,
            ActivityBagService bagService,
            ActivityBag bag,
            InMemoryNodePortionReader portions,
            StockLedgerService stocks,
            InMemoryStockLedgerRepository ledger,
            MemoryBusinessWorkUnit unit,
            ProcessAvailabilityQuery query,
            QuantityDispositionService service
    ) {
        private QuantityDispositionResult dispose(String requestId, ActivityBag target, String handoff, String loss,
                                                  String scrap, String returned, String pending) {
            return service.dispose(command(requestId, target, handoff, loss, scrap, returned, pending), operator());
        }

        private ProcessAvailableQuantity available(ActivityBag target) {
            return query.lookup(target.id(), operator());
        }
    }
}
