package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalTaskRepository;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessReviewData.BusinessWorkspaceSummary;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryBusinessWorkspaceRepository;
import com.zhubao.erp.business.infra.BusinessWorkspaceSeedData;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 千单、万条台账下的工作台读取基准。内存仓储计数，不代表 MySQL 延迟。
 */
class FormalBusinessWorkspaceQueryLoadTest {
    private static final int MOTHERS = 1000;
    private static final int LEDGERS_PER_MOTHER = 10;
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void recordsRepositoryCallsAndTimingForThousandOrders() throws Exception {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        FixtureStock stock = new FixtureStock();
        seed(documents, stock);
        Map<String, Integer> documentCalls = new LinkedHashMap<>();
        Map<String, Integer> approvalCalls = new LinkedHashMap<>();
        BusinessDocumentRepository countedDocuments = counting(BusinessDocumentRepository.class, documents, documentCalls);
        ApprovalTaskRepository countedApprovals = counting(ApprovalTaskRepository.class, new InMemoryApprovalTaskRepository(), approvalCalls);
        BusinessWorkspaceService service = new BusinessWorkspaceService(
                new InMemoryBusinessWorkspaceRepository(new BusinessWorkspaceSeedData()),
                countedDocuments,
                new ProgressBoardService(countedDocuments),
                new StockLedgerService(stock),
                new ApprovalService(countedApprovals),
                false
        );

        service.summary();
        Map<String, Reading> readings = new LinkedHashMap<>();
        BusinessWorkspaceSummary[] summaryHolder = new BusinessWorkspaceSummary[1];
        readings.put("summary", read("summary", documentCalls, approvalCalls, stock, () -> summaryHolder[0] = service.summary()));
        InboundWorkspaceData inbound;
        ProductionWorkspaceData production;
        QualityWorkspaceData quality;
        InventoryWorkspaceData inventory;
        MobileWorkspaceData mobile;

        documentCalls.clear();
        approvalCalls.clear();
        stock.reset();
        long started = System.nanoTime();
        inbound = service.inbound();
        readings.put("inbound", reading("inbound", started, documentCalls, approvalCalls, stock));

        documentCalls.clear();
        approvalCalls.clear();
        stock.reset();
        started = System.nanoTime();
        production = service.production();
        readings.put("production", reading("production", started, documentCalls, approvalCalls, stock));

        documentCalls.clear();
        approvalCalls.clear();
        stock.reset();
        started = System.nanoTime();
        quality = service.quality();
        readings.put("quality", reading("quality", started, documentCalls, approvalCalls, stock));

        documentCalls.clear();
        approvalCalls.clear();
        stock.reset();
        started = System.nanoTime();
        inventory = service.inventory();
        readings.put("inventory", reading("inventory", started, documentCalls, approvalCalls, stock));

        documentCalls.clear();
        approvalCalls.clear();
        stock.reset();
        started = System.nanoTime();
        mobile = service.mobile();
        readings.put("mobile", reading("mobile", started, documentCalls, approvalCalls, stock));

        String fingerprint = fingerprint(summaryHolder[0], inbound, production, quality, inventory, mobile);
        String report = report(readings, fingerprint);
        Path output = Path.of(".cache", "r14-baseline.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
        System.out.println(report);

        assertThat(inbound.orders()).hasSize(MOTHERS);
        assertThat(inventory.ledgerEntries()).hasSize(MOTHERS * LEDGERS_PER_MOTHER);
        assertThat(stock.ledgerSize()).isEqualTo(MOTHERS * LEDGERS_PER_MOTHER);
        assertThat(fingerprint).isEqualTo("f3a90636747b81025e52486c353db93037f9e87f30cd66b9d5f817782274886b");
        assertThat(readings.get("summary").ledgerReads()).isEqualTo(1);
        assertThat(readings.get("summary").balanceReads()).isEqualTo(1);
        assertThat(readings.get("summary").approvalCalls()).containsExactly(Map.entry("findAll", 1));
        assertThat(readings.get("summary").documentCalls()).containsExactly(
                Map.entry("listAllChildWorkOrders", 1),
                Map.entry("listAllMotherSplitStates", 1),
                Map.entry("listAllProcessStationHandoffs", 1),
                Map.entry("listAllQualityRequirementChecks", 1),
                Map.entry("listMotherOrders", 1)
        );
        assertThat(readings.get("inbound").ledgerReads()).isZero();
        assertThat(readings.get("inbound").balanceReads()).isEqualTo(1);
        assertThat(readings.get("inbound").documentCalls()).containsExactly(Map.entry("listMotherOrders", 1));
        assertThat(readings.get("production").ledgerReads()).isEqualTo(1);
        assertThat(readings.get("production").balanceReads()).isEqualTo(1);
        assertThat(readings.get("production").documentCalls().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(5);
        assertThat(readings.get("quality").ledgerReads()).isEqualTo(1);
        assertThat(readings.get("quality").approvalCalls()).containsExactly(Map.entry("findAll", 1));
        assertThat(readings.get("inventory").ledgerReads()).isEqualTo(1);
        assertThat(readings.get("inventory").balanceReads()).isEqualTo(1);
        assertThat(readings.get("inventory").documentCalls()).containsEntry("listAllProcessStationHandoffs", 1);
        assertThat(readings.get("inventory").documentCalls()).doesNotContainKey("findMotherOrderByCode");
        assertThat(readings.get("mobile").ledgerReads()).isEqualTo(1);
        assertThat(readings.get("mobile").balanceReads()).isEqualTo(1);
        assertThat(readings.get("mobile").documentCalls()).doesNotContainKey("findChildWorkOrderByCode");
        assertThat(readings.get("mobile").documentCalls()).doesNotContainKey("findMotherSplitState");
        assertThat(stock.rangeReads()).isZero();
    }

    @Test
    void customerScopeRangeKeepsTheSameVisibleLedgerAsFullRead() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        FixtureStock full = new FixtureStock(false);
        FixtureStock ranged = new FixtureStock(true);
        seedCustomer(documents, full, ranged, "MD-A", 7001L, "客户甲");
        seedCustomer(documents, full, ranged, "MD-B", 7002L, "客户乙");
        BusinessWorkspaceService fullService = workspace(documents, full);
        BusinessWorkspaceService rangedService = workspace(documents, ranged);
        for (OperatorContext operator : List.of(
                new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", List.of("客户甲")),
                new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", List.of("7001"))
        )) {
            assertThat(rangedService.inbound(operator)).isEqualTo(fullService.inbound(operator));
            assertThat(rangedService.production(operator)).isEqualTo(fullService.production(operator));
            assertThat(rangedService.quality(operator)).isEqualTo(fullService.quality(operator));
            assertThat(rangedService.inventory(operator)).isEqualTo(fullService.inventory(operator));
            assertThat(rangedService.mobile(operator)).isEqualTo(fullService.mobile(operator));
            assertThat(rangedService.summary(operator)).isEqualTo(fullService.summary(operator));
        }
        full.reset();
        ranged.reset();
        OperatorContext byId = new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", List.of("7001"));
        fullService.inventory(byId);
        rangedService.inventory(byId);
        assertThat(full.rangeReads()).isEqualTo(1);
        assertThat(ranged.rangeReads()).isEqualTo(1);
        assertThat(full.ledgerReads()).isEqualTo(1);
        assertThat(ranged.ledgerReads()).isEqualTo(1);
        OperatorContext unassigned = new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE);
        ranged.reset();
        assertThat(rangedService.inventory(unassigned).ledgerEntries()).isEmpty();
        assertThat(ranged.ledgerReads()).isZero();
    }

