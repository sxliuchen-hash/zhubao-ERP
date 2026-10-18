package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.ProcessStationReportResult;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.JdbcBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.inventory.infra.JdbcStockLedgerRepository;
import com.zhubao.erp.inventory.support.StockRepositoryFixture;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工序交付的回放与同一次提交。
 * 只覆盖带请求编号的岗位交付。来料测试和 S5p 的公共测试不能说明这里已经安全。
 * 质检、返工和发货不在这里。
 */
class ProcessDeliveryTest {

    @Test
    void memoryProcessHandoffSharesOneCommit() {
        assertDelivery(new MemoryWorld(CommandResultProbe.NONE, HandoffSaveProbe.NONE));
    }

    @Test
    void jdbcProcessHandoffSharesOneCommit() throws Exception {
        JdbcWorld world = new JdbcWorld(CommandResultProbe.NONE, HandoffSaveProbe.NONE);
        assertDelivery(world);
        world.close();
    }

    private void assertDelivery(World world) {
        keep(world, "MD-ST-01", 1001L, "恒信珠宝");
        AuthUserAccount carving = account("雕刻工 林海", "雕刻");
        AuthUserAccount inlay = account("镶嵌工 赵敏", "镶嵌");

        assertThatThrownBy(() -> world.station().report(sticker("MD-ST-01"), "镶嵌", inlay, "req-station-skip"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("雕刻")
                .hasMessageContaining("还没交");
        assertThatThrownBy(() -> world.station().report(sticker("MD-ST-01"), "雕刻", account("操作工 王强"), "req-station-role"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前账号不能做这道工序");
        assertThat(world.raw("MD-ST-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.handoffs("MD-ST-01")).isZero();
        assertThat(world.hasRequest("req-station-skip")).isFalse();
        assertThat(world.hasRequest("req-station-role")).isFalse();

        ProcessStationReportResult created = world.station().report(sticker("MD-ST-01"), "雕刻", carving, "req-station-01");
        ProcessStationReportResult replay = world.station().report(sticker("MD-ST-01"), "  雕刻  ", carving, "req-station-01");

        assertThat(replay.ledgerCode()).isEqualTo(created.ledgerCode());
        assertThat(replay.orderCode()).isEqualTo("MD-ST-01");
        assertThat(replay.movedToSemiFinished()).isFalse();
        assertThat(replay.stockStatus()).isEqualTo("WIP");
        assertThat(world.handoffs("MD-ST-01")).isEqualTo(1);
        assertThat(world.handoffLedger("MD-ST-01", "雕刻")).isEqualTo(created.ledgerCode());
        assertThat(world.raw("MD-ST-01", 1001L)).isEqualByComparingTo("0");
        assertThat(world.wip("MD-ST-01", 1001L)).isEqualByComparingTo("10");
        assertThat(world.semi("MD-ST-01", 1001L)).isEqualByComparingTo("0");
        assertThat(world.ledgers("MD-ST-01")).isEqualTo(3);
        assertThat(world.documentTypes("MD-ST-01")).contains("work_order_split", "process_station_report");

        assertThatThrownBy(() -> world.station().report(sticker("MD-ST-01"), "镶嵌", carving, "req-station-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        keep(world, "MD-ST-02", 1002L, "雅辰饰品");
        assertThatThrownBy(() -> world.station().report(sticker("MD-ST-02"), "雕刻", carving, "req-station-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");
        assertThatThrownBy(() -> world.station().report(
                sticker("MD-ST-01"), "雕刻", account("另一账号", "雕刻"), "req-station-01"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("该请求不能跨账号重放");
        assertThatThrownBy(() -> world.station().report(sticker("MD-ST-01"), "雕刻", carving, "req-station-again"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经交过");
        assertThat(world.ledgers("MD-ST-01")).isEqualTo(3);
        assertThat(world.handoffs("MD-ST-01")).isEqualTo(1);
        assertThat(world.raw("MD-ST-02", 1002L)).isEqualByComparingTo("10");
        assertThat(world.handoffs("MD-ST-02")).isZero();
        assertThat(world.hasRequest("req-station-again")).isFalse();

        world.operations().confirmInbound(inbound("MD-ST-SPLIT", 1001L, "恒信珠宝"));
        world.splits().split(new SplitMotherOrderCommand("MD-ST-SPLIT", "何琳", List.of(
                new SplitMotherOrderLine(new BigDecimal("4"), new BigDecimal("8"), null),
                new SplitMotherOrderLine(new BigDecimal("6"), new BigDecimal("12"), null)
        )));
        assertThatThrownBy(() -> world.station().report(sticker("MD-ST-SPLIT"), "雕刻", carving, "req-station-split"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请扫描子单贴纸");
        assertThat(world.handoffs("MD-ST-SPLIT")).isZero();
        assertThat(world.hasRequest("req-station-split")).isFalse();

        keep(world, "MD-ST-FAIL", 1001L, "恒信珠宝");
        World failingHandoff = world.withProbes(CommandResultProbe.NONE, () -> {
            throw new IllegalStateException("交接保存失败");
        });
        assertThatThrownBy(() -> failingHandoff.station().report(sticker("MD-ST-FAIL"), "雕刻", carving, "req-station-fail"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("交接保存失败");
        assertThat(world.raw("MD-ST-FAIL", 1001L)).isEqualByComparingTo("10");
        assertThat(world.wip("MD-ST-FAIL", 1001L)).isEqualByComparingTo("0");
        assertThat(world.ledgers("MD-ST-FAIL")).isEqualTo(1);
        assertThat(world.handoffs("MD-ST-FAIL")).isZero();
        assertThat(world.hasRequest("req-station-fail")).isFalse();

        keep(world, "MD-ST-RESULT", 1001L, "恒信珠宝");
        World failingResult = world.withProbes(() -> {
            throw new IllegalStateException("请求结果保存失败");
        }, HandoffSaveProbe.NONE);
        assertThatThrownBy(() -> failingResult.station().report(sticker("MD-ST-RESULT"), "雕刻", carving, "req-station-result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("请求结果保存失败");
        assertThat(world.raw("MD-ST-RESULT", 1001L)).isEqualByComparingTo("10");
        assertThat(world.ledgers("MD-ST-RESULT")).isEqualTo(1);
        assertThat(world.handoffs("MD-ST-RESULT")).isZero();
        assertThat(world.hasRequest("req-station-result")).isFalse();

        keep(world, "MD-ST-OLD", 1001L, "恒信珠宝");
        World oldPath = world.withProbes(CommandResultProbe.NONE, () -> {
            throw new IllegalStateException("交接保存失败");
        });
        assertThatThrownBy(() -> oldPath.station().report(sticker("MD-ST-OLD"), "雕刻", carving))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("交接保存失败");
        assertThat(world.raw("MD-ST-OLD", 1001L)).isEqualByComparingTo("0");
        assertThat(world.wip("MD-ST-OLD", 1001L)).isEqualByComparingTo("10");
        assertThat(world.handoffs("MD-ST-OLD")).isZero();
        assertThat(world.hasRequest("req-station-old")).isFalse();

        keep(world, "MD-ST-RACE", 1001L, "恒信珠宝");
        raceSame(world, "MD-ST-RACE", "req-station-race");
        keep(world, "MD-ST-RACE2", 1001L, "恒信珠宝");
        raceDifferent(world, "MD-ST-RACE2");
    }

    private void raceSame(World world, String mother, String requestId) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AuthUserAccount carving = account("雕刻工 林海", "雕刻");
            Future<ProcessStationReportResult> left = pool.submit(() -> {
                start.await();
                return world.station().report(sticker(mother), "雕刻", carving, requestId);
            });
            Future<ProcessStationReportResult> right = pool.submit(() -> {
                start.await();
                return world.station().report(sticker(mother), "  雕刻  ", carving, requestId);
            });
            start.countDown();
            assertThat(left.get().ledgerCode()).isEqualTo(right.get().ledgerCode());
            assertThat(world.handoffs(mother)).isEqualTo(1);
            assertThat(world.ledgers(mother)).isEqualTo(3);
            assertThat(world.raw(mother, 1001L)).isEqualByComparingTo("0");
            assertThat(world.wip(mother, 1001L)).isEqualByComparingTo("10");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    private void raceDifferent(World world, String mother) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AuthUserAccount carving = account("雕刻工 林海", "雕刻");
            Future<ProcessStationReportResult> left = pool.submit(() -> {
                start.await();
                return world.station().report(sticker(mother), "雕刻", carving, "req-station-left");
            });
            Future<ProcessStationReportResult> right = pool.submit(() -> {
                start.await();
                return world.station().report(sticker(mother), "雕刻", carving, "req-station-right");
            });
            start.countDown();
            int success = 0;
            success += outcome(left);
            success += outcome(right);
            assertThat(success).isEqualTo(1);
            assertThat(world.handoffs(mother)).isEqualTo(1);
            assertThat(world.ledgers(mother)).isEqualTo(3);
            assertThat(world.raw(mother, 1001L)).isEqualByComparingTo("0");
            assertThat(world.wip(mother, 1001L)).isEqualByComparingTo("10");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    private int outcome(Future<ProcessStationReportResult> future) throws Exception {
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

    private void keep(World world, String code, long customerId, String customerName) {
        world.operations().confirmInbound(inbound(code, customerId, customerName));
        world.splits().keep(code, "何琳");
    }

    private String sticker(String code) {
        return "JG|mother|" + code;
    }

    private ConfirmInboundCommand inbound(String code, long customerId, String customerName) {
        return new ConfirmInboundCommand(
                code,
                customerId,
                customerId,
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
                new BigDecimal("10"),
                "件",
                new BigDecimal("20"),
                "何琳",
                "",
                "测试入库",
                "内壁刻字",
                "2026-10-20",
                customerName
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

    private interface World {
        ProcessStationService station();

        BusinessOperationService operations();

        MotherOrderSplitService splits();

        BigDecimal raw(String mother, long customerId);

        BigDecimal wip(String mother, long customerId);

        BigDecimal semi(String mother, long customerId);

        int ledgers(String mother);

        List<String> documentTypes(String mother);

        int handoffs(String mother);

        String handoffLedger(String mother, String process);

        boolean hasRequest(String requestId);

        World withProbes(CommandResultProbe resultProbe, HandoffSaveProbe handoffProbe);
    }

    private static final class MemoryWorld implements World {
        private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        private final InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
        private final InMemoryCommandRequestRepository requests = new InMemoryCommandRequestRepository();
        private final StockLedgerService stocks;
        private final BusinessOperationService operations;
        private final MotherOrderSplitService splits;
        private final ProcessStationService station;

        private MemoryWorld(CommandResultProbe resultProbe, HandoffSaveProbe handoffProbe) {
            this.stocks = new StockLedgerService(stock);
            this.operations = new BusinessOperationService(stocks, null, documents);
            this.splits = new MotherOrderSplitService(documents, stocks);
            BusinessWorkUnit unit = new MemoryBusinessWorkUnit(requests, resultProbe);
            this.station = new ProcessStationService(
                    documents, stocks, operations, new ScanCodeParser(), null, unit, handoffProbe);
        }

        @Override
        public ProcessStationService station() {
            return station;
        }

        @Override
        public BusinessOperationService operations() {
            return operations;
        }

        @Override
        public MotherOrderSplitService splits() {
            return splits;
        }

        @Override
        public BigDecimal raw(String mother, long customerId) {
            return quantity(mother, customerId, null, StockStatus.RAW_WAITING);
        }

        @Override
        public BigDecimal wip(String mother, long customerId) {
            return quantity(mother, customerId, mother, StockStatus.WIP);
        }

        @Override
        public BigDecimal semi(String mother, long customerId) {
            return quantity(mother, customerId, mother, StockStatus.SEMI_FINISHED);
        }

        @Override
        public int ledgers(String mother) {
            return (int) stock.findAllLedgers().stream()
                    .filter(entry -> mother.equals(entry.to().motherOrderCode()))
                    .count();
        }

        @Override
        public List<String> documentTypes(String mother) {
            return stock.findAllLedgers().stream()
                    .filter(entry -> mother.equals(entry.to().motherOrderCode()))
                    .map(StockLedgerEntry::documentType)
                    .toList();
        }

        @Override
        public int handoffs(String mother) {
            return documents.listProcessStationHandoffs(mother).size();
        }

        @Override
        public String handoffLedger(String mother, String process) {
            return documents.findProcessStationHandoff(mother, process).orElseThrow().ledgerCode();
        }

        @Override
        public boolean hasRequest(String requestId) {
            return requests.findByRequestId(requestId).isPresent();
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, HandoffSaveProbe handoffProbe) {
            BusinessWorkUnit unit = new MemoryBusinessWorkUnit(requests, resultProbe);
            ProcessStationService next = new ProcessStationService(
                    documents, stocks, operations, new ScanCodeParser(), null, unit, handoffProbe);
            return new WorldView(next, this);
        }

        private BigDecimal quantity(String mother, long customerId, String workOrder, StockStatus status) {
            return stock.findBalance(new StockDimension(
                    OwnerType.CUSTOMER, customerId, customerId, mother, workOrder, "AU9999", "RAW-CUSTOMER", status
            )).quantity();
        }
    }

    private static final class JdbcWorld implements World {
        private final JdbcDataSource dataSource;
        private final JdbcBusinessDocumentRepository documents;
        private final JdbcStockLedgerRepository stock;
        private final JdbcCommandRequestRepository requests;
        private final StockLedgerService stocks;
        private final BusinessOperationService operations;
        private final MotherOrderSplitService splits;
        private final ProcessStationService station;

        private JdbcWorld(CommandResultProbe resultProbe, HandoffSaveProbe handoffProbe) throws Exception {
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
                            customer_name varchar(120) not null,
                            recycled_at varchar(40),
                            recycled_by varchar(80),
                            recycle_batch varchar(40),
                            recycle_reason varchar(200)
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
                statement.execute("""
                        create table biz_mother_order_split (
                            mother_order_code varchar(80) primary key,
                            split_decision varchar(20) not null,
                            verification_code varchar(16) not null,
                            operator varchar(80) not null,
                            created_at varchar(40) not null
                        )
                        """);
                statement.execute("""
                        create table biz_work_order (
                            id bigint generated by default as identity primary key,
                            code varchar(90) not null unique,
                            mother_order_code varchar(80) not null,
                            customer_id bigint,
                            customer_name varchar(120) not null,
                            processing_requirement varchar(500) not null,
                            due_date varchar(20) not null,
                            material_code varchar(80) not null,
                            goods_name varchar(160) not null,
                            quantity decimal(18,3) not null,
                            quantity_unit varchar(20) not null,
                            weight decimal(18,3) not null,
                            warehouse_code varchar(50) not null,
                            process_names varchar(80) not null,
                            verification_code varchar(16) not null unique,
                            ledger_code varchar(80) not null,
                            operator varchar(80) not null,
                            created_at varchar(40) not null,
                            recycled_at varchar(40),
                            recycled_by varchar(80),
                            recycle_batch varchar(40),
                            recycle_reason varchar(200)
                        )
                        """);
                statement.execute("""
                        create table biz_process_station_handoff (
                            id bigint generated by default as identity primary key,
                            order_code varchar(90) not null,
                            process_name varchar(20) not null,
                            operator varchar(80) not null,
                            ledger_code varchar(80) not null,
                            created_at varchar(40) not null,
                            unique (order_code, process_name)
                        )
                        """);
                apply(statement, "src/main/resources/db/mysql/V44__m3_command_request.sql");
                apply(statement, "src/main/resources/db/mysql/V45__m3_mother_order_courier_identity.sql");
            }
            documents = new JdbcBusinessDocumentRepository(dataSource);
            stock = new JdbcStockLedgerRepository(dataSource);
            requests = new JdbcCommandRequestRepository(dataSource);
            stocks = new StockLedgerService(stock);
            operations = new BusinessOperationService(stocks, null, documents);
            splits = new MotherOrderSplitService(documents, stocks);
            BusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests, resultProbe);
            station = new ProcessStationService(
                    documents, stocks, operations, new ScanCodeParser(), null, unit, handoffProbe);
        }

        @Override
        public ProcessStationService station() {
            return station;
        }

        @Override
        public BusinessOperationService operations() {
            return operations;
        }

        @Override
        public MotherOrderSplitService splits() {
            return splits;
        }

        @Override
        public BigDecimal raw(String mother, long customerId) {
            return quantity(mother, customerId, null, StockStatus.RAW_WAITING);
        }

        @Override
        public BigDecimal wip(String mother, long customerId) {
            return quantity(mother, customerId, mother, StockStatus.WIP);
        }

        @Override
        public BigDecimal semi(String mother, long customerId) {
            return quantity(mother, customerId, mother, StockStatus.SEMI_FINISHED);
        }

        @Override
        public int ledgers(String mother) {
            return (int) stock.findAllLedgers().stream()
                    .filter(entry -> mother.equals(entry.to().motherOrderCode()))
                    .count();
        }

        @Override
        public List<String> documentTypes(String mother) {
            return stock.findAllLedgers().stream()
                    .filter(entry -> mother.equals(entry.to().motherOrderCode()))
                    .map(StockLedgerEntry::documentType)
                    .toList();
        }

        @Override
        public int handoffs(String mother) {
            return documents.listProcessStationHandoffs(mother).size();
        }

        @Override
        public String handoffLedger(String mother, String process) {
            return documents.findProcessStationHandoff(mother, process).orElseThrow().ledgerCode();
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
        public World withProbes(CommandResultProbe resultProbe, HandoffSaveProbe handoffProbe) {
            BusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, requests, resultProbe);
            ProcessStationService next = new ProcessStationService(
                    documents, stocks, operations, new ScanCodeParser(), null, unit, handoffProbe);
            return new WorldView(next, this);
        }

        private BigDecimal quantity(String mother, long customerId, String workOrder, StockStatus status) {
            return stock.findBalance(new StockDimension(
                    OwnerType.CUSTOMER, customerId, customerId, mother, workOrder, "AU9999", "RAW-CUSTOMER", status
            )).quantity();
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

    private record WorldView(ProcessStationService station, World delegate) implements World {
        @Override
        public BusinessOperationService operations() {
            return delegate.operations();
        }

        @Override
        public MotherOrderSplitService splits() {
            return delegate.splits();
        }

        @Override
        public BigDecimal raw(String mother, long customerId) {
            return delegate.raw(mother, customerId);
        }

        @Override
        public BigDecimal wip(String mother, long customerId) {
            return delegate.wip(mother, customerId);
        }

        @Override
        public BigDecimal semi(String mother, long customerId) {
            return delegate.semi(mother, customerId);
        }

        @Override
        public int ledgers(String mother) {
            return delegate.ledgers(mother);
        }

        @Override
        public List<String> documentTypes(String mother) {
            return delegate.documentTypes(mother);
        }

        @Override
        public int handoffs(String mother) {
            return delegate.handoffs(mother);
        }

        @Override
        public String handoffLedger(String mother, String process) {
            return delegate.handoffLedger(mother, process);
        }

        @Override
        public boolean hasRequest(String requestId) {
            return delegate.hasRequest(requestId);
        }

        @Override
        public World withProbes(CommandResultProbe resultProbe, HandoffSaveProbe handoffProbe) {
            return delegate.withProbes(resultProbe, handoffProbe);
        }
    }
}
