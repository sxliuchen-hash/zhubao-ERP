package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.business.BusinessWorkFixture;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.work.app.*;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.infra.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/** 退货、售后与损耗申请不能在原请求结果保存失败后留下半笔业务。 */
class AftercareRequestContractTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void 退货与售后按原请求回放且改变内容拒绝(boolean jdbc) throws Exception {
        var world = world(jdbc, CommandResultProbe.NONE);
        var returned = world.service().returnCustomerShipment(returning("return-001", "客户退回"));
        assertThat(world.service().returnCustomerShipment(returning("return-001", "客户退回"))).isEqualTo(returned);
        assertThatThrownBy(() -> world.service().returnCustomerShipment(returning("return-new-request", "客户退回")))
                .hasMessageContaining("业务单号已入账");
        assertThatThrownBy(() -> world.service().returnCustomerShipment(returning("return-001", "改变原因")))
                .hasMessageContaining("内容");
        var reviewed = world.service().reviewAfterSalesReturn(review("review-001"));
        assertThat(world.service().reviewAfterSalesReturn(review("review-001"))).isEqualTo(reviewed);
        assertThatThrownBy(() -> world.service().reviewAfterSalesReturn(review("review-new-request")))
                .hasMessageContaining("业务单号已入账");
        assertThat(world.stocks().ledgers()).hasSize(3);
        assertThat(world.stocks().balances()).filteredOn(row -> row.dimension().stockStatus() == StockStatus.REWORK)
                .singleElement().satisfies(row -> assertThat(row.quantity()).isEqualByComparingTo("2"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void 退货请求结果失败不动库存且原请求可重试(boolean jdbc) throws Exception {
        var failing = new java.util.concurrent.atomic.AtomicBoolean(true);
        var world = world(jdbc, () -> { if (failing.get()) throw new IllegalStateException("结果保存失败"); });
        assertThatThrownBy(() -> world.service().returnCustomerShipment(returning("return-rollback", "客户退回")))
                .hasMessage("结果保存失败");
        assertThat(world.stocks().ledgers()).hasSize(1);
        failing.set(false);
        world.service().returnCustomerShipment(returning("return-rollback", "客户退回"));
        assertThat(world.stocks().ledgers()).hasSize(2);
        failing.set(true);
        assertThatThrownBy(() -> world.service().reviewAfterSalesReturn(review("review-rollback"))).hasMessage("结果保存失败");
        assertThat(world.stocks().ledgers()).hasSize(2);
        failing.set(false);
        world.service().reviewAfterSalesReturn(review("review-rollback"));
        assertThat(world.stocks().ledgers()).hasSize(3);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void 缺少稳定请求拒绝退售后且损耗申请失败撤销审批任务(boolean jdbc) throws Exception {
        var failing = new java.util.concurrent.atomic.AtomicBoolean(false);
        var world = world(jdbc, () -> { if (failing.get()) throw new IllegalStateException("结果保存失败"); });
        assertThatThrownBy(() -> world.service().returnCustomerShipment(returning("", "客户退回"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> world.service().reviewAfterSalesReturn(review(""))).isInstanceOf(RuntimeException.class);
        assertThat(world.stocks().ledgers()).hasSize(1);
        failing.set(true);
        assertThatThrownBy(() -> world.service().approveLoss(loss("loss-rollback"))).hasMessage("结果保存失败");
        assertThat(world.approvals().findAll()).isEmpty();
        failing.set(false);
        var result = world.service().approveLoss(loss("loss-rollback"));
        assertThat(world.service().approveLoss(loss("loss-rollback"))).isEqualTo(result);
        assertThat(world.approvals().findAll()).hasSize(1);
        assertThatThrownBy(() -> world.service().approveLoss(loss("loss-another-request"))).hasMessageContaining("申请单号已存在");
        assertThat(world.stocks().ledgers()).hasSize(1);
    }

    private World world(boolean jdbc, CommandResultProbe probe) throws Exception {
        var dataSource = jdbc ? StockRepositoryFixture.jdbcDataSource() : null;
        BusinessWorkFixture.unit(dataSource);
        BusinessWorkUnit unit = jdbc ? new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), probe)
                : new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), probe);
        var stocks = new StockLedgerService(jdbc ? new JdbcStockLedgerRepository(dataSource) : new InMemoryStockLedgerRepository());
        stocks.transfer(new StockTransferCommand("test_seed", "OUT-1", null,
                new StockDimension(OwnerType.CUSTOMER, 1L, 1L, "MD-1", "WO-1", "MAT", "SHIPPED", StockStatus.SHIPPED),
                new BigDecimal("10"), new BigDecimal("20"), "显式发货事实"));
        if (jdbc) BusinessWorkFixture.migrate(dataSource, "db/mysql/V11__m3_lightweight_approval_contract.sql");
        com.zhubao.erp.approval.domain.ApprovalTaskRepository approvals = jdbc
                ? new com.zhubao.erp.approval.infra.JdbcApprovalTaskRepository(dataSource) : new InMemoryApprovalTaskRepository();
        return new World(new BusinessOperationService(stocks, new ApprovalService(approvals), BusinessWorkFixture.documents("MD-1", 1L), unit), stocks, approvals);
    }
    private CustomerReturnCommand returning(String request, String reason) {
        return new CustomerReturnCommand("RET-1", "OUT-1", "MD-1", "WO-1", 1L, 1L, "MAT", "SHIPPED", "RETURN",
                new BigDecimal("2"), new BigDecimal("4"), "YT-1", reason, "库管", "", "", request);
    }
    private AfterSalesReviewCommand review(String request) {
        return new AfterSalesReviewCommand("REV-1", "RET-1", "OUT-1", "MD-1", "WO-1", 1L, 1L, "MAT", "RETURN", "REWORK",
                new BigDecimal("2"), new BigDecimal("4"), AfterSalesReviewDisposition.REWORK, "质检员", "返工", "", "", request);
    }
    private ApproveLossCommand loss(String request) {
        return new ApproveLossCommand("LOSS-1", "MD-1", "WO-1", 1L, 1L, "MAT", "抛光", "工人", "组长", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, "", "", request);
    }
    private record World(BusinessOperationService service, StockLedgerService stocks, com.zhubao.erp.approval.domain.ApprovalTaskRepository approvals) { }
}