    private static Reading read(String name, Map<String, Integer> documentCalls, Map<String, Integer> approvalCalls,
                                FixtureStock stock, Runnable action) {
        documentCalls.clear();
        approvalCalls.clear();
        stock.reset();
        long started = System.nanoTime();
        action.run();
        return reading(name, started, documentCalls, approvalCalls, stock);
    }

    private static Reading reading(String name, long started, Map<String, Integer> documentCalls,
                                   Map<String, Integer> approvalCalls, FixtureStock stock) {
        long elapsed = System.nanoTime() - started;
        return new Reading(name, elapsed, copy(documentCalls), copy(approvalCalls), stock.ledgerReads, stock.balanceReads);
    }

    private static Map<String, Integer> copy(Map<String, Integer> source) {
        return source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
    }

    private static String fingerprint(BusinessWorkspaceSummary summary, InboundWorkspaceData inbound,
                                      ProductionWorkspaceData production, QualityWorkspaceData quality,
                                      InventoryWorkspaceData inventory, MobileWorkspaceData mobile) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        add(digest, summary);
        add(digest, inbound);
        add(digest, production);
        add(digest, quality);
        add(digest, inventory);
        add(digest, mobile);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void add(MessageDigest digest, Object value) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static String report(Map<String, Reading> readings, String fingerprint) {
        StringBuilder report = new StringBuilder();
        report.append("environment=").append(System.getProperty("java.version"))
                .append(" / ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(" / in-memory repositories, not MySQL\n");
        report.append("sample=mothers:").append(MOTHERS)
                .append(", ledgers:").append(MOTHERS * LEDGERS_PER_MOTHER)
                .append(", method=one warmup summary then one timed call per entry, JDK dynamic proxy counts document and approval reads\n");
        report.append("fingerprint=").append(fingerprint).append('\n');
        for (Reading reading : readings.values()) {
            report.append(reading).append('\n');
        }
        return report.toString();
    }

    private static BusinessWorkspaceService workspace(InMemoryBusinessDocumentRepository documents, FixtureStock stock) {
        return new BusinessWorkspaceService(
                new InMemoryBusinessWorkspaceRepository(new BusinessWorkspaceSeedData()),
                documents,
                new ProgressBoardService(documents),
                new StockLedgerService(stock),
                new ApprovalService(new InMemoryApprovalTaskRepository()),
                false
        );
    }

    private static void seedCustomer(InMemoryBusinessDocumentRepository documents, FixtureStock first, FixtureStock second,
                                     String mother, Long customerId, String customerName) {
        documents.saveMotherOrder(new MotherOrderRecord(
                mother, customerId, customerId, "MAT-REAL", "2026-10-10", "", "", "", "收货员", "",
                "货品", "CUSTOMER", true, "RAW", new BigDecimal("2"), "件", new BigDecimal("2"), "RAW_WAITING",
                mother + "-0", "", null, "", "收货员", "", "", "已入库",
                "2026-10-10T00:00:00", "无划痕", "2099-01-01", customerName
        ));
        documents.saveMotherSplitState(new MotherSplitState(mother, MotherSplitState.KEEP, mother + "-VF", "收货员", "2026-10-10T00:00:00"));
        StockDimension raw = new StockDimension(OwnerType.CUSTOMER, customerId, customerId, mother, null, "MAT-REAL", "RAW", StockStatus.RAW_WAITING);
        StockLedgerEntry inbound = new StockLedgerEntry(mother + "-0", BASE, "inbound_confirm", mother, null, raw,
                new BigDecimal("2"), new BigDecimal("2"), "");
        StockLedgerEntry move = new StockLedgerEntry(mother + "-1", BASE.plusSeconds(1), "stock_move", "MOVE-" + mother, raw, raw,
                BigDecimal.ONE, BigDecimal.ONE, "");
        first.add(inbound, raw, new BigDecimal("2"));
        second.add(inbound, raw, new BigDecimal("2"));
        first.add(move, raw, new BigDecimal("2"));
        second.add(move, raw, new BigDecimal("2"));
    }

    private static void seed(InMemoryBusinessDocumentRepository documents, FixtureStock stock) {
        BigDecimal ten = new BigDecimal("10");
        BigDecimal one = new BigDecimal("1");
        BigDecimal nine = new BigDecimal("9");
        for (int i = 0; i < MOTHERS; i++) {
            String mother = code("MD", i);
            String child = code("ZD", i);
            String orphan = code("OR", i);
            documents.saveMotherOrder(new MotherOrderRecord(
                    mother, 7001L, 7001L, "MAT-REAL", "2026-10-10", "", "", "", "收货员", "",
                    "货品" + i, "CUSTOMER", true, "RAW", ten, "件", ten, "RAW_WAITING",
                    ledgerCode(i, 0), "", null, "", "收货员", "", "", "已入库",
                    "2026-10-10T00:00:00", "无划痕", "2099-01-01", "真实客户"
            ));
            documents.saveMotherSplitState(new MotherSplitState(mother, MotherSplitState.KEEP, code("VF", i), "收货员", "2026-10-10T00:00:00"));
            documents.saveChildWorkOrder(new ChildWorkOrderRecord(
                    child, mother, 7001L, "真实客户", "无划痕", "2099-01-01", "MAT-REAL", "货品" + i,
                    ten, "件", ten, "RAW", "雕刻、镶嵌、抛光", code("VC", i), "", "收货员", "2026-10-10T00:00:00"
            ));
            documents.saveProcessStationHandoff(new ProcessStationHandoff(child, "雕刻", "岗位员", ledgerCode(i, 1), "2026-10-10T01:00:00"));
            documents.saveProcessStationHandoff(new ProcessStationHandoff(child, "镶嵌", "岗位员", "", "2026-10-10T02:00:00"));

            StockDimension raw = dimension(mother, null, "RAW", StockStatus.RAW_WAITING);
            StockDimension wip = dimension(mother, orphan, "WIP", StockStatus.WIP);
            StockDimension semi = dimension(mother, orphan, "SEMI", StockStatus.SEMI_FINISHED);
            stock.add(ledger(i, 0, mother, null, raw, ten), raw, i < 200 ? nine : ten);
            if (i < 200) {
                stock.add(ledger(i, 1, "SPLIT-" + mother, raw, wip, one), wip, i < 100 ? BigDecimal.ZERO : one);
                if (i < 100) {
                    stock.add(ledger(i, 2, "SEMI-" + mother, wip, semi, one), semi, one);
                    for (int step = 3; step < LEDGERS_PER_MOTHER; step++) {
                        stock.add(ledger(i, step, "MOVE-" + mother + "-" + step, raw, raw, one), raw, nine);
                    }
                } else {
                    for (int step = 2; step < LEDGERS_PER_MOTHER; step++) {
                        stock.add(ledger(i, step, "MOVE-" + mother + "-" + step, raw, raw, one), raw, nine);
                    }
                }
            } else {
                for (int step = 1; step < LEDGERS_PER_MOTHER; step++) {
                    stock.add(ledger(i, step, "MOVE-" + mother + "-" + step, raw, raw, one), raw, ten);
                }
            }
        }
    }

    private static StockLedgerEntry ledger(int mother, int step, String document, StockDimension from, StockDimension to, BigDecimal amount) {
        return new StockLedgerEntry(ledgerCode(mother, step), BASE.plusSeconds(mother * 20L + step),
                step == 0 ? "inbound_confirm" : step == 1 && mother < 200 ? "work_order_split" : "stock_move",
                document, from, to, amount, amount, step == 2 && mother < 100 ? "工序：抛光；操作人：张工" : "");
    }

    private static StockDimension dimension(String mother, String workOrder, String warehouse, StockStatus status) {
        return new StockDimension(OwnerType.CUSTOMER, 7001L, 7001L, mother, workOrder, "MAT-REAL", warehouse, status);
    }

    private static String ledgerCode(int mother, int step) {
        return "STK-" + String.format("%04d", mother) + "-" + step;
    }

    private static String code(String prefix, int value) {
        return prefix + "-" + String.format("%04d", value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T counting(Class<T> type, T delegate, Map<String, Integer> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(delegate, args);
            }
            calls.merge(method.getName(), 1, Integer::sum);
            try {
                return method.invoke(delegate, args == null ? new Object[0] : args);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw ex;
            }
        });
    }

