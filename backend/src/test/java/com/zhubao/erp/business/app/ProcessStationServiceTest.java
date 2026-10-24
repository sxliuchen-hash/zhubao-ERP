package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ProcessStationCard;
import com.zhubao.erp.business.domain.ProcessStationReportResult;
import com.zhubao.erp.business.domain.ProcessStationView;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 三个工序岗位扫码：只看绑定工序，按顺序交付，最后一道才转入半成品。
 */
class ProcessStationServiceTest {

    private InMemoryStockLedgerRepository stockRepository;
    private StockLedgerService stockLedgerService;
    private InMemoryBusinessDocumentRepository documents;
    private BusinessOperationService operations;
    private MotherOrderSplitService splitService;
    private ProcessStationService stationService;

    @BeforeEach
    void setUp() {
        stockRepository = new InMemoryStockLedgerRepository();
        stockLedgerService = new StockLedgerService(stockRepository);
        documents = new InMemoryBusinessDocumentRepository();
        operations = new BusinessOperationService(stockLedgerService, null, documents);
        splitService = new MotherOrderSplitService(documents, stockLedgerService);
        stationService = new ProcessStationService(documents, stockLedgerService, operations, new ScanCodeParser());
    }

    @Test
    void carvingAccountSeesOnlyItsProcessAndUnboundAccountCannotReport() {
        String childCode = splitOneChild("MD-STATION-001");
        String sticker = "JG|work_order|" + childCode;

        ProcessStationView carving = stationService.lookup(sticker, account("雕刻工 林海", "雕刻"));

        assertThat(carving.orderCode()).isEqualTo(childCode);
        assertThat(carving.customerName()).isEqualTo("恒信珠宝");
        assertThat(carving.dueDate()).isEqualTo("2026-10-20");
        assertThat(carving.stations()).extracting(ProcessStationCard::processName).containsExactly("雕刻");
        assertThat(carving.stations().get(0).requirement()).isEqualTo("内壁刻字");
        assertThat(carving.stations().get(0).previousProcessName()).isBlank();
        assertThat(carving.stations().get(0).previousSubmitted()).isTrue();
        assertThat(carving.message()).isBlank();

        ProcessStationView unbound = stationService.lookup(sticker, account("操作工 王强"));
        assertThat(unbound.stations()).isEmpty();
        assertThat(unbound.message()).contains("不能报工");
        assertThatThrownBy(() -> stationService.report(sticker, "雕刻", account("操作工 王强")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前账号不能做这道工序");
        assertThat(documents.listProcessStationHandoffs(childCode)).isEmpty();
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::documentType)
                .doesNotContain("process_station_report", "production_report");
    }

    @Test
    void rejectsSkippedProcessAndKeepsWipUntilTheLastStation() {
        String childCode = splitOneChild("MD-STATION-002");
        String sticker = "JG|work_order|" + childCode;
        AuthUserAccount carving = account("雕刻工 林海", "雕刻");
        AuthUserAccount inlay = account("镶嵌工 赵敏", "镶嵌");
        AuthUserAccount polish = account("抛光工 陈刚", "抛光");

        ProcessStationView inlayView = stationService.lookup(sticker, inlay);
        assertThat(inlayView.stations()).extracting(ProcessStationCard::processName).containsExactly("镶嵌");
        assertThat(inlayView.stations().get(0).previousProcessName()).isEqualTo("雕刻");
        assertThat(inlayView.stations().get(0).previousSubmitted()).isFalse();
        assertThatThrownBy(() -> stationService.report(sticker, "镶嵌", inlay))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("雕刻")
                .hasMessageContaining("还没交");
        assertThatThrownBy(() -> stationService.report(sticker, "抛光", polish))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还没交");

        ProcessStationReportResult carved = stationService.report(sticker, "雕刻", carving);

        assertThat(carved.movedToSemiFinished()).isFalse();
        assertThat(carved.stockStatus()).isEqualTo("WIP");
        assertThat(quantity(childCode, StockStatus.WIP)).isEqualByComparingTo("4");
        assertThat(quantity(childCode, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("0");
        assertThat(stockLedgerService.ledgers())
                .filteredOn(entry -> childCode.equals(entry.documentCode()))
                .extracting(StockLedgerEntry::documentType)
                .contains("process_station_report")
                .doesNotContain("production_report");
        assertThat(stationService.lookup(sticker, inlay).stations().get(0).previousSubmitted()).isTrue();

        ProcessStationReportResult inlaid = stationService.report(sticker, "镶嵌", inlay);
        assertThat(inlaid.movedToSemiFinished()).isFalse();
        assertThat(quantity(childCode, StockStatus.WIP)).isEqualByComparingTo("4");

        assertThatThrownBy(() -> stationService.report(sticker, "雕刻", carving))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经交过");

        ProcessStationReportResult polished = stationService.report(sticker, "抛光", polish);
        assertThat(polished.movedToSemiFinished()).isTrue();
        assertThat(polished.stockStatus()).isEqualTo("SEMI_FINISHED");
        assertThat(quantity(childCode, StockStatus.WIP)).isEqualByComparingTo("0");
        assertThat(quantity(childCode, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("4");
        assertThat(stockLedgerService.ledgers())
                .filteredOn(entry -> "production_report".equals(entry.documentType()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.from().stockStatus()).isEqualTo(StockStatus.WIP);
                    assertThat(entry.to().stockStatus()).isEqualTo(StockStatus.SEMI_FINISHED);
                    assertThat(entry.remark()).contains("工序：抛光");
                });
    }

    @Test
    void keptMotherMovesRawWaitingIntoWipBeforeTheFirstStation() {
        inbound("MD-KEEP-STATION", "10", "20");
        splitService.keep("MD-KEEP-STATION", "何琳");
        String sticker = "JG|mother|MD-KEEP-STATION";

        ProcessStationView view = stationService.lookup(sticker, account("雕刻工 林海", "雕刻"));
        assertThat(view.stations()).extracting(ProcessStationCard::processName).containsExactly("雕刻");
        assertThat(rawWaiting("MD-KEEP-STATION").quantity()).isEqualByComparingTo("10");

        stationService.report(sticker, "雕刻", account("雕刻工 林海", "雕刻"));

        assertThat(rawWaiting("MD-KEEP-STATION").quantity()).isEqualByComparingTo("0");
        assertThat(quantity("MD-KEEP-STATION", StockStatus.WIP)).isEqualByComparingTo("10");
        assertThat(quantity("MD-KEEP-STATION", StockStatus.SEMI_FINISHED)).isEqualByComparingTo("0");
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::documentType)
                .contains("work_order_split", "process_station_report")
                .doesNotContain("production_report");

        stationService.report(sticker, "镶嵌", account("镶嵌工 赵敏", "镶嵌"));
        stationService.report(sticker, "抛光", account("抛光工 陈刚", "抛光"));

        assertThat(quantity("MD-KEEP-STATION", StockStatus.WIP)).isEqualByComparingTo("0");
        assertThat(quantity("MD-KEEP-STATION", StockStatus.SEMI_FINISHED)).isEqualByComparingTo("10");
    }

    @Test
    void skippedProcessOnTheOrderIsNotRequiredAndLastSelectedProcessFinishesStock() {
        inbound("MD-PARTIAL", "8", "16");
        var split = splitService.split(new SplitMotherOrderCommand("MD-PARTIAL", "何琳", List.of(
                new SplitMotherOrderLine(new BigDecimal("3"), new BigDecimal("6"), List.of("抛光", "雕刻")),
                new SplitMotherOrderLine(new BigDecimal("5"), new BigDecimal("10"), null)
        )));
        String childCode = split.children().get(0).code();
        String sticker = "JG|work_order|" + childCode;

        assertThatThrownBy(() -> stationService.report(sticker, "镶嵌", account("镶嵌工 赵敏", "镶嵌")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有「镶嵌」");
        assertThatThrownBy(() -> stationService.report(sticker, "抛光", account("抛光工 陈刚", "抛光")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("雕刻");

        stationService.report(sticker, "雕刻", account("雕刻工 林海", "雕刻"));
        assertThat(quantity(childCode, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("0");

        ProcessStationReportResult polished = stationService.report(sticker, "抛光", account("抛光工 陈刚", "抛光"));
        assertThat(polished.movedToSemiFinished()).isTrue();
        assertThat(quantity(childCode, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("3");
    }

    @Test
    void productionReplayCommitsOnceAndRejectsStaleOrForeignRetry() {
        String childCode = splitOneChild("MD-REPLAY-STATION");
        ProcessStationService replay = new ProcessStationService(
                documents,
                stockLedgerService,
                operations,
                new ScanCodeParser(),
                null,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository())
        );
        AuthUserAccount carving = account("雕刻工 林海", "雕刻");
        ReportProductionCommand command = production(childCode, "MD-REPLAY-STATION", "4", "8", "雕刻");

        ProductionReportResult first = replay.reportProduction(command, carving, "station-replay-001");
        ProductionReportResult second = replay.reportProduction(command, carving, "station-replay-001");

        assertThat(second.ledgerCode()).isEqualTo(first.ledgerCode());
        assertThat(documents.listProcessStationHandoffs(childCode)).hasSize(1);
        assertThat(stockLedgerService.ledgers())
                .filteredOn(entry -> "process_station_report".equals(entry.documentType()))
                .hasSize(1);
        assertThatThrownBy(() -> replay.reportProduction(
                production(childCode, "MD-REPLAY-STATION", "3", "6", "雕刻"),
                carving,
                "station-replay-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThatThrownBy(() -> replay.reportProduction(command, account("别人", "雕刻"), "station-replay-001"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("跨账号");
        assertThat(documents.listProcessStationHandoffs(childCode)).hasSize(1);
    }

    @Test
    void productionFailureRollsBackHandoffAndStock() {
        String childCode = splitOneChild("MD-ROLLBACK-STATION");
        ProcessStationService replay = new ProcessStationService(
                documents,
                stockLedgerService,
                operations,
                new ScanCodeParser(),
                null,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()),
                () -> {
                    throw new BusinessException("模拟报工中途失败");
                }
        );
        int ledgersBefore = stockLedgerService.ledgers().size();

        assertThatThrownBy(() -> replay.reportProduction(
                production(childCode, "MD-ROLLBACK-STATION", "4", "8", "雕刻"),
                account("雕刻工 林海", "雕刻"),
                "station-rollback-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模拟报工中途失败");

        assertThat(documents.listProcessStationHandoffs(childCode)).isEmpty();
        assertThat(stockLedgerService.ledgers()).hasSize(ledgersBefore);
        assertThat(quantity(childCode, StockStatus.WIP)).isEqualByComparingTo("4");
    }

    @Test
    void splitMotherStickerCannotBeReportedAsAStation() {
        String childCode = splitOneChild("MD-ALREADY-SPLIT");
        assertThat(childCode).isNotBlank();

        assertThatThrownBy(() -> stationService.lookup("JG|mother|MD-ALREADY-SPLIT", account("雕刻工 林海", "雕刻")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请扫描子单贴纸");
    }


    private ProcessStationService batchService(com.zhubao.erp.work.domain.CommandResultProbe probe) {
        return new ProcessStationService(documents, stockLedgerService, operations, new ScanCodeParser(), null,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), probe));
    }
    private com.zhubao.erp.business.domain.ProcessStationReportCommand batch(String code, String process, String id, String quantity, String weight) {
        return new com.zhubao.erp.business.domain.ProcessStationReportCommand(code, process, "req-" + id, null, new BigDecimal(quantity), weight == null ? null : new BigDecimal(weight));
    }
    @Test void partialHandoffFlowsDownstreamAndWeightWaitsUntilFinalQuantity() {
        String code = splitOneChild("MD-BATCH");
        var service = batchService(com.zhubao.erp.work.domain.CommandResultProbe.NONE);
        var carving = account("雕刻工 林海", "雕刻"); var inlay = account("镶嵌工 赵敏", "镶嵌"); var polish = account("抛光工 陈刚", "抛光");
        var first = service.reportBatch(batch(code, "雕刻", "batch-1", "2", null), carving);
        assertThat(first.processCompleted()).isFalse(); assertThat(first.deliveredQuantity()).isEqualByComparingTo("2");
        assertThat(service.reportBatch(batch(code, "雕刻", "batch-1", "2.0", null), carving)).isEqualTo(first);
        assertThat(documents.listProcessBatches(code, null)).hasSize(1);
        assertThat(documents.listProcessStationHandoffs(code)).isEmpty();
        assertThat(service.lookup(code, inlay).stations().get(0).availableQuantity()).isEqualByComparingTo("2");
        assertThat(service.openProgress(code, inlay).currentProcess()).isEqualTo("镶嵌");
        assertThatThrownBy(() -> service.openProgress(code, polish)).hasMessageContaining("不能做");
        assertThatThrownBy(() -> service.reportBatch(batch(code, "镶嵌", "batch-over", "3", null), inlay)).hasMessageContaining("超过");
        service.reportBatch(batch(code, "镶嵌", "batch-2", "2", null), inlay);
        service.reportBatch(batch(code, "抛光", "batch-3", "2", null), polish);
        assertThat(quantity(code, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.reportBatch(batch(code, "抛光", "batch-weight-early", "0", "7.8"), polish)).hasMessageContaining("全部交齐");
        assertThatThrownBy(() -> service.report(code, "雕刻", carving, "legacy-bypass")).hasMessageContaining("分批");
        service.reportBatch(batch(code, "雕刻", "batch-4", "2", null), carving);
        service.reportBatch(batch(code, "镶嵌", "batch-5", "2", null), inlay);
        var lastQuantity = service.reportBatch(batch(code, "抛光", "batch-6", "2", null), polish);
        assertThat(lastQuantity.processCompleted()).isFalse(); assertThat(lastQuantity.remainingQuantity()).isEqualByComparingTo("0");
        assertThat(documents.findProcessStationHandoff(code, "抛光")).isEmpty();
        var weighed = service.reportBatch(batch(code, "抛光", "batch-weigh", "0", "7.8"), polish);
        assertThat(weighed.processCompleted()).isTrue(); assertThat(weighed.finalWeight()).isEqualByComparingTo("7.8");
        assertThat(quantity(code, StockStatus.SEMI_FINISHED)).isEqualByComparingTo("4");
        assertThat(documents.listProcessStationHandoffs(code)).hasSize(3);
        assertThat(service.reportBatch(batch(code, "抛光", "batch-weigh", "0", "7.8"), polish)).isEqualTo(weighed);
        assertThatThrownBy(() -> service.reportBatch(batch(code, "抛光", "batch-weigh", "0", "7.9"), polish)).hasMessageContaining("内容不一致");
    }
    @Test void batchRejectsUnboundAndInvalidNumbersAndRollsBackFailedResult() {
        String code = splitOneChild("MD-BATCH-FAIL"); var service = batchService(com.zhubao.erp.work.domain.CommandResultProbe.NONE);
        assertThatThrownBy(() -> service.reportBatch(batch(code, "镶嵌", "batch-role", "1", null), account("雕刻工 林海", "雕刻"))).hasMessageContaining("不能做");
        assertThatThrownBy(() -> service.reportBatch(batch(code, "雕刻", "batch-negative", "-1", null), account("雕刻工 林海", "雕刻"))).hasMessageContaining("非负数");
        assertThatThrownBy(() -> service.reportBatch(batch(code, "雕刻", "batch-precision", "0.0001", null), account("雕刻工 林海", "雕刻"))).hasMessageContaining("三位");
        int ledgers = stockLedgerService.ledgers().size();
        var failing = batchService(() -> { throw new IllegalStateException("模拟回执失败"); });
        assertThatThrownBy(() -> failing.reportBatch(batch(code, "雕刻", "batch-fail", "4", null), account("雕刻工 林海", "雕刻"))).hasMessageContaining("模拟回执失败");
        assertThat(documents.listProcessBatches(code, null)).isEmpty(); assertThat(documents.listProcessStationHandoffs(code)).isEmpty();
        assertThat(stockLedgerService.ledgers()).hasSize(ledgers);
    }
    @Test void concurrentBatchRequestsCannotOverDeliver() throws Exception {
        String code = splitOneChild("MD-BATCH-RACE"); var service = batchService(com.zhubao.erp.work.domain.CommandResultProbe.NONE);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> one = () -> { try { service.reportBatch(batch(code, "雕刻", java.util.UUID.randomUUID().toString(), "3", null), account("雕刻工 林海", "雕刻")); return true; } catch (BusinessException ex) { return false; } };
            var a = pool.submit(one); var b = pool.submit(one);
            assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
            assertThat(documents.listProcessBatches(code, null)).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    private String splitOneChild(String motherCode) {
        inbound(motherCode, "10", "20");
        var view = splitService.split(new SplitMotherOrderCommand(motherCode, "何琳", List.of(
                new SplitMotherOrderLine(new BigDecimal("4"), new BigDecimal("8"), null),
                new SplitMotherOrderLine(new BigDecimal("6"), new BigDecimal("12"), null)
        )));
        return view.children().get(0).code();
    }

    private void inbound(String code, String quantity, String weight) {
        operations.confirmInbound(new ConfirmInboundCommand(
                code,
                1001L,
                1001L,
                "AU9999",
                "2026-10-08",
                "顺丰",
                "SF" + code.replace("-", ""),
                "",
                "何琳",
                "何琳",
                "戒托",
                "",
                null,
                "",
                "RAW-CUSTOMER",
                new BigDecimal(quantity),
                "件",
                new BigDecimal(weight),
                "何琳",
                "",
                "测试入库",
                "内壁刻字",
                "2026-10-20",
                "恒信珠宝"
        ));
    }

    private ReportProductionCommand production(String workOrder, String mother, String quantity, String weight, String process) {
        return new ReportProductionCommand(
                mother,
                workOrder,
                1001L,
                1001L,
                "AU9999",
                "RAW-CUSTOMER",
                "RAW-CUSTOMER",
                new BigDecimal(quantity),
                "件",
                new BigDecimal(weight),
                "2026-10-08",
                process,
                null,
                null,
                null,
                null,
                null,
                null,
                "完成",
                "已审核",
                null,
                null,
                null,
                "雕刻工 林海",
                null,
                null,
                null
        );
    }

    private AuthUserAccount account(String operator, String... processes) {
        return new AuthUserAccount(
                operator,
                "hash",
                operator,
                RoleCode.OPERATOR,
                DataScope.SELF,
                true,
                true
        ).withAllowedProcesses(List.of(processes));
    }

    private BigDecimal quantity(String orderCode, StockStatus status) {
        return stockLedgerService.balances().stream()
                .filter(balance -> orderCode.equals(balance.dimension().workOrderCode())
                        || (balance.dimension().workOrderCode() == null && orderCode.equals(balance.dimension().motherOrderCode())))
                .filter(balance -> balance.dimension().stockStatus() == status)
                .map(StockBalance::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private StockBalance rawWaiting(String motherOrderCode) {
        return stockLedgerService.balances().stream()
                .filter(balance -> motherOrderCode.equals(balance.dimension().motherOrderCode()))
                .filter(balance -> balance.dimension().workOrderCode() == null)
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.RAW_WAITING)
                .findFirst()
                .orElseThrow();
    }
}
