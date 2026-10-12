package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.InboundConfirmationResult;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeResult;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.JdbcBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 来料建单和入库确认的回放与同一次提交。
 * 报工、质检、发货不在这里，不能用本测试宣称那些命令已经安全。
 */
class IntakeReceivingTest {

    private final OperatorContext receiver = new OperatorContext("收货员甲", RoleCode.RECEIVER, DataScope.ALL);

    @Test
    void memoryIntakeAndInboundShareOneCommit() {
        MemoryWorld world = new MemoryWorld(CommandResultProbe.NONE, MotherOrderSaveProbe.NONE);
        assertIntake(world);
        assertInbound(world);
    }

    @Test
    void jdbcIntakeAndInboundShareOneCommit() throws Exception {
        JdbcWorld world = new JdbcWorld(CommandResultProbe.NONE, MotherOrderSaveProbe.NONE);
        assertIntake(world);
        assertInbound(world);
        world.close();
    }

    private void assertIntake(World world) {
        MobileIntakeResult created = world.intake().intake(intake("req-intake-01", "恒信珠宝", "戒托抛光", "12"), receiver);
        MobileIntakeResult replay = world.intake().intake(intake("req-intake-01", "恒信珠宝", "  戒托抛光  ", "12.0"), receiver);

        assertThat(replay.motherOrderCode()).isEqualTo(created.motherOrderCode());
        assertThat(replay.ledgerCode()).isEqualTo(created.ledgerCode());
        assertThat(replay.alreadyExists()).isFalse();
        assertThat(world.mothers()).isEqualTo(1);
        assertThat(world.ledgers()).isEqualTo(1);
        assertThat(world.requests()).isEqualTo(1);

        assertThatThrownBy(() -> world.intake().intake(intake("req-intake-01", "恒信珠宝", "戒托抛光", "13"), receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> world.intake().intake(intake("req-intake-01", "雅辰饰品", "戒托抛光", "12"), receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> world.intake().intake(
                intake("req-intake-01", "恒信珠宝", "戒托抛光", "12"),
                new OperatorContext("另一账号", RoleCode.RECEIVER, DataScope.ALL)))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("该请求不能跨账号重放");
        assertThat(world.mothers()).isEqualTo(1);
        assertThat(world.ledgers()).isEqualTo(1);

        MobileIntakeResult opened = world.intake().intake(intake("req-intake-02", "恒信珠宝", "另一条要求", "9"), receiver);
        assertThat(opened.alreadyExists()).isTrue();
        assertThat(opened.motherOrderCode()).isEqualTo(created.motherOrderCode());
        assertThat(world.mothers()).isEqualTo(1);
        assertThat(world.ledgers()).isEqualTo(1);

        World failingMother = world.withProbes(CommandResultProbe.NONE, () -> {
            throw new IllegalStateException("母单保存失败");
        });
        assertThatThrownBy(() -> failingMother.intake().intake(intake("req-intake-03", "璟瑞工贸", "镶嵌", "1", "SF2000000001"), receiver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("母单保存失败");
        assertThat(world.mothers()).isEqualTo(1);
        assertThat(world.ledgers()).isEqualTo(1);
        assertThat(failingMother.requests()).isEqualTo(world.requests());

        World failingResult = world.withProbes(() -> {
            throw new IllegalStateException("请求结果保存失败");
        }, MotherOrderSaveProbe.NONE);
        assertThatThrownBy(() -> failingResult.intake().intake(intake("req-intake-04", "瑞禾珠宝", "抛光", "1", "SF2000000002"), receiver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("请求结果保存失败");
        assertThat(world.mothers()).isEqualTo(1);
        assertThat(world.ledgers()).isEqualTo(1);

        race(world, "req-intake-05", "req-intake-05", "SF3000000001");
        race(world, "req-intake-06", "req-intake-07", "SF3000000002");
        assertThat(world.mothers()).isEqualTo(3);
        assertThat(world.ledgers()).isEqualTo(3);
    }

    private void assertInbound(World world) {
        int mothers = world.mothers();
        int ledgers = world.ledgers();
        int requests = world.requests();
        InboundConfirmationResult created = world.operations().confirmInbound(inbound("req-inbound-01", "MD-IN-001", "3.5"), receiver.operator());
        InboundConfirmationResult replay = world.operations().confirmInbound(inbound("req-inbound-01", "MD-IN-001", "3.50"), receiver.operator());

        assertThat(replay.ledgerCode()).isEqualTo(created.ledgerCode());
        assertThat(replay.motherOrderCode()).isEqualTo("MD-IN-001");
        assertThat(world.mothers()).isEqualTo(mothers + 1);
        assertThat(world.ledgers()).isEqualTo(ledgers + 1);
        assertThat(world.requests()).isEqualTo(requests + 1);

        assertThatThrownBy(() -> world.operations().confirmInbound(inbound("req-inbound-01", "MD-IN-002", "3.5"), receiver.operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> world.operations().confirmInbound(
                inbound("req-inbound-01", "MD-IN-001", "3.5", 1002L, "雅辰饰品"),
                receiver.operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> world.operations().confirmInbound(inbound("req-inbound-01", "MD-IN-001", "3.5"), "另一账号"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("该请求不能跨账号重放");
        assertThat(world.ledgers()).isEqualTo(ledgers + 1);

        World failingMother = world.withProbes(CommandResultProbe.NONE, () -> {
            throw new IllegalStateException("母单保存失败");
        });
        assertThatThrownBy(() -> failingMother.operations().confirmInbound(inbound("req-inbound-02", "MD-IN-002", "1"), receiver.operator()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("母单保存失败");
        assertThat(world.mothers()).isEqualTo(mothers + 1);
        assertThat(world.ledgers()).isEqualTo(ledgers + 1);
    }

    private void race(World world, String leftRequestId, String rightRequestId, String courier) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<MobileIntakeResult> left = pool.submit(() -> {
                start.await();
                return world.intake().intake(intake(leftRequestId, "恒信珠宝", "并发建单", "2", courier), receiver);
            });
            Future<MobileIntakeResult> right = pool.submit(() -> {
                start.await();
                return world.intake().intake(intake(rightRequestId, "恒信珠宝", "并发建单", "2", courier), receiver);
            });
            start.countDown();
            assertThat(left.get().motherOrderCode()).isEqualTo(right.get().motherOrderCode());
            assertThat(left.get().ledgerCode()).isEqualTo(right.get().ledgerCode());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    private MobileIntakeCommand intake(String requestId, String customer, String requirement, String quantity) {
        return intake(requestId, customer, requirement, quantity, "SF1048329001");
    }

    private MobileIntakeCommand intake(String requestId, String customer, String requirement, String quantity, String courier) {
        return new MobileIntakeCommand(
                courier,
                customer,
                null,
                requirement,
                "2026-10-20",
                "18K 金戒托",
                new BigDecimal(quantity),
                new BigDecimal("18.500"),
                "顺丰",
                "",
                "件",
                requestId
        );
    }

    private ConfirmInboundCommand inbound(String requestId, String mother, String weight) {
        return inbound(requestId, mother, weight, 1001L, "恒信珠宝");
    }

    private ConfirmInboundCommand inbound(String requestId, String mother, String weight, long customerId, String customerName) {
        return new ConfirmInboundCommand(
                mother,
                customerId,
                customerId,
                "MAT-1",
                "2026-10-12",
                "顺丰",
                "SF9" + mother,
                "",
                "寄货人",
                "收货员甲",
                "戒托",
                null,
                null,
                "",
                "RAW-CUSTOMER",
                new BigDecimal("2"),
                "件",
                new BigDecimal(weight),
                receiver.operator(),
                "",
                "确认入库",
                "戒托抛光",
                "2026-10-20",
                customerName,
                requestId
        );
    }

    private interface World {
        MobileIntakeService intake();

        BusinessOperationService operations();

        int mothers();

        int ledgers();

        int requests();

        World withProbes(CommandResultProbe resultProbe, MotherOrderSaveProbe motherProbe);
    }

    private static final class MemoryWorld implements World {
        private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        private final InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
        private final InMemoryCommandRequestRepository requests = new InMemoryCommandRequestRepository();
        private final MobileIntakeService intake;
        private final BusinessOperationService operations;

        private MemoryWorld(CommandResultProbe resultProbe, MotherOrderSaveProbe motherProbe) {
            BusinessWorkUnit unit = new MemoryBusinessWorkUnit(requests, resultProbe);
            this.operations = new BusinessOperationService(new StockLedgerService(stock), null, documents, unit, motherProbe);
            this.intake = new MobileIntakeService(operations, documents, unit);
        }

        @Override
        public MobileIntakeService intake() {
            return intake;
        }

        @Override
        public BusinessOperationService operations() {
            return operations;
        }

        @Override
        public int mothers() {
            return documents.listMotherOrders().size();
        }

        @Override
        public int ledgers() {
            return stock.findAllLedgers().size();
        }

        @Override
        public int requests() {
            int count = 0;
            for (String id : new String[]{
                    "req-intake-01", "req-intake-02", "req-intake-03", "req-intake-04", "req-intake-05",
                    "req-inbound-01", "req-inbound-02"
            }) {
                if (requests.findByRequestId(id).isPresent()) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, MotherOrderSaveProbe motherProbe) {
            BusinessWorkUnit unit = new MemoryBusinessWorkUnit(requests, resultProbe);
            BusinessOperationService next = new BusinessOperationService(
                    new StockLedgerService(stock), null, documents, unit, motherProbe);
            return new WorldView(new MobileIntakeService(next, documents, unit), next, this);
        }
    }

    private static final class JdbcWorld implements World {
        private final JdbcDataSource dataSource;
        private final JdbcBusinessDocumentRepository documents;
        private final JdbcStockLedgerRepository stock;
        private final JdbcCommandRequestRepository requests;
        private final MobileIntakeService intake;
        private final BusinessOperationService operations;

        private JdbcWorld(CommandResultProbe resultProbe, MotherOrderSaveProbe motherProbe) throws Exception {
            dataSource = StockRepositoryFixture.jdbcDataSource();
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        create table biz_mother_order (
                            id bigint generated by default as identity primary key,
                            code varchar(80) not null unique,
                            customer_id bigint not null,
                            owner_customer_id bigint,
                            material_code varchar(80) not null,
                            inbound_date varchar(20),
                            inbound_express_company varchar(80),
                            inbound_express_no varchar(80),
                            sender_name_snapshot varchar(120),
                            receiver_name_snapshot varchar(120),
                            unpack_signer_name varchar(120),
                            goods_name_snapshot varchar(160),
                            owner_type varchar(20) not null,
                            is_customer_owned boolean not null,
                            warehouse_code varchar(50) not null,
                            quantity decimal(18,3) not null,
                            quantity_unit varchar(20) not null,
                            weight decimal(18,3) not null,
                            stock_status varchar(40) not null,
                            ledger_code varchar(80) not null unique,
                            source_register_type varchar(64),
                            source_line_no int,
                            source_sequence_no varchar(40),
                            operator varchar(80) not null,
                            evidence_code varchar(120),
                            inbound_remark varchar(500),
                            order_status varchar(40) not null,
                            created_at varchar(40) not null,
                            processing_requirement varchar(500) not null,
                            due_date varchar(20) not null,
                            customer_name varchar(120) not null
                        )
                        """);
                statement.execute("""
                        create table biz_mother_order_item (
                            id bigint generated by default as identity primary key,
                            mother_order_code varchar(80) not null,
                            material_code varchar(80) not null,
                            quantity decimal(18,3) not null,
                            quantity_unit varchar(20) not null,
                            weight decimal(18,3) not null,
                            weight_unit varchar(20) not null,
                            ledger_code varchar(80) not null
                        )
                        """);
                apply(statement, "src/main/resources/db/mysql/V44__m3_command_request.sql");
                apply(statement, "src/main/resources/db/mysql/V45__m3_mother_order_courier_identity.sql");
            }
            documents = new JdbcBusinessDocumentRepository(dataSource);
            stock = new JdbcStockLedgerRepository(dataSource);
            requests = new JdbcCommandRequestRepository(dataSource);
            BusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests, resultProbe);
            operations = new BusinessOperationService(new StockLedgerService(stock), null, documents, unit, motherProbe);
            intake = new MobileIntakeService(operations, documents, unit);
        }

        @Override
        public MobileIntakeService intake() {
            return intake;
        }

        @Override
        public BusinessOperationService operations() {
            return operations;
        }

        @Override
        public int mothers() {
            return documents.listMotherOrders().size();
        }

        @Override
        public int ledgers() {
            return stock.findAllLedgers().size();
        }

        @Override
        public int requests() {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select count(*) from biz_command_request where result_text <> ''")) {
                rs.next();
                return rs.getInt(1);
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, MotherOrderSaveProbe motherProbe) {
            BusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests, resultProbe);
            BusinessOperationService next = new BusinessOperationService(
                    new StockLedgerService(stock), null, documents, unit, motherProbe);
            return new WorldView(new MobileIntakeService(next, documents, unit), next, this);
        }

        private void close() {
        }

        private static void apply(Statement statement, String path) throws Exception {
            String sql = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            for (String item : MigrationCatalog.statements(sql)) {
                statement.execute(item);
            }
        }
    }

    private record WorldView(MobileIntakeService intake, BusinessOperationService operations, World counts) implements World {
        @Override
        public int mothers() {
            return counts.mothers();
        }

        @Override
        public int ledgers() {
            return counts.ledgers();
        }

        @Override
        public int requests() {
            return counts.requests();
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, MotherOrderSaveProbe motherProbe) {
            return counts.withProbes(resultProbe, motherProbe);
        }
    }
}