    private record Reading(String name, long nanos, Map<String, Integer> documentCalls, Map<String, Integer> approvalCalls,
                           int ledgerReads, int balanceReads) {
        @Override
        public String toString() {
            return name + " ms=" + (nanos / 1_000_000)
                    + " ledgerReads=" + ledgerReads
                    + " balanceReads=" + balanceReads
                    + " documents=" + documentCalls
                    + " approvals=" + approvalCalls;
        }
    }

    private static final class FixtureStock implements StockLedgerRepository {
        private final List<StockLedgerEntry> ledgers = new ArrayList<>();
        private final Map<StockDimension, StockBalance> balances = new LinkedHashMap<>();
        private final boolean rangeFilter;
        private int ledgerReads;
        private int balanceReads;
        private int rangeReads;

        private FixtureStock() {
            this(false);
        }

        private FixtureStock(boolean rangeFilter) {
            this.rangeFilter = rangeFilter;
        }

        void add(StockLedgerEntry entry, StockDimension balanceDimension, BigDecimal quantity) {
            ledgers.add(entry);
            balances.put(balanceDimension, new StockBalance(balanceDimension, quantity, quantity));
        }

        void reset() {
            ledgerReads = 0;
            balanceReads = 0;
            rangeReads = 0;
        }

        int rangeReads() {
            return rangeReads;
        }

