package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.OutboundShipmentResult;
import com.zhubao.erp.business.domain.ShipFinishedStockCommand;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.JdbcBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandResultProbe;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 确认发货的回放与同一次提交。
 * 只覆盖带请求编号的成品出库，以及未带编号时仍各自提交的旧路径。
 * 质检和工序测试不能说明这里已经安全。
 */
class ShippingDeliveryTest {

    private static final String ACTOR = "库管员 何琳";

    @Test
    void memoryShipmentSharesOneCommit() {
        assertShipment(new MemoryWorld(CommandResultProbe.NONE, OutboundSaveProbe.NONE));
    }

    @Test
    void jdbcShipmentSharesOneCommit() throws Exception {
        JdbcWorld world = new JdbcWorld(CommandResultProbe.NONE, OutboundSaveProbe.NONE);
        assertShipment(world);
        world.close();
    }

    private void assertShipment(World world) {
        seed(world, "MD-SHIP-01", "ZD-SHIP-01", 1001L);
        OutboundShipmentResult created = ship(world, "req-ship-0001", "OUT-SHIP-01", "MD-SHIP-01", "ZD-SHIP-01", 1001L, "10", "20", "YT10000001");
        OutboundShipmentResult replay = ship(world, "req-ship-0001", "OUT-SHIP-01", "MD-SHIP-01", "ZD-SHIP-01", 1001L, "10", "20.000", "  YT10000001  ");
        assertThat(replay.ledgerCode()).isEqualTo(created.ledgerCode());
        assertThat(replay.shipmentCode()).isEqualTo("OUT-SHIP-01");
        assertThat(replay.logisticsNo()).isEqualTo("YT10000001");
        assertThat(world.finished("MD-SHIP-01", "ZD-SHIP-01", 1001L)).isEqualByComparingTo("0");
        assertThat(world.shipped("MD-SHIP-01", "ZD-SHIP-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.shipmentLedgers("MD-SHIP-01")).isEqualTo(1);
        OutboundOrderRecord outbound = world.outbound("OUT-SHIP-01");
        assertThat(outbound.quantity()).isEqualByComparingTo("10");
        assertThat(outbound.weight()).isEqualByComparingTo("20");
        assertThat(outbound.ledgerCode()).isEqualTo(created.ledgerCode());
        assertThat(outbound.status()).isEqualTo("SHIPPED");
        assertThat(outbound.logisticsNo()).isEqualTo("YT10000001");

        assertThatThrownBy(() -> ship(world, "req-ship-0001", "OUT-SHIP-01", "MD-SHIP-01", "ZD-SHIP-01", 1001L, "4", "8", "YT10000001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> ship(world, "req-ship-0001", "OUT-SHIP-01", "MD-SHIP-01", "ZD-SHIP-01", 1001L, "10", "20", "YT10000002"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> ship(world, "req-ship-0001", "OUT-SHIP-OTHER", "MD-SHIP-01", "ZD-SHIP-01", 1001L, "10", "20", "YT10000001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> ship(world, "req-ship-0001", "OUT-SHIP-01", "MD-SHIP-01", "ZD-SHIP-01", 1002L, "10", "20", "YT10000001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> world.operations().shipFinishedStock(
                order("OUT-SHIP-01", "MD-SHIP-01", "ZD-SHIP-01", 1001L, "10", "20", "YT10000001", "req-ship-0001"),
                "另一账号"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("该请求不能跨账号重放");
        assertThat(world.shipmentLedgers("MD-SHIP-01")).isEqualTo(1);
        assertThat(world.outboundCount()).isEqualTo(1);
        assertThat(world.hasOutbound("OUT-SHIP-OTHER")).isFalse();

        assertThatThrownBy(() -> ship(world, "req-ship-again", "OUT-SHIP-01", "MD-SHIP-01", "ZD-SHIP-01", 1001L, "10", "20", "YT10000009"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复确认出库");
        assertThat(world.shipmentLedgers("MD-SHIP-01")).isEqualTo(1);
        assertThat(world.finished("MD-SHIP-01", "ZD-SHIP-01", 1001L)).isEqualByComparingTo("0");
        assertThat(world.hasRequest("req-ship-again")).isFalse();

        seed(world, "MD-SHIP-PART", "ZD-SHIP-PART", 1001L);
        OutboundShipmentResult partial = ship(world, "req-ship-part1", "OUT-SHIP-PART-1", "MD-SHIP-PART", "ZD-SHIP-PART", 1001L, "4", "8", "YT20000001");
        assertThat(world.finished("MD-SHIP-PART", "ZD-SHIP-PART", 1001L)).isEqualByComparingTo("6");
        assertThat(world.finishedWeight("MD-SHIP-PART", "ZD-SHIP-PART", 1001L)).isEqualByComparingTo("12");
        assertThat(world.shipped("MD-SHIP-PART", "ZD-SHIP-PART", 1001L)).isEqualByComparingTo("4");
        assertThat(world.shippedWeight("MD-SHIP-PART", "ZD-SHIP-PART", 1001L)).isEqualByComparingTo("8");
        assertThat(world.outbound("OUT-SHIP-PART-1").quantity()).isEqualByComparingTo("4");
        assertThat(world.outbound("OUT-SHIP-PART-1").weight()).isEqualByComparingTo("8");
        assertThat(world.outbound("OUT-SHIP-PART-1").ledgerCode()).isEqualTo(partial.ledgerCode());
        assertThat(world.shipmentLedgers("MD-SHIP-PART")).isEqualTo(1);
        ship(world, "req-ship-part2", "OUT-SHIP-PART-2", "MD-SHIP-PART", "ZD-SHIP-PART", 1001L, "6", "12", "YT20000002");
        assertThat(world.finished("MD-SHIP-PART", "ZD-SHIP-PART", 1001L)).isEqualByComparingTo("0");
        assertThat(world.shipped("MD-SHIP-PART", "ZD-SHIP-PART", 1001L)).isEqualByComparingTo("10");
        assertThat(world.shipmentLedgers("MD-SHIP-PART")).isEqualTo(2);
        assertThat(world.hasOutbound("OUT-SHIP-PART-2")).isTrue();

        seed(world, "MD-SHIP-SHORT", "ZD-SHIP-SHORT", 1001L);
        assertThatThrownBy(() -> ship(world, "req-ship-short", "OUT-SHIP-SHORT", "MD-SHIP-SHORT", "ZD-SHIP-SHORT", 1001L, "11", "20", "YT30000001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不足");
        assertThat(world.finished("MD-SHIP-SHORT", "ZD-SHIP-SHORT", 1001L)).isEqualByComparingTo("10");
        assertThat(world.shipped("MD-SHIP-SHORT", "ZD-SHIP-SHORT", 1001L)).isEqualByComparingTo("0");
        assertThat(world.hasOutbound("OUT-SHIP-SHORT")).isFalse();
        assertThat(world.shipmentLedgers("MD-SHIP-SHORT")).isZero();
        assertThat(world.hasRequest("req-ship-short")).isFalse();

        seed(world, "MD-SHIP-FACT", "ZD-SHIP-FACT", 1001L);
        World failingOrder = world.withProbes(CommandResultProbe.NONE, () -> {
            throw new IllegalStateException("出库单保存失败");
        });
        assertThatThrownBy(() -> ship(failingOrder, "req-ship-fact", "OUT-SHIP-FACT", "MD-SHIP-FACT", "ZD-SHIP-FACT", 1001L, "10", "20", "YT40000001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("出库单保存失败");
        assertThat(world.finished("MD-SHIP-FACT", "ZD-SHIP-FACT", 1001L)).isEqualByComparingTo("10");
        assertThat(world.shipped("MD-SHIP-FACT", "ZD-SHIP-FACT", 1001L)).isEqualByComparingTo("0");
        assertThat(world.hasOutbound("OUT-SHIP-FACT")).isFalse();
        assertThat(world.shipmentLedgers("MD-SHIP-FACT")).isZero();
        assertThat(world.hasRequest("req-ship-fact")).isFalse();

        seed(world, "MD-SHIP-RESULT", "ZD-SHIP-RESULT", 1001L);
        World failingResult = world.withProbes(() -> {
            throw new IllegalStateException("请求结果保存失败");
        }, OutboundSaveProbe.NONE);
        assertThatThrownBy(() -> ship(failingResult, "req-ship-result", "OUT-SHIP-RESULT", "MD-SHIP-RESULT", "ZD-SHIP-RESULT", 1001L, "10", "20", "YT50000001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("请求结果保存失败");
        assertThat(world.finished("MD-SHIP-RESULT", "ZD-SHIP-RESULT", 1001L)).isEqualByComparingTo("10");
        assertThat(world.hasOutbound("OUT-SHIP-RESULT")).isFalse();
        assertThat(world.shipmentLedgers("MD-SHIP-RESULT")).isZero();
        assertThat(world.hasRequest("req-ship-result")).isFalse();

        seed(world, "MD-SHIP-OLD", "ZD-SHIP-OLD", 1001L);
        World oldPath = world.withProbes(CommandResultProbe.NONE, () -> {
            throw new IllegalStateException("出库单保存失败");
        });
        assertThatThrownBy(() -> oldPath.operations().shipFinishedStock(
                order("OUT-SHIP-OLD", "MD-SHIP-OLD", "ZD-SHIP-OLD", 1001L, "10", "20", "YT60000001", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("出库单保存失败");
        assertThat(world.finished("MD-SHIP-OLD", "ZD-SHIP-OLD", 1001L)).isEqualByComparingTo("0");
        assertThat(world.shipped("MD-SHIP-OLD", "ZD-SHIP-OLD", 1001L)).isEqualByComparingTo("10");
        assertThat(world.hasOutbound("OUT-SHIP-OLD")).isFalse();
        assertThat(world.shipmentLedgers("MD-SHIP-OLD")).isEqualTo(1);
        assertThat(world.hasRequest("req-ship-old")).isFalse();

        seed(world, "MD-SHIP-RACE", "ZD-SHIP-RACE", 1001L);
        raceSame(world, "MD-SHIP-RACE", "ZD-SHIP-RACE", "OUT-SHIP-RACE", "req-ship-race");
        seed(world, "MD-SHIP-RACE2", "ZD-SHIP-RACE2", 1001L);
        raceDifferent(world, "MD-SHIP-RACE2", "ZD-SHIP-RACE2", "OUT-SHIP-RACE2");
    }

    private void raceSame(World world, String mother, String work, String shipment, String requestId) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<OutboundShipmentResult> left = pool.submit(() -> {
                start.await();
                return ship(world, requestId, shipment, mother, work, 1001L, "10", "20", "YT70000001");
            });
            Future<OutboundShipmentResult> right = pool.submit(() -> {
                start.await();
                return ship(world, requestId, shipment, mother, work, 1001L, "10", "20", " YT70000001 ");
            });
            start.countDown();
            assertThat(left.get().ledgerCode()).isEqualTo(right.get().ledgerCode());
            assertThat(world.shipmentLedgers(mother)).isEqualTo(1);
            assertThat(world.finished(mother, work, 1001L)).isEqualByComparingTo("0");
            assertThat(world.shipped(mother, work, 1001L)).isEqualByComparingTo("10");
            assertThat(world.hasOutbound(shipment)).isTrue();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    private void raceDifferent(World world, String mother, String work, String shipment) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<OutboundShipmentResult> left = pool.submit(() -> {
                start.await();
                return ship(world, "req-ship-left", shipment, mother, work, 1001L, "10", "20", "YT80000001");
            });
            Future<OutboundShipmentResult> right = pool.submit(() -> {
                start.await();
                return ship(world, "req-ship-right", shipment, mother, work, 1001L, "10", "20", "YT80000002");
            });
            start.countDown();
            int success = outcome(left) + outcome(right);
            assertThat(success).isEqualTo(1);
            assertThat(world.shipmentLedgers(mother)).isEqualTo(1);
            assertThat(world.finished(mother, work, 1001L)).isEqualByComparingTo("0");
            assertThat(world.shipped(mother, work, 1001L)).isEqualByComparingTo("10");
            assertThat(world.hasOutbound(shipment)).isTrue();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    private int outcome(Future<OutboundShipmentResult> future) throws Exception {
        try {
            future.get();
            return 1;
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof BusinessException) {
                return 0;
            }
            throw ex;
        }
    }

    private void seed(World world, String mother, String work, long customerId) {
        world.stocks().transfer(new StockTransferCommand(
                "quality_release",
                "QC-" + mother,
                null,
                dimension(mother, work, customerId, "FINISHED-CUSTOMER", StockStatus.FINISHED),
                new BigDecimal("10"),
                new BigDecimal("20"),
                "预置成品"
        ));
    }

    private OutboundShipmentResult ship(
            World world,
            String requestId,
            String shipment,
            String mother,
            String work,
            long customerId,
            String quantity,
            String weight,
            String logisticsNo
    ) {
        return world.operations().shipFinishedStock(
                order(shipment, mother, work, customerId, quantity, weight, logisticsNo, requestId),
                ACTOR
        );
    }

    private ShipFinishedStockCommand order(
            String shipment,
            String mother,
            String work,
            long customerId,
            String quantity,
            String weight,
            String logisticsNo,
            String requestId
    ) {
        return new ShipFinishedStockCommand(
                shipment,
                mother,
                work,
                customerId,
                customerId,
                "AU9999",
                "FINISHED-CUSTOMER",
                "SHIPPED-CUSTOMER",
                "2026-10-12",
                "成品",
                new BigDecimal(quantity),
                "件",
                new BigDecimal(weight),
                "顺丰",
                logisticsNo,
                "",
                "",
                null,
                "",
                "何琳",
                ACTOR,
                "",
                "确认发货",
                requestId
        );
    }

    private static StockDimension dimension(String mother, String work, long customerId, String warehouse, StockStatus status) {
        return new StockDimension(OwnerType.CUSTOMER, customerId, customerId, mother, work, "AU9999", warehouse, status);
    }

    private interface World {
        BusinessOperationService operations();

        StockLedgerService stocks();

        BigDecimal finished(String mother, String work, long customerId);

        BigDecimal finishedWeight(String mother, String work, long customerId);

        BigDecimal shipped(String mother, String work, long customerId);

        BigDecimal shippedWeight(String mother, String work, long customerId);

        int shipmentLedgers(String mother);

        OutboundOrderRecord outbound(String code);

        boolean hasOutbound(String code);

        int outboundCount();

        boolean hasRequest(String requestId);

        World withProbes(CommandResultProbe resultProbe, OutboundSaveProbe outboundProbe);
    }

    private static final class MemoryWorld implements World {
        private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        private final InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
        private final InMemoryCommandRequestRepository requests = new InMemoryCommandRequestRepository();
        private final StockLedgerService stocks;
        private final BusinessOperationService operations;

        private MemoryWorld(CommandResultProbe resultProbe, OutboundSaveProbe outboundProbe) {
            this.stocks = new StockLedgerService(stock);
            BusinessWorkUnit unit = new MemoryBusinessWorkUnit(requests, resultProbe);
            this.operations = new BusinessOperationService(
                    stocks, null, documents, unit, MotherOrderSaveProbe.NONE, outboundProbe);
        }

        @Override
        public BusinessOperationService operations() {
            return operations;
        }

        @Override
        public StockLedgerService stocks() {
            return stocks;
        }

        @Override
        public BigDecimal finished(String mother, String work, long customerId) {
            return quantity(mother, work, customerId, StockStatus.FINISHED);
        }

        @Override
        public BigDecimal finishedWeight(String mother, String work, long customerId) {
            return weight(mother, work, customerId, StockStatus.FINISHED);
        }

        @Override
        public BigDecimal shipped(String mother, String work, long customerId) {
            return quantity(mother, work, customerId, StockStatus.SHIPPED);
        }

        @Override
        public BigDecimal shippedWeight(String mother, String work, long customerId) {
            return weight(mother, work, customerId, StockStatus.SHIPPED);
        }

        @Override
        public int shipmentLedgers(String mother) {
            return (int) stock.findAllLedgers().stream()
                    .filter(entry -> mother.equals(entry.to().motherOrderCode()))
                    .filter(entry -> "outbound_shipment".equals(entry.documentType()))
                    .count();
        }

        @Override
        public OutboundOrderRecord outbound(String code) {
            return documents.findOutboundOrderByCode(code).orElseThrow();
        }

        @Override
        public boolean hasOutbound(String code) {
            return documents.findOutboundOrderByCode(code).isPresent();
        }

        @Override
        public int outboundCount() {
            return documents.listOutboundOrders().size();
        }

        @Override
        public boolean hasRequest(String requestId) {
            return requests.findByRequestId(requestId).isPresent();
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, OutboundSaveProbe outboundProbe) {
            BusinessWorkUnit unit = new MemoryBusinessWorkUnit(requests, resultProbe);
            BusinessOperationService next = new BusinessOperationService(
                    stocks, null, documents, unit, MotherOrderSaveProbe.NONE, outboundProbe);
            return new WorldView(next, this);
        }

        private BigDecimal quantity(String mother, String work, long customerId, StockStatus status) {
            String warehouse = status == StockStatus.SHIPPED ? "SHIPPED-CUSTOMER" : "FINISHED-CUSTOMER";
            return stock.findBalance(dimension(mother, work, customerId, warehouse, status)).quantity();
        }

        private BigDecimal weight(String mother, String work, long customerId, StockStatus status) {
            String warehouse = status == StockStatus.SHIPPED ? "SHIPPED-CUSTOMER" : "FINISHED-CUSTOMER";
            return stock.findBalance(dimension(mother, work, customerId, warehouse, status)).weight();
        }
    }

    private static final class JdbcWorld implements World {
        private final JdbcDataSource dataSource;
        private final JdbcBusinessDocumentRepository documents;
        private final JdbcStockLedgerRepository stock;
        private final JdbcCommandRequestRepository requests;
        private final StockLedgerService stocks;
        private final BusinessOperationService operations;

        private JdbcWorld(CommandResultProbe resultProbe, OutboundSaveProbe outboundProbe) throws Exception {
            dataSource = StockRepositoryFixture.jdbcDataSource();
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        create table biz_outbound_order (
                            id bigint generated by default as identity primary key,
                            code varchar(80) not null,
                            mother_order_code varchar(80) not null,
                            work_order_code varchar(90) not null,
                            customer_id bigint not null,
                            owner_customer_id bigint,
                            material_code varchar(80) not null,
                            from_warehouse_code varchar(50) not null,
                            to_warehouse_code varchar(50) not null,
                            delivery_date varchar(20),
                            delivery_category varchar(80),
                            delivery_quantity decimal(18,3) not null,
                            delivery_quantity_unit varchar(20) not null,
                            delivery_weight decimal(18,3) not null,
                            express_company varchar(80),
                            express_no varchar(80) not null,
                            logistics_raw_text varchar(160),
                            handler_name_snapshot varchar(120),
                            source_register_type varchar(64),
                            source_line_no int,
                            source_sequence_no varchar(40),
                            operator varchar(80) not null,
                            evidence_code varchar(120),
                            outbound_remark varchar(500),
                            order_status varchar(40) not null,
                            ledger_code varchar(80) not null,
                            created_at varchar(40) not null,
                            unique (code),
                            unique (ledger_code)
                        )
                        """);
                statement.execute("""
                        create table biz_outbound_order_item (
                            id bigint generated by default as identity primary key,
                            outbound_order_code varchar(80) not null,
                            mother_order_code varchar(80) not null,
                            work_order_code varchar(90) not null,
                            material_code varchar(80) not null,
                            quantity decimal(18,3) not null,
                            quantity_unit varchar(20) not null,
                            weight decimal(18,3) not null,
                            weight_unit varchar(20) not null,
                            ledger_code varchar(80) not null
                        )
                        """);
                apply(statement, "src/main/resources/db/mysql/V44__m3_command_request.sql");
            }
            documents = new JdbcBusinessDocumentRepository(dataSource);
            stock = new JdbcStockLedgerRepository(dataSource);
            requests = new JdbcCommandRequestRepository(dataSource);
            stocks = new StockLedgerService(stock);
            BusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests, resultProbe);
            operations = new BusinessOperationService(
                    stocks, null, documents, unit, MotherOrderSaveProbe.NONE, outboundProbe);
        }

        @Override
        public BusinessOperationService operations() {
            return operations;
        }

        @Override
        public StockLedgerService stocks() {
            return stocks;
        }

        @Override
        public BigDecimal finished(String mother, String work, long customerId) {
            return quantity(mother, work, customerId, StockStatus.FINISHED);
        }

        @Override
        public BigDecimal finishedWeight(String mother, String work, long customerId) {
            return weight(mother, work, customerId, StockStatus.FINISHED);
        }

        @Override
        public BigDecimal shipped(String mother, String work, long customerId) {
            return quantity(mother, work, customerId, StockStatus.SHIPPED);
        }

        @Override
        public BigDecimal shippedWeight(String mother, String work, long customerId) {
            return weight(mother, work, customerId, StockStatus.SHIPPED);
        }

        @Override
        public int shipmentLedgers(String mother) {
            return (int) stock.findAllLedgers().stream()
                    .filter(entry -> mother.equals(entry.to().motherOrderCode()))
                    .filter(entry -> "outbound_shipment".equals(entry.documentType()))
                    .count();
        }

        @Override
        public OutboundOrderRecord outbound(String code) {
            return documents.findOutboundOrderByCode(code).orElseThrow();
        }

        @Override
        public boolean hasOutbound(String code) {
            return documents.findOutboundOrderByCode(code).isPresent();
        }

        @Override
        public int outboundCount() {
            return documents.listOutboundOrders().size();
        }

        @Override
        public boolean hasRequest(String requestId) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "select count(*) from biz_command_request where request_id = '" + requestId + "'")) {
                rs.next();
                return rs.getInt(1) > 0;
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, OutboundSaveProbe outboundProbe) {
            BusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests, resultProbe);
            BusinessOperationService next = new BusinessOperationService(
                    stocks, null, documents, unit, MotherOrderSaveProbe.NONE, outboundProbe);
            return new WorldView(next, this);
        }

        private void close() {
        }

        private BigDecimal quantity(String mother, String work, long customerId, StockStatus status) {
            String warehouse = status == StockStatus.SHIPPED ? "SHIPPED-CUSTOMER" : "FINISHED-CUSTOMER";
            return stock.findBalance(dimension(mother, work, customerId, warehouse, status)).quantity();
        }

        private BigDecimal weight(String mother, String work, long customerId, StockStatus status) {
            String warehouse = status == StockStatus.SHIPPED ? "SHIPPED-CUSTOMER" : "FINISHED-CUSTOMER";
            return stock.findBalance(dimension(mother, work, customerId, warehouse, status)).weight();
        }

        private static void apply(Statement statement, String path) throws Exception {
            String sql = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            for (String item : MigrationCatalog.statements(sql)) {
                statement.execute(item);
            }
        }
    }

    private record WorldView(BusinessOperationService operations, World delegate) implements World {
        @Override
        public StockLedgerService stocks() {
            return delegate.stocks();
        }

        @Override
        public BigDecimal finished(String mother, String work, long customerId) {
            return delegate.finished(mother, work, customerId);
        }

        @Override
        public BigDecimal finishedWeight(String mother, String work, long customerId) {
            return delegate.finishedWeight(mother, work, customerId);
        }

        @Override
        public BigDecimal shipped(String mother, String work, long customerId) {
            return delegate.shipped(mother, work, customerId);
        }

        @Override
        public BigDecimal shippedWeight(String mother, String work, long customerId) {
            return delegate.shippedWeight(mother, work, customerId);
        }

        @Override
        public int shipmentLedgers(String mother) {
            return delegate.shipmentLedgers(mother);
        }

        @Override
        public OutboundOrderRecord outbound(String code) {
            return delegate.outbound(code);
        }

        @Override
        public boolean hasOutbound(String code) {
            return delegate.hasOutbound(code);
        }

        @Override
        public int outboundCount() {
            return delegate.outboundCount();
        }

        @Override
        public boolean hasRequest(String requestId) {
            return delegate.hasRequest(requestId);
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, OutboundSaveProbe outboundProbe) {
            return delegate.withProbes(resultProbe, outboundProbe);
        }
    }
}
