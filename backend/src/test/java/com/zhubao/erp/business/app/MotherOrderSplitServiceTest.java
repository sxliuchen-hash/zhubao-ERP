package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderView;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.MotherOrderSplitView;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.domain.VerificationCodes;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.label.app.LabelService;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.ReprintLabelCommand;
import com.zhubao.erp.label.infra.InMemoryLabelTemplateRepository;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.domain.ScanObjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 母单不拆、拆成子单，以及贴纸验证码。
 */
class MotherOrderSplitServiceTest {

    private InMemoryStockLedgerRepository stockRepository;
    private StockLedgerService stockLedgerService;
    private InMemoryBusinessDocumentRepository documents;
    private BusinessOperationService operations;
    private MotherOrderSplitService splitService;
    private LabelService labelService;
    private final ScanCodeParser scanCodeParser = new ScanCodeParser();

    @BeforeEach
    void setUp() {
        stockRepository = new InMemoryStockLedgerRepository();
        stockLedgerService = new StockLedgerService(stockRepository);
        documents = new InMemoryBusinessDocumentRepository();
        operations = new BusinessOperationService(stockLedgerService, null, documents);
        splitService = new MotherOrderSplitService(
                documents,
                stockLedgerService,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository())
        );
        labelService = new LabelService(documents, new InMemoryLabelTemplateRepository());
    }

    @Test
    void keepPrintsOneMotherStickerWithoutMovingStock() {
        inbound("MD-KEEP-001", "10", "20");

        MotherOrderSplitView view = splitService.keep("MD-KEEP-001", "何琳");
        LabelPayload sticker = labelService.generateMotherLabel("MD-KEEP-001");

        assertThat(view.decision()).isEqualTo("KEEP");
        assertThat(view.children()).isEmpty();
        assertThat(view.verificationCode()).isNotBlank();
        assertThat(sticker.qrContent()).startsWith("JG|mother|MD-KEEP-001|0|");
        assertThat(sticker.qrContent().split("\\|", -1)).hasSize(5);
        assertThat(sticker.fields()).extracting(LabelField::name)
                .contains("验证码", "客户", "加工要求", "期限");
        assertThat(sticker.primaryCode()).isEqualTo("MD-KEEP-001");
        assertThat(value(sticker, "验证码")).isEqualTo(view.verificationCode());
        assertThat(value(sticker, "客户")).isEqualTo("恒信珠宝");
        assertThat(value(sticker, "加工要求")).isEqualTo("内壁刻字");
        assertThat(value(sticker, "期限")).isEqualTo("2026-10-20");
        assertThat(scanCodeParser.resolve(sticker.qrContent(), "lookup").objectType())
                .isEqualTo(ScanObjectType.MOTHER_ORDER);
        assertThat(scanCodeParser.resolve(sticker.qrContent(), "lookup").code()).isEqualTo("MD-KEEP-001");
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::documentType)
                .containsExactly("inbound_confirm");
        assertThat(rawWaiting("MD-KEEP-001").quantity()).isEqualByComparingTo("10");
        assertThat(wipBalances()).isEmpty();

        LabelPayload reprinted = labelService.reprint(new ReprintLabelCommand(
                "MOTHER", "MD-KEEP-001", "贴纸丢失", "何琳"));
        assertThat(value(reprinted, "验证码")).isEqualTo(view.verificationCode());
        assertThat(documents.listLabelReprints("MOTHER", "MD-KEEP-001")).singleElement()
                .satisfies(log -> {
                    assertThat(log.reason()).isEqualTo("贴纸丢失");
                    assertThat(log.verificationCode()).isEqualTo(view.verificationCode());
                });
        assertThatThrownBy(() -> labelService.reprint(new ReprintLabelCommand("MOTHER", "MD-KEEP-001", "  ", "何琳")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("补打必须填写原因");
    }

    @Test
    void splitTwoChildrenMovesStockAndPrintsDifferentVerificationCodes() {
        inbound("MD-SPLIT-001", "10", "20");

        assertThatThrownBy(() -> splitService.split(command("MD-SPLIT-001",
                line("6", "12", null),
                line("5", "8", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量合计不得超过母单剩余");
        assertThat(documents.listChildWorkOrders("MD-SPLIT-001")).isEmpty();
        assertThat(wipBalances()).isEmpty();

        MotherOrderSplitView view = splitService.split(command("MD-SPLIT-001",
                line("4", "8", List.of("抛光", "雕刻")),
                line("6", "12", null)));

        assertThat(view.decision()).isEqualTo("SPLIT");
        assertThat(view.remainingQuantity()).isEqualByComparingTo("0");
        assertThat(view.remainingWeight()).isEqualByComparingTo("0");
        assertThat(view.children()).hasSize(2);
        assertThat(view.children()).extracting(ChildWorkOrderView::verificationCode)
                .doesNotHaveDuplicates()
                .allMatch(code -> code != null && !code.isBlank());
        assertThat(view.children().get(0).processes()).containsExactly("雕刻", "抛光");
        assertThat(view.children().get(1).processes()).containsExactly("雕刻", "镶嵌", "抛光");
        assertThat(view.children()).allSatisfy(child -> {
            assertThat(child.customerName()).isEqualTo("恒信珠宝");
            assertThat(child.processingRequirement()).isEqualTo("内壁刻字");
            assertThat(child.dueDate()).isEqualTo("2026-10-20");
            LabelPayload sticker = labelService.generateWorkOrderLabel(child.code());
            assertThat(sticker.qrContent()).startsWith("JG|work_order|" + child.code() + "|0|");
            assertThat(sticker.qrContent().split("\\|", -1)).hasSize(5);
            assertThat(value(sticker, "验证码")).isEqualTo(child.verificationCode());
            assertThat(value(sticker, "客户")).isEqualTo("恒信珠宝");
            assertThat(value(sticker, "加工要求")).isEqualTo("内壁刻字");
            assertThat(value(sticker, "期限")).isEqualTo("2026-10-20");
            assertThat(sticker.primaryCode()).isEqualTo(child.code());
            assertThat(child.code()).matches("ZD-\\d{8}-\\d{4}");
            assertThat(scanCodeParser.resolve(sticker.qrContent(), "lookup").objectType())
                    .isEqualTo(ScanObjectType.WORK_ORDER);
            assertThat(scanCodeParser.resolve(sticker.qrContent(), "lookup").code()).isEqualTo(child.code());
        });
        assertThat(rawWaiting("MD-SPLIT-001").quantity()).isEqualByComparingTo("0");
        assertThat(wipBalances()).hasSize(2);
        assertThat(stockLedgerService.ledgers())
                .filteredOn(entry -> "work_order_split".equals(entry.documentType()))
                .hasSize(2)
                .allSatisfy(entry -> {
                    assertThat(entry.from().stockStatus()).isEqualTo(StockStatus.RAW_WAITING);
                    assertThat(entry.to().stockStatus()).isEqualTo(StockStatus.WIP);
                });
        assertThatThrownBy(() -> splitService.keep("MD-SPLIT-001", "何琳"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能再选择不拆");
        assertThatThrownBy(() -> labelService.generateMotherLabel("MD-SPLIT-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请打印子单贴纸");
    }

    @Test
    void rejectsSplitAfterKeepAndWeightBeyondRemaining() {
        inbound("MD-KEEP-002", "10", "20");
        splitService.keep("MD-KEEP-002", "何琳");

        assertThatThrownBy(() -> splitService.split(command("MD-KEEP-002",
                line("4", "8", null),
                line("6", "12", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已选择不拆");

        inbound("MD-SPLIT-002", "10", "20");
        assertThatThrownBy(() -> splitService.split(command("MD-SPLIT-002",
                line("4", "12", null),
                line("6", "12", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重量合计不得超过母单剩余");
        assertThatThrownBy(() -> splitService.split(command("MD-SPLIT-002", line("10", "20", List.of()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要两张子单");
        assertThatThrownBy(() -> splitService.split(command("MD-SPLIT-002",
                line("4", "8", null),
                line("6", "12", List.of("焊接")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知工序");
    }

    @Test
    void replayedSplitCommitsStockChildrenAndDecisionOnce() {
        inbound("MD-REPLAY-001", "10", "20");
        SplitMotherOrderCommand splitCommand = command("MD-REPLAY-001",
                line("4", "8", List.of("雕刻")),
                line("6", "12", null));

        MotherOrderSplitView first = splitService.split(splitCommand, "split-replay-001");
        MotherOrderSplitView second = splitService.split(splitCommand, "split-replay-001");

        assertThat(second.children()).extracting(ChildWorkOrderView::code)
                .containsExactlyElementsOf(first.children().stream().map(ChildWorkOrderView::code).toList());
        assertThat(documents.listChildWorkOrders("MD-REPLAY-001")).hasSize(2);
        assertThat(documents.findMotherSplitState("MD-REPLAY-001")).get()
                .extracting(state -> state.decision())
                .isEqualTo("SPLIT");
        assertThat(stockLedgerService.ledgers())
                .filteredOn(entry -> "work_order_split".equals(entry.documentType()))
                .hasSize(2);
        assertThat(rawWaiting("MD-REPLAY-001").quantity()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> splitService.split(command("MD-REPLAY-001",
                line("3", "6", null),
                line("7", "14", null)), "split-replay-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThat(documents.listChildWorkOrders("MD-REPLAY-001")).hasSize(2);
    }

    @Test
    void keepRequiresRequestIdAndReplaysTheSameDecision() {
        inbound("MD-KEEP-REQ", "10", "20");
        assertThatThrownBy(() -> splitService.keep("MD-KEEP-REQ", "何琳", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须带请求编号");
        assertThat(documents.findMotherSplitState("MD-KEEP-REQ")).isEmpty();

        MotherOrderSplitView first = splitService.keep("MD-KEEP-REQ", "何琳", "keep-replay-001");
        MotherOrderSplitView second = splitService.keep("MD-KEEP-REQ", "何琳", "keep-replay-001");
        assertThat(second.verificationCode()).isEqualTo(first.verificationCode());
        assertThat(stockLedgerService.ledgers()).extracting(StockLedgerEntry::documentType)
                .containsExactly("inbound_confirm");
        assertThatThrownBy(() -> splitService.keep("MD-KEEP-REQ", "别人", "keep-replay-001"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("跨账号");
    }

    @Test
    void failedSplitRollsBackStockChildrenAndDecision() {
        InMemoryStockLedgerRepository stocks = new InMemoryStockLedgerRepository();
        StockLedgerService ledger = new StockLedgerService(new SecondSessionSaveFails(stocks));
        InMemoryBusinessDocumentRepository docs = new InMemoryBusinessDocumentRepository();
        BusinessOperationService ops = new BusinessOperationService(ledger, null, docs);
        MotherOrderSplitService splits = new MotherOrderSplitService(
                docs,
                ledger,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository())
        );
        ops.confirmInbound(inboundCommand("MD-ROLLBACK-001", "10", "20"));

        assertThatThrownBy(() -> splits.split(command("MD-ROLLBACK-001",
                line("4", "8", null),
                line("6", "12", null)), "split-rollback-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模拟拆单中途失败");

        assertThat(docs.listChildWorkOrders("MD-ROLLBACK-001")).isEmpty();
        assertThat(docs.findMotherSplitState("MD-ROLLBACK-001")).isEmpty();
        assertThat(ledger.ledgers()).extracting(StockLedgerEntry::documentType).containsExactly("inbound_confirm");
        assertThat(ledger.balances().stream()
                .filter(balance -> "MD-ROLLBACK-001".equals(balance.dimension().motherOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.RAW_WAITING)
                .findFirst()
                .orElseThrow()
                .quantity()).isEqualByComparingTo("10");
    }

    @Test
    void concurrentSplitsCannotExceedRemaining() throws Exception {
        inbound("MD-RACE-001", "10", "20");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> task = () -> {
                start.await();
                String requestId = "race-" + Thread.currentThread().getId() + "-0001";
                splitService.split(command("MD-RACE-001",
                        line("4", "8", null),
                        line("6", "12", null)), requestId);
                return "ok";
            };
            List<Future<String>> futures = List.of(pool.submit(task), pool.submit(task));
            start.countDown();
            int succeeded = 0;
            int rejected = 0;
            for (Future<String> future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
                    rejected++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            assertThat(documents.listChildWorkOrders("MD-RACE-001")).hasSize(2);
            assertThat(rawWaiting("MD-RACE-001").quantity()).isEqualByComparingTo("0");
            assertThat(wipBalances()).allSatisfy(balance -> assertThat(balance.quantity()).isGreaterThan(BigDecimal.ZERO));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void lengthensVerificationCodeWhenTheShortCodeIsAlreadyTaken() {
        String shortCode = VerificationCodes.derive("ZD-SAME", candidate -> false);
        String longer = VerificationCodes.derive("ZD-SAME", candidate -> candidate.length() == 4);

        assertThat(shortCode).hasSize(4);
        assertThat(longer).hasSize(5).startsWith(shortCode);
    }

    private void inbound(String code, String quantity, String weight) {
        operations.confirmInbound(inboundCommand(code, quantity, weight));
    }

    private ConfirmInboundCommand inboundCommand(String code, String quantity, String weight) {
        return new ConfirmInboundCommand(
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
        );
    }

    private SplitMotherOrderCommand command(String motherOrderCode, SplitMotherOrderLine... lines) {
        return new SplitMotherOrderCommand(motherOrderCode, "何琳", List.of(lines));
    }

    private SplitMotherOrderLine line(String quantity, String weight, List<String> processes) {
        return new SplitMotherOrderLine(new BigDecimal(quantity), new BigDecimal(weight), processes);
    }

    private StockBalance rawWaiting(String motherOrderCode) {
        return stockLedgerService.balances().stream()
                .filter(balance -> motherOrderCode.equals(balance.dimension().motherOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.RAW_WAITING)
                .findFirst()
                .orElseThrow();
    }

    private List<StockBalance> wipBalances() {
        return stockLedgerService.balances().stream()
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.WIP)
                .filter(balance -> balance.quantity().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private static final class SecondSessionSaveFails implements StockLedgerRepository {
        private final StockLedgerRepository delegate;
        private int sessionSaves;

        private SecondSessionSaveFails(StockLedgerRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public com.zhubao.erp.inventory.domain.StockLedgerEntry save(com.zhubao.erp.inventory.domain.StockLedgerEntry entry) {
            return delegate.save(entry);
        }

        @Override
        public com.zhubao.erp.inventory.domain.StockLedgerEntry save(
                com.zhubao.erp.inventory.domain.StockLedgerEntry entry,
                WorkSession session
        ) {
            sessionSaves++;
            if (sessionSaves >= 2) {
                throw new BusinessException("模拟拆单中途失败");
            }
            return delegate.save(entry, session);
        }

        @Override
        public List<com.zhubao.erp.inventory.domain.StockLedgerEntry> findAllLedgers() {
            return delegate.findAllLedgers();
        }

        @Override
        public List<com.zhubao.erp.inventory.domain.StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes) {
            return delegate.findLedgersByCustomers(customerIds, motherOrderCodes);
        }

        @Override
        public List<com.zhubao.erp.inventory.domain.StockBalance> findAllBalances() {
            return delegate.findAllBalances();
        }

        @Override
        public com.zhubao.erp.inventory.domain.StockBalance findBalance(com.zhubao.erp.inventory.domain.StockDimension dimension) {
            return delegate.findBalance(dimension);
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    private String value(LabelPayload payload, String name) {
        return payload.fields().stream()
                .filter(field -> name.equals(field.name()))
                .map(LabelField::value)
                .findFirst()
                .orElse("");
    }
}
