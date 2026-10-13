package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrepStockServiceTest {

    private InMemoryStockLedgerRepository repository;
    private StockLedgerService ledger;
    private InMemoryBusinessDocumentRepository documents;
    private PrepStockService prep;
    private AuthUserAccount keeper;

    @BeforeEach
    void setUp() {
        repository = new InMemoryStockLedgerRepository();
        ledger = new StockLedgerService(repository);
        documents = new InMemoryBusinessDocumentRepository();
        BusinessOperationService operations = new BusinessOperationService(ledger, null, documents);
        operations.confirmInbound(new ConfirmInboundCommand(
                "MD-PREP-001", 1001L, 1001L, "AU9999", "2026-10-08", "顺丰", "SF-PREP-001", "",
                "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                new BigDecimal("10"), "件", new BigDecimal("20"), "何琳", "", "原母单",
                "内壁刻字", "2026-10-20", "恒信珠宝"
        ));
        keeper = account("库管员", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL, List.of());
        prep = new PrepStockService(ledger, documents, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
    }

    @Test
    void issueReducesBatchWithoutRewritingTheOriginalMotherReceipt() {
        prep.receiveCustomerPrep(1001L, "AU9999", "PREP-WH", new BigDecimal("5"), new BigDecimal("8"),
                "PREP-001", "prep-recv-0001", keeper);
        prep.issueToMother(1001L, "AU9999", "PREP-WH", "PREP-001", "MD-PREP-001",
                new BigDecimal("2"), new BigDecimal("3"), "prep-issue-0001", keeper);
        prep.issueToMother(1001L, "AU9999", "PREP-WH", "PREP-001", "MD-PREP-001",
                new BigDecimal("2"), new BigDecimal("3"), "prep-issue-0001", keeper);

        assertThat(quantity(OwnerType.CUSTOMER, "PREP-001", "")).isEqualByComparingTo("3");
        assertThat(quantity(OwnerType.CUSTOMER, "", "MD-PREP-001")).isEqualByComparingTo("12");
        assertThat(ledger.ledgers()).filteredOn(entry -> "inbound_confirm".equals(entry.documentType())).hasSize(1);
        assertThat(ledger.ledgers()).filteredOn(entry -> "prep_issue".equals(entry.documentType())).hasSize(1);
        assertThatThrownBy(() -> prep.issueToMother(1002L, "AU9999", "PREP-WH", "PREP-001", "MD-PREP-001",
                new BigDecimal("1"), new BigDecimal("1"), "prep-cross-0001", keeper))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("其他客户");
        assertThat(quantity(OwnerType.CUSTOMER, "PREP-001", "")).isEqualByComparingTo("3");
    }

    @Test
    void failedReceiptRollsBackAndCompanyStockStaysSeparate() {
        MemoryBusinessWorkUnit failing = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
            throw new BusinessException("模拟备料失败");
        });
        PrepStockService failingPrep = new PrepStockService(ledger, documents, failing);
        assertThatThrownBy(() -> failingPrep.receiveCustomerPrep(1001L, "AU9999", "PREP-WH",
                new BigDecimal("5"), new BigDecimal("8"), "PREP-FAIL", "prep-fail-0001", keeper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模拟备料失败");
        assertThat(prep.customerPrep(keeper)).noneMatch(balance -> "PREP-FAIL".equals(balance.dimension().batchCode()));

        prep.receiveCompany("AU9999", "COMPANY-WH", new BigDecimal("1"), new BigDecimal("2"),
                "CO-001", "company-recv-01", keeper);
        assertThat(prep.companyStock(keeper)).singleElement()
                .satisfies(balance -> assertThat(balance.dimension().ownerType()).isEqualTo(OwnerType.COMPANY));
        assertThat(prep.customerPrep(keeper)).noneMatch(balance -> balance.dimension().ownerType() == OwnerType.COMPANY);
        AuthUserAccount customerUser = account("客户员", RoleCode.OPERATOR, DataScope.CUSTOMER_RANGE, List.of("1001"));
        assertThatThrownBy(() -> prep.companyStock(customerUser)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void concurrentIssuesCannotOverdrawTheBatch() throws Exception {
        prep.receiveCustomerPrep(1001L, "AU9999", "PREP-WH", new BigDecimal("4"), new BigDecimal("4"),
                "PREP-RACE", "prep-race-recv", keeper);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = pool.submit(() -> issueRace("prep-race-a001", start));
            Future<String> second = pool.submit(() -> issueRace("prep-race-b001", start));
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
            assertThat(quantity(OwnerType.CUSTOMER, "PREP-RACE", "")).isEqualByComparingTo("1");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void returnRestoresCustomerBatchAndCompanyMovementKeepsOwnership() {
        prep.receiveCustomerPrep(1001L, "AU9999", "PREP-WH", new BigDecimal("5"), new BigDecimal("8"),
                "PREP-RET", "prep-ret-recv", keeper);
        prep.issueToMother(1001L, "AU9999", "PREP-WH", "PREP-RET", "MD-PREP-001",
                new BigDecimal("2"), new BigDecimal("3"), "prep-ret-issue", keeper);
        prep.returnToBatch(1001L, "AU9999", "PREP-WH", "PREP-RET", "MD-PREP-001",
                new BigDecimal("1"), new BigDecimal("1"), "prep-ret-back", keeper);
        assertThat(quantity(OwnerType.CUSTOMER, "PREP-RET", "")).isEqualByComparingTo("4");
        assertThat(ledger.ledgers()).filteredOn(entry -> "prep_return".equals(entry.documentType())).hasSize(1);

        prep.receiveCompany("AU9999", "COMP-WH", new BigDecimal("6"), new BigDecimal("6"), "COMP-1", "comp-recv-0001", keeper);
        prep.issueCompany("AU9999", "COMP-WH", new BigDecimal("2"), new BigDecimal("2"), "COMP-1", "comp-issue-0001", keeper);
        prep.returnCompany("AU9999", "COMP-WH", new BigDecimal("1"), new BigDecimal("1"), "COMP-1", "comp-back-0001", keeper);
        assertThat(ledger.balances().stream()
                .filter(balance -> balance.dimension().ownerType() == OwnerType.COMPANY)
                .filter(balance -> "COMP-1".equals(balance.dimension().batchCode()))
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.RAW_WAITING)
                .map(balance -> balance.quantity())
                .findFirst().orElseThrow()).isEqualByComparingTo("5");
        assertThat(ledger.balances().stream().filter(balance -> balance.dimension().ownerType() == OwnerType.COMPANY)
                .allMatch(balance -> balance.dimension().customerId() == null)).isTrue();
    }

    private String issueRace(String requestId, CountDownLatch start) throws InterruptedException {
        start.await();
        prep.issueToMother(1001L, "AU9999", "PREP-WH", "PREP-RACE", "MD-PREP-001",
                new BigDecimal("3"), new BigDecimal("3"), requestId, keeper);
        return "ok";
    }

    private BigDecimal quantity(OwnerType owner, String batch, String mother) {
        return ledger.balances().stream()
                .filter(balance -> balance.dimension().ownerType() == owner)
                .filter(balance -> batch.equals(balance.dimension().batchCode()))
                .filter(balance -> mother.equals(balance.dimension().motherOrderCode()))
                .filter(balance -> balance.dimension().stockStatus() == StockStatus.RAW_WAITING)
                .map(balance -> balance.quantity())
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private AuthUserAccount account(String operator, RoleCode role, DataScope scope, List<String> customers) {
        return new AuthUserAccount(
                "user", "hash", operator, role, scope, true, true,
                Instant.now(), 0, null, customers, false, false, List.of()
        );
    }
}