        int ledgerReads() {
            return ledgerReads;
        }

        int ledgerSize() {
            return ledgers.size();
        }

        @Override
        public StockLedgerEntry save(StockLedgerEntry entry) {
            throw new UnsupportedOperationException("基准只读取台账");
        }

        @Override
        public List<StockLedgerEntry> findAllLedgers() {
            ledgerReads++;
            return List.copyOf(ledgers);
        }

        @Override
        public List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes) {
            ledgerReads++;
            rangeReads++;
            Set<Long> ids = customerIds == null ? Set.of() : customerIds;
            Set<String> mothers = motherOrderCodes == null ? Set.of() : motherOrderCodes;
            if (ids.isEmpty() && mothers.isEmpty()) {
                return List.of();
            }
            if (!rangeFilter) {
                return List.copyOf(ledgers);
            }
            return ledgers.stream().filter(entry -> com.zhubao.erp.inventory.domain.CustomerLedgerFilter.touches(entry, ids, mothers)).toList();
        }

        @Override
        public List<StockBalance> findAllBalances() {
            balanceReads++;
            return List.copyOf(balances.values());
        }

        @Override
        public StockBalance findBalance(StockDimension dimension) {
            return balances.getOrDefault(dimension, new StockBalance(dimension, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        @Override
        public void clear() {
            ledgers.clear();
            balances.clear();
        }
    }
}
