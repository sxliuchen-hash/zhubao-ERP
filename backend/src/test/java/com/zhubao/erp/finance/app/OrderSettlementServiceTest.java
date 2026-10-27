package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.app.OrderDeliveryCompletionQuery;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OrderDeliveryAssessment;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessBatchHandoff;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.OrderSettlementItem;
import com.zhubao.erp.finance.domain.OrderSettlementRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.SettlementCommand;
import com.zhubao.erp.finance.domain.SettlementItemKind;
import com.zhubao.erp.finance.domain.SettlementStatus;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.InMemoryOrderSettlementRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderSettlementServiceTest {

    private InMemoryBusinessDocumentRepository documents;
    private InMemoryStockLedgerRepository stocks;
    private InMemoryFinanceRepository finance;
    private InMemoryOrderSettlementRepository settlements;
    private OrderDeliveryCompletionQuery deliveries;
    private OrderSettlementService service;
    private OperatorContext financeUser;

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        stocks = new InMemoryStockLedgerRepository();
        finance = new InMemoryFinanceRepository();
        settlements = new InMemoryOrderSettlementRepository();
        deliveries = new OrderDeliveryCompletionQuery(documents, stocks, null);
        service = service(finance, settlements);
        financeUser = new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL, false, "token",
                List.of(), List.of(), "finance", "", "");
        settlements.enablePolicy("2026-10-27T00:00:00", "迁移", null);
    }

    @Test
    void confirmPostsReceivablePayrollAndPayableOnce() {
        deliver("ZD-1", new BigDecimal("1.000"));
        SettlementCommand command = command("settle-0001", "ZD-1", fixedCustomer("80.00"), piece("PROCESS|ZD-1|雕刻", "carve", "1.000", "30.0000"));
        var confirmed = service.confirm(command, financeUser, rights());
        assertThat(confirmed.status()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(finance.listReceivables()).hasSize(1);
        assertThat(finance.listReceivables().get(0).sourceKey()).startsWith("SETTLEMENT|");
        assertThat(finance.listPayrollLines()).singleElement().extracting(PayrollLine::sourceMode).isEqualTo(PayrollLine.POST_DELIVERY);
        assertThat(finance.listPayables()).singleElement().extracting(Payable::payeeAccount).isEqualTo("carve");
        assertThat(finance.listPayables().get(0).amount()).isEqualByComparingTo("30.00");
        service.confirm(command, financeUser, rights());
        assertThat(finance.listReceivables()).hasSize(1);
        assertThat(finance.listPayrollLines()).hasSize(1);
        assertThat(finance.listPayables()).hasSize(1);
    }

    @Test
    void employeeFeeMayExceedCustomerFeeAndTwoWorkersShareOneProcess() {
        deliver("ZD-1", new BigDecimal("2.000"));
        SettlementCommand rich = command("settle-0002", "ZD-1", fixedCustomer("10.00"),
                piece("PROCESS|ZD-1|雕刻", "carve", "2.000", "80.0000"));
        var confirmed = service.confirm(rich, financeUser, rights());
        assertThat(confirmed.employeeNet()).isGreaterThan(confirmed.customerNet());

        documents = new InMemoryBusinessDocumentRepository();
        stocks = new InMemoryStockLedgerRepository();
        finance = new InMemoryFinanceRepository();
        settlements = new InMemoryOrderSettlementRepository();
        deliveries = new OrderDeliveryCompletionQuery(documents, stocks, null);
        service = service(finance, settlements);
        settlements.enablePolicy("2026-10-27T00:00:00", "迁移", null);
        deliver("ZD-2", new BigDecimal("2.000"));
        service.confirm(command("settle-0003", "ZD-2", fixedCustomer("100.00"),
                piece(2, "PROCESS|ZD-2|雕刻", "carve", "1.000", "10.0000"),
                piece(3, "PROCESS|ZD-2|雕刻", "polish", "1.000", "12.0000")), financeUser, rights());
        assertThat(finance.listPayrollLines()).hasSize(2);
        assertThat(finance.listPayables()).hasSize(2);
        assertThat(finance.listPayables()).allMatch(row -> row.postingKey() != null && !row.postingKey().isBlank());
    }

    @Test
    void rejectsEarlyPricingUnknownPayeeAndOverAllocation() {
        saveFamily("ZD-1", new BigDecimal("1.000"));
        assertThatThrownBy(() -> service.confirm(command("settle-0004", "ZD-1", fixedCustomer("10.00"),
                piece("PROCESS|ZD-1|雕刻", "carve", "1.000", "10.0000")), financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未交齐");
        deliver("ZD-1", new BigDecimal("1.000"));
        assertThatThrownBy(() -> service.confirm(command("settle-0005", "ZD-1", fixedCustomer("10.00"),
                piece("PROCESS|ZD-1|雕刻", "ghost", "1.000", "10.0000")), financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用账号");
        assertThatThrownBy(() -> service.confirm(command("settle-0006", "ZD-1", fixedCustomer("10.00"),
                piece("PROCESS|ZD-1|雕刻", "carve", "3.000", "10.0000")), financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过");
        assertThat(finance.listReceivables()).isEmpty();
    }

    @Test
    void gramWageUsesRecordedWeightAndDoesNotCompareWithPieceCount() {
        deliver("ZD-G", new BigDecimal("1.000"));
        saveBatch("B-G", "ZD-G", new BigDecimal("1.000"), new BigDecimal("10.000"));
        var confirmed = service.confirm(command("settle-gram-01", "ZD-G", fixedCustomer("20.00"),
                gram(2, "BATCH|B-G", "carve", "10.000", "2.0000", "")), financeUser, rights());
        assertThat(confirmed.employeeNet()).isEqualByComparingTo("20.00");
        assertThat(finance.listPayrollLines()).singleElement().extracting(PayrollLine::quantity).isEqualTo(new BigDecimal("10.000"));

        deliver("ZD-OVER", new BigDecimal("1.000"));
        saveBatch("B-OVER", "ZD-OVER", new BigDecimal("1.000"), new BigDecimal("10.000"));
        assertThatThrownBy(() -> service.confirm(command("settle-gram-02", "ZD-OVER", fixedCustomer("20.00"),
                gram(2, "BATCH|B-OVER", "carve", "6.000", "2.0000", ""),
                gram(3, "BATCH|B-OVER", "polish", "6.000", "2.0000", "")), financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计费重量");

        deliver("ZD-SHARE", new BigDecimal("1.000"));
        saveBatch("B-SHARE", "ZD-SHARE", new BigDecimal("1.000"), new BigDecimal("10.000"));
        service.confirm(command("settle-gram-03", "ZD-SHARE", fixedCustomer("20.00"),
                gram(2, "BATCH|B-SHARE", "carve", "4.000", "2.0000", ""),
                gram(3, "BATCH|B-SHARE", "polish", "6.000", "2.0000", "")), financeUser, rights());
        assertThat(finance.listPayrollLines().stream().filter(line -> "ZD-SHARE".equals(line.orderCode()))).hasSize(2);

        deliver("ZD-W", new BigDecimal("1.000"));
        saveBatch("B-W", "ZD-W", BigDecimal.ZERO.setScale(3), new BigDecimal("8.000"));
        assertThatThrownBy(() -> service.confirm(command("settle-gram-04", "ZD-W", fixedCustomer("20.00"),
                piece(2, "BATCH|B-W", "carve", "1.000", "10.0000"),
                piece(3, "GAP|ZD-W|雕刻", "polish", "1.000", "10.0000")), financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("补重量不能按件计酬");
        service.confirm(command("settle-gram-05", "ZD-W", fixedCustomer("20.00"),
                gram(2, "BATCH|B-W", "carve", "8.000", "1.0000", ""),
                piece(3, "GAP|ZD-W|雕刻", "polish", "1.000", "10.0000")), financeUser, rights());

        deliver("ZD-E", new BigDecimal("1.000"));
        assertThatThrownBy(() -> service.confirm(command("settle-gram-06", "ZD-E", fixedCustomer("20.00"),
                gram(2, "PROCESS|ZD-E|雕刻", "carve", "10.000", "1.0000", "")), financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计费重量依据");
        assertThatThrownBy(() -> service.confirm(command("settle-gram-07", "ZD-E", fixedCustomer("20.00"),
                gram(2, "PROCESS|ZD-E|雕刻", "carve", "6.000", "1.0000", "台秤确认"),
                gram(3, "PROCESS|ZD-E|雕刻", "polish", "4.000", "1.0000", "台秤确认")), financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能把同一来源按克分给多人");
        service.confirm(command("settle-gram-08", "ZD-E", fixedCustomer("20.00"),
                gram(2, "PROCESS|ZD-E|雕刻", "carve", "10.000", "1.5000", "台秤确认十克")), financeUser, rights());

        deliver("ZD-U", new BigDecimal("1.000"));
        saveBatch("B-U", "ZD-U", new BigDecimal("1.000"), new BigDecimal("10.000"));
        assertThatThrownBy(() -> service.confirm(command("settle-gram-09", "ZD-U", fixedCustomer("20.00"),
                new OrderSettlementItem(2, SettlementItemKind.EMPLOYEE, "雕刻", "BATCH|B-U", "GRAM",
                        new BigDecimal("10.000"), "件", "GRAM", new BigDecimal("1.0000"), null, BigDecimal.ZERO, null,
                        "carve", "", "", "")), financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量单位");
        assertThat(finance.listReceivables().stream().noneMatch(line -> "ZD-OVER".equals(line.workOrderCode())
                || "ZD-U".equals(line.workOrderCode()))).isTrue();
    }

    @Test
    void payableFailureRollsBackReceivableAndDraft() {
        deliver("ZD-1", new BigDecimal("1.000"));
        InMemoryFinanceRepository failing = new InMemoryFinanceRepository() {
            @Override
            public void savePayable(Payable payable, WorkSession session) {
                throw new IllegalStateException("应付失败");
            }
        };
        service = service(failing, settlements);
        assertThatThrownBy(() -> service.confirm(command("settle-0007", "ZD-1", fixedCustomer("10.00"),
                piece("PROCESS|ZD-1|雕刻", "carve", "1.000", "10.0000")), financeUser, rights()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(failing.listReceivables()).isEmpty();
        assertThat(failing.listPayrollLines()).isEmpty();
        assertThat(settlements.count("")).isZero();
    }

    @Test
    void explicitWaiverPostsNoMoneyAndDisabledPolicyRejects() {
        deliver("ZD-1", new BigDecimal("1.000"));
        SettlementCommand waived = new SettlementCommand("settle-0008", OrderDeliveryAssessment.CHILD, "ZD-1", 0,
                deliveries.assess(OrderDeliveryAssessment.CHILD, "ZD-1").deliveryRevision(),
                true, "客户免收", true, "无内部计件", List.of());
        var confirmed = service.confirm(waived, financeUser, rights());
        assertThat(confirmed.customerWaived()).isTrue();
        assertThat(finance.listReceivables()).isEmpty();
        assertThat(finance.listPayables()).isEmpty();
        InMemoryOrderSettlementRepository closed = new InMemoryOrderSettlementRepository(false);
        OrderSettlementService disabled = service(finance, closed);
        assertThatThrownBy(() -> disabled.confirm(waived, financeUser, rights()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未启用");
        assertThatThrownBy(() -> service.confirm(waived, financeUser, Set.of(PermissionCode.FINANCE_BILLING_POST.name())))
                .isInstanceOf(PermissionDeniedException.class);
    }

    private OrderSettlementService service(InMemoryFinanceRepository ledger, OrderSettlementRepository settlementRepository) {
        return new OrderSettlementService(deliveries, settlementRepository, ledger, documents,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), accounts(), new FinanceReadAuthorizer());
    }

    private void deliver(String child, BigDecimal quantity) {
        if (documents.findChildWorkOrderByCode(child).isEmpty()) {
            saveFamily(child, quantity);
        }
        documents.saveProcessStationHandoff(new ProcessStationHandoff(child, "雕刻", "交工人", "HL", "2026-10-26"));
        documents.saveQualityRequirementCheck(new QualityRequirementCheck("QC-" + child, child, "MD-1", "质检员",
                QualityVerdicts.PASS, false, "QL", "", "2026-10-26", List.of()));
        documents.saveOutboundOrder(new OutboundOrderRecord("OUT-" + child, "MD-1", child, 101L, 101L, "MAT", "FG", "CUS",
                "2026-10-27", "成品", quantity, "件", quantity, "", "", "", "", "", null, "", "发货员", "", "",
                "SHIPPED", "L-" + child, "2026-10-27"));
        stocks.save(new StockLedgerEntry("L-" + child, Instant.parse("2026-10-27T00:00:00Z"), "outbound_shipment", "OUT-" + child,
                null, new StockDimension(OwnerType.CUSTOMER, 101L, 101L, "MD-1", child, "MAT", "FG", StockStatus.SHIPPED),
                quantity, quantity, "发货"));
    }

    private void saveFamily(String child, BigDecimal quantity) {
        if (documents.findMotherOrderByCode("MD-1").isEmpty()) {
            documents.saveMotherOrder(new MotherOrderRecord("MD-1", 101L, 101L, "MAT", "2026-10-10", "", "SF-MD-1", "",
                    "收货员", "拆包员", "戒托", "CUSTOMER", true, "RAW", quantity, "件", quantity, "RAW_WAITING", "", "",
                    null, "", "开单员", "", "", "INBOUND_CONFIRMED", "2026-10-10", "内壁刻字", "2026-10-20", "真实客户"));
            documents.saveMotherSplitState(new MotherSplitState("MD-1", MotherSplitState.SPLIT, "", "开单员", "2026-10-27"));
        }
        documents.saveChildWorkOrder(new ChildWorkOrderRecord(child, "MD-1", 101L, "真实客户", "内壁刻字", "2026-10-20",
                "MAT", "戒托", quantity, "件", quantity, "RAW", "雕刻", "verify-" + child, "ledger", "开单员", "2026-10-10"));
    }

    private SettlementCommand command(String requestId, String child, OrderSettlementItem... items) {
        String revision = deliveries.assess(OrderDeliveryAssessment.CHILD, child).deliveryRevision();
        return new SettlementCommand(requestId, OrderDeliveryAssessment.CHILD, child, 0, revision, false, "", false, "", List.of(items));
    }

    private static OrderSettlementItem fixedCustomer(String amount) {
        return new OrderSettlementItem(1, SettlementItemKind.CUSTOMER, "加工费", "", "FIXED", null, "件", "",
                null, new BigDecimal(amount), BigDecimal.ZERO, null, "", "", "", "");
    }

    private static OrderSettlementItem piece(String source, String payee, String quantity, String price) {
        return piece(2, source, payee, quantity, price);
    }

    private void saveBatch(String code, String child, BigDecimal quantity, BigDecimal weight) {
        new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()).execute(
                new CommandCall("batch-" + code, "tester", "PROCESS_BATCH", "WORK_ORDER", child, code),
                session -> {
                    documents.saveProcessBatch(new ProcessBatchHandoff(
                            code, child, "雕刻", quantity, weight, "交工人", "2026-10-26T01:00:00"
                    ), session);
                    return "ok";
                }
        );
    }

    private static OrderSettlementItem gram(int lineNo, String source, String payee, String quantity, String price, String basis) {
        return new OrderSettlementItem(lineNo, SettlementItemKind.EMPLOYEE, "雕刻", source, "GRAM",
                new BigDecimal(quantity), "克", "GRAM", new BigDecimal(price), null, BigDecimal.ZERO, null,
                payee, "", basis, "");
    }

    private static OrderSettlementItem piece(int lineNo, String source, String payee, String quantity, String price) {
        return new OrderSettlementItem(lineNo, SettlementItemKind.EMPLOYEE, "雕刻", source, "PIECE",
                new BigDecimal(quantity), "件", "PIECE", new BigDecimal(price), null, BigDecimal.ZERO, null,
                payee, "", "", "");
    }

    private static Set<String> rights() {
        return Set.of(PermissionCode.FINANCE_BILLING_POST.name(), PermissionCode.FINANCE_PAYROLL.name());
    }

    private static AuthUserAccountRepository accounts() {
        AuthUserAccount carve = new AuthUserAccount("carve", "hash", "雕刻工", RoleCode.OPERATOR, DataScope.SELF, true, false);
        AuthUserAccount polish = new AuthUserAccount("polish", "hash", "抛光工", RoleCode.OPERATOR, DataScope.SELF, true, false);
        return new AuthUserAccountRepository() {
            @Override public AuthUserAccount save(AuthUserAccount account) { throw new UnsupportedOperationException(); }
            @Override public List<AuthUserAccount> findAll() { return List.of(carve, polish); }
            @Override public Optional<AuthUserAccount> findByUsername(String username) {
                if ("carve".equals(username)) return Optional.of(carve);
                if ("polish".equals(username)) return Optional.of(polish);
                return Optional.empty();
            }
            @Override public List<String> findActiveUsernames() { return List.of("carve", "polish"); }
            @Override public void updateEnabled(String username, boolean enabled) {}
            @Override public void unlock(String username) {}
            @Override public void recordLoginFailure(String username, int failedLoginCount, Instant lockedUntil) {}
            @Override public void recordLoginSuccess(String username) {}
            @Override public boolean consumeTemporaryPassword(String username) { return false; }
        };
    }
}
