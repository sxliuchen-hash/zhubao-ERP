package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderAdjustment;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.PrintTargetJob;
import com.zhubao.erp.label.infra.InMemoryPrintJobRepository;
import com.zhubao.erp.label.infra.InMemoryPrintRoutingRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderMaintenanceServiceTest {
    private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
    private final InMemoryPrintJobRepository printJobs = new InMemoryPrintJobRepository();
    private final InMemoryPrintRoutingRepository routing = new InMemoryPrintRoutingRepository();
    private final OrderMaintenanceService service = new OrderMaintenanceService(documents, printJobs, routing, new PermissionGuard());
    private final OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
    private final OperatorContext keeper = new OperatorContext("库管员", RoleCode.WAREHOUSE_KEEPER, DataScope.TEAM);

    @Test
    void 打开单据时同内容重复打印不逐笔展开() {
        documents.saveMotherOrder(mother());
        routing.insertBatch(targetBatch("B-1", "R-1", List.of(target("J-1", "B-1", "A", "Q1", 1, "2026-10-16T12:00")),
                sticker("刻字", 1), "2026-10-16T12:00"));
        routing.insertBatch(targetBatch("B-2", "R-2", List.of(
                target("J-2", "B-2", "A", "Q1", 2, "2026-10-16T13:00"),
                target("J-2b", "B-2", "B", "Q2", 2, "2026-10-16T13:00")),
                sticker("刻字", 2), "2026-10-16T13:00"));
        routing.insertBatch(targetBatch("B-3", "R-3", List.of(target("J-3", "B-3", "A", "Q1", 1, "2026-10-16T14:00")),
                sticker("改刻名", 1), "2026-10-16T14:00"));

        assertThat(service.search("MD-1", admin).stream()
                .filter(row -> "PRINT_TARGET".equals(row.targetType()))
                .map(MaintainedOrder::code))
                .containsExactlyInAnyOrder("J-2", "J-2b", "J-3");
        assertThat(service.search("J-1", admin).stream()
                .filter(row -> "PRINT_TARGET".equals(row.targetType()))
                .map(MaintainedOrder::code))
                .containsExactly("J-1");
    }

    @Test
    void 可按日期客户和建单人列出未回收母单() {
        documents.saveMotherOrder(mother());
        documents.saveMotherOrder(new MotherOrderRecord("MD-2", 2L, null, "AG", "2026-09-01", "", "", "", "", "",
                "手链", "CUSTOMER", true, "WH", new BigDecimal("1"), "件", new BigDecimal("3"),
                "RAW_WAITING", "", null, null, null, "王强", "", "", "OPEN", "2026-09-01 08:00:00",
                "", "2026-09-10", "李四"));
        documents.saveMotherOrder(new MotherOrderRecord("MD-3", 1L, null, "AU", "2026-10-20", "", "", "", "", "",
                "吊坠", "CUSTOMER", true, "WH", new BigDecimal("1"), "件", new BigDecimal("1"),
                "RAW_WAITING", "", null, null, null, "收货员", "", "", "OPEN", "2026-10-20T09:00",
                "", "2026-11-01", "张三"));
        service.recycle(new OrderMaintenanceCommands.RecycleCommand("MOTHER", "MD-3", "测试单作废"), admin);

        MaintainedMotherBrowse october = service.listMothers("2026-10-01", "2026-10-31", "张", "", admin);
        assertThat(october.items()).extracting(MaintainedMotherSummary::code).containsExactly("MD-1");
        assertThat(october.total()).isEqualTo(1);
        assertThat(october.truncated()).isFalse();
        assertThat(october.items().get(0).orderDate()).isEqualTo("2026-10-16");

        assertThat(service.listMothers("", "", "", "王强", admin).items())
                .extracting(MaintainedMotherSummary::code).containsExactly("MD-2");
        assertThat(service.listMothers("", "", "", "", admin).items())
                .extracting(MaintainedMotherSummary::code).containsExactly("MD-1", "MD-2");
        assertThatThrownBy(() -> service.listMothers("2026/10/01", "", "", "", admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("年-月-日");
        assertThatThrownBy(() -> service.listMothers("2026-11-01", "2026-10-01", "", "", admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
        assertThatThrownBy(() -> service.listMothers("", "", "", "", keeper))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void 非管理员不能查找或回收() {
        documents.saveMotherOrder(mother());
        assertThatThrownBy(() -> service.search("MD-1", keeper)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.recycle(new OrderMaintenanceCommands.RecycleCommand("MOTHER", "MD-1", "录错"), keeper))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void 已拆出的子单仍可改正并且回收后别人看不到() {
        documents.saveMotherOrder(mother());
        documents.saveChildWorkOrder(child());
        routing.insertBatch(batch());
        printJobs.save(legacyJob());

        MaintainedOrder corrected = service.correctChild("ZD-1", new OrderMaintenanceCommands.ChildCorrection(
                1L, "张三", "AU", "戒指", new BigDecimal("2"), new BigDecimal("10"),
                "改镶嵌", "2026-12-01", "抛光", "客户改了要求"), admin);
        assertThat(corrected.requirement()).isEqualTo("改镶嵌");
        assertThat(documents.findChildWorkOrderByCode("ZD-1").orElseThrow().materialCode()).isEqualTo("AU");
        assertThat(corrected.adjustments()).anyMatch(line -> line.contains("客户改了要求"));

        service.recycle(new OrderMaintenanceCommands.RecycleCommand("MOTHER", "MD-1", "整单录错"), admin);

        assertThat(documents.findMotherOrderByCode("MD-1")).isEmpty();
        assertThat(documents.listMotherOrders()).isEmpty();
        assertThat(documents.findChildWorkOrderByCode("ZD-1")).isEmpty();
        assertThat(documents.listChildWorkOrders("MD-1")).isEmpty();
        assertThat(routing.claim("A", "lease", "now")).isEmpty();
        assertThat(printJobs.claim("A", "lease", "now")).isEmpty();
        assertThat(service.recycleBin(admin)).extracting(MaintainedOrder::code).contains("MD-1", "ZD-1", "J-1", "P-1");

        service.restore(new OrderMaintenanceCommands.RestoreCommand("MOTHER", "MD-1"), admin);
        assertThat(documents.findMotherOrderByCode("MD-1")).isPresent();
        assertThat(documents.findChildWorkOrderByCode("ZD-1").orElseThrow().materialCode()).isEqualTo("AU");
        assertThat(routing.claim("A", "lease", "now").orElseThrow().id()).isEqualTo("J-1");
    }

    @Test
    void 单独回收子单时母单仍可见且须先恢复母单才能恢复子单() {
        documents.saveMotherOrder(mother());
        documents.saveChildWorkOrder(child());
        routing.insertBatch(batch());

        service.recycle(new OrderMaintenanceCommands.RecycleCommand("WORK_ORDER", "ZD-1", "这张子单作废"), admin);
        assertThat(documents.findMotherOrderByCode("MD-1")).isPresent();
        assertThat(documents.findChildWorkOrderByCode("ZD-1")).isEmpty();
        assertThat(routing.claim("A", "lease", "now")).isEmpty();

        service.restore(new OrderMaintenanceCommands.RestoreCommand("WORK_ORDER", "ZD-1"), admin);
        assertThat(documents.findChildWorkOrderByCode("ZD-1")).isPresent();
        assertThat(routing.job("J-1")).isPresent();

        service.recycle(new OrderMaintenanceCommands.RecycleCommand("MOTHER", "MD-1", "整单作废"), admin);
        assertThatThrownBy(() -> service.restore(new OrderMaintenanceCommands.RestoreCommand("WORK_ORDER", "ZD-1"), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先恢复母单");
    }

    @Test
    void 回收中途失败撤销母子单及打印标记并允许同请求重试() {
        documents.saveMotherOrder(mother());
        documents.saveChildWorkOrder(child());
        printJobs.save(legacyJob());
        routing.insertBatch(batch());
        AtomicBoolean fail = new AtomicBoolean(true);
        InMemoryPrintRoutingRepository failingRouting = new InMemoryPrintRoutingRepository() {
            @Override
            public synchronized int recycleByBusinessCode(String businessCode, String operator, String at,
                                                           String batchId, String reason, WorkSession session) {
                if (fail.getAndSet(false)) throw new IllegalStateException("目标任务回收失败");
                return super.recycleByBusinessCode(businessCode, operator, at, batchId, reason, session);
            }
        };
        failingRouting.insertBatch(batch());
        OrderMaintenanceService atomic = new OrderMaintenanceService(documents, printJobs, failingRouting,
                new PermissionGuard(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        var command = new OrderMaintenanceCommands.RecycleCommand("MOTHER", "MD-1", "整单核对", "req-recycle-mother");
        assertThatThrownBy(() -> atomic.recycle(command, admin)).isInstanceOf(IllegalStateException.class);
        assertThat(documents.motherRecycle("MD-1")).isEmpty();
        assertThat(documents.childRecycle("ZD-1")).isEmpty();
        assertThat(printJobs.recycleMark("P-1")).isEmpty();
        assertThat(failingRouting.jobRecycle("J-1")).isEmpty();

        atomic.recycle(command, admin);
        atomic.recycle(command, admin);
        assertThat(documents.motherRecycle("MD-1")).isPresent();
        assertThat(documents.childRecycle("ZD-1")).isPresent();
        assertThat(printJobs.recycleMark("P-1")).isPresent();
        assertThat(failingRouting.jobRecycle("J-1")).isPresent();
        assertThatThrownBy(() -> atomic.recycle(new OrderMaintenanceCommands.RecycleCommand(
                "MOTHER", "MD-1", "换一个原因", "req-recycle-mother"), admin))
                .isInstanceOf(BusinessException.class);

        var restore = new OrderMaintenanceCommands.RestoreCommand("MOTHER", "MD-1", "req-restore-mother");
        atomic.restore(restore, admin);
        atomic.restore(restore, admin);
        assertThat(documents.motherRecycle("MD-1")).isEmpty();
        assertThat(documents.childRecycle("ZD-1")).isEmpty();
        assertThat(printJobs.recycleMark("P-1")).isEmpty();
        assertThat(failingRouting.jobRecycle("J-1")).isEmpty();
    }

    @Test
    void 母单已回收时不能单独恢复子单名下的打印任务() {
        documents.saveMotherOrder(mother());
        documents.saveChildWorkOrder(child());
        documents.recycleMother("MD-1", "管理员", "2026-10-19T12:00", "batch-mother", "整单作废");
        routing.insertBatch(batch());
        routing.recycleJob("J-1", "管理员", "2026-10-19T12:00", "batch-print", "先收打印");
        OrderMaintenanceService atomic = replayService();
        assertThatThrownBy(() -> atomic.restore(new OrderMaintenanceCommands.RestoreCommand(
                "PRINT_TARGET", "J-1", "req-restore-child-print"), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先恢复对应单据");
        assertThat(routing.jobRecycle("J-1")).isPresent();
        assertThat(documents.childRecycle("ZD-1")).isEmpty();
        assertThat(documents.motherRecycle("MD-1")).isPresent();
    }

    @Test
    void 单独打印任务回收恢复按请求回放且失败撤销标记() {
        printJobs.save(legacyJob());
        routing.insertBatch(batch());
        AtomicBoolean fail = new AtomicBoolean(true);
        OrderMaintenanceService atomic = new OrderMaintenanceService(documents, printJobs, routing,
                new PermissionGuard(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    if (fail.getAndSet(false)) throw new IllegalStateException("请求结果写入失败");
                }));
        var recycle = new OrderMaintenanceCommands.RecycleCommand("PRINT_JOB", "P-1", "核纸后作废", "req-print-recycle");
        assertThatThrownBy(() -> atomic.recycle(recycle, admin)).isInstanceOf(IllegalStateException.class);
        assertThat(printJobs.recycleMark("P-1")).isEmpty();
        atomic.recycle(recycle, admin);
        atomic.recycle(recycle, admin);
        assertThat(printJobs.recycleMark("P-1")).isPresent();
        var restore = new OrderMaintenanceCommands.RestoreCommand("PRINT_JOB", "P-1", "req-print-restore");
        atomic.restore(restore, admin);
        atomic.restore(restore, admin);
        assertThat(printJobs.recycleMark("P-1")).isEmpty();

        var targetRecycle = new OrderMaintenanceCommands.RecycleCommand("PRINT_TARGET", "J-1", "核纸后作废",
                "req-target-recycle");
        atomic.recycle(targetRecycle, admin);
        atomic.recycle(targetRecycle, admin);
        assertThat(routing.jobRecycle("J-1")).isPresent();
        var targetRestore = new OrderMaintenanceCommands.RestoreCommand("PRINT_TARGET", "J-1", "req-target-restore");
        atomic.restore(targetRestore, admin);
        atomic.restore(targetRestore, admin);
        assertThat(routing.jobRecycle("J-1")).isEmpty();
    }

    @Test
    void 已领取的目标任务拒绝直接改单() {
        routing.insertBatch(batch());
        PrintTargetJob leased = routing.claim("A", "lease", "now").orElseThrow();
        OrderMaintenanceService replay = replayService();
        String revision = replay.search(leased.id(), admin).stream()
                .filter(row -> "PRINT_TARGET".equals(row.targetType())).findFirst().orElseThrow().revision();
        assertThatThrownBy(() -> replay.correctPrintTarget(leased.id(), new OrderMaintenanceCommands.PrintCorrection(
                3, "B", "Q2", "补打两份", "req-print-leased", revision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已领取");
        assertThat(routing.jobAny(leased.id()).orElseThrow().status()).isEqualTo(PrintJobStatus.LEASED);
        assertThat(documents.listOrderAdjustments("PRINT_TARGET", leased.id())).isEmpty();
    }

    @Test
    void 未领取目标任务改单与审计同次提交且请求回放() {
        routing.insertBatch(batch());
        OrderMaintenanceService replay = replayService();
        String revision = replay.search("J-1", admin).stream()
                .filter(row -> "PRINT_TARGET".equals(row.targetType())).findFirst().orElseThrow().revision();
        assertThatThrownBy(() -> replay.correctPrintTarget("J-1", new OrderMaintenanceCommands.PrintCorrection(
                3, "B", "Q2", "改派", "req-print-reroute", revision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("打印批次改派");
        var command = new OrderMaintenanceCommands.PrintCorrection(3, "A", "Q1", "核对份数",
                "req-print-target-1", revision);
        replay.correctPrintTarget("J-1", command, admin);
        replay.correctPrintTarget("J-1", command, admin);
        assertThat(routing.jobAny("J-1").orElseThrow().copies()).isEqualTo(3);
        assertThat(documents.listOrderAdjustments("PRINT_TARGET", "J-1")).hasSize(1);
        assertThatThrownBy(() -> replay.correctPrintTarget("J-1", new OrderMaintenanceCommands.PrintCorrection(
                2, "A", "Q1", "旧页面", "req-print-target-2", revision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已被修改");
    }

    @Test
    void 旧打印任务仅能在未领取时改份数而不能改领取工位() {
        printJobs.save(legacyJob());
        OrderMaintenanceService replay = replayService();
        String revision = replay.search("P-1", admin).stream()
                .filter(row -> "PRINT_JOB".equals(row.targetType())).findFirst().orElseThrow().revision();
        assertThatThrownBy(() -> replay.correctPrintJob("P-1", new OrderMaintenanceCommands.PrintCorrection(
                2, "B", "", "改工位", "req-legacy-station", revision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("领取记录");
        assertThat(printJobs.findAny("P-1").orElseThrow().copies()).isEqualTo(1);
        replay.correctPrintJob("P-1", new OrderMaintenanceCommands.PrintCorrection(
                2, "", "", "份数核对", "req-legacy-copies", revision), admin);
        assertThat(printJobs.findAny("P-1").orElseThrow().copies()).isEqualTo(2);
        assertThat(documents.listOrderAdjustments("PRINT_JOB", "P-1")).hasSize(1);
    }

    @Test
    void 已领取已入队失败不确定和已取消都不能原地改份数() {
        for (PrintJobStatus status : List.of(PrintJobStatus.LEASED, PrintJobStatus.SPOOLED,
                PrintJobStatus.FAILED, PrintJobStatus.UNCERTAIN, PrintJobStatus.CANCELED)) {
            InMemoryPrintRoutingRepository targets = new InMemoryPrintRoutingRepository();
            String jobId = "J-" + status.name();
            targets.insertBatch(new PrintBatch("B-" + status.name(), "R-" + status.name(), "ZD-1", "WORK", 1,
                    "hash", "snapshot", "收货员", "2026-10-16T12:00", List.of(new PrintTargetJob(
                    jobId, "B-" + status.name(), "A", "Q1", 1, status, "", "", "", "", "", "",
                    "2026-10-16T12:00", "2026-10-16T12:00"))));
            OrderMaintenanceService service = new OrderMaintenanceService(documents, printJobs, targets,
                    new PermissionGuard(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
            String revision = service.search(jobId, admin).stream()
                    .filter(row -> "PRINT_TARGET".equals(row.targetType())).findFirst().orElseThrow().revision();
            assertThatThrownBy(() -> service.correctPrintTarget(jobId, new OrderMaintenanceCommands.PrintCorrection(
                    4, "A", "Q1", "改份数", "req-keep-" + status.name(), revision), admin))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不能直接改单");
            PrintTargetJob kept = targets.jobAny(jobId).orElseThrow();
            assertThat(kept.copies()).isEqualTo(1);
            assertThat(kept.status()).isEqualTo(status);
            assertThat(kept.targetStationId()).isEqualTo("A");
            assertThat(kept.targetQueueId()).isEqualTo("Q1");

            printJobs.save(new PrintJob("P-" + status.name(), "REQ-" + status.name(), "MD-1", "MOTHER", 1,
                    "snapshot", status, "", "", "", "", "", "收货员", "2026-10-16T12:00", "2026-10-16T12:00"));
            String legacyRevision = service.search("P-" + status.name(), admin).stream()
                    .filter(row -> "PRINT_JOB".equals(row.targetType())).findFirst().orElseThrow().revision();
            assertThatThrownBy(() -> service.correctPrintJob("P-" + status.name(),
                    new OrderMaintenanceCommands.PrintCorrection(4, "", "", "改份数",
                            "req-legacy-keep-" + status.name(), legacyRevision), admin))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不能直接改单");
            assertThat(printJobs.findAny("P-" + status.name()).orElseThrow().copies()).isEqualTo(1);
            assertThat(printJobs.findAny("P-" + status.name()).orElseThrow().status()).isEqualTo(status);
        }
    }

    @Test
    void 内存打印审计失败会撤销份数且原请求编号可重试() {
        AtomicBoolean failOnce = new AtomicBoolean(true);
        InMemoryBusinessDocumentRepository failingDocuments = new InMemoryBusinessDocumentRepository() {
            @Override
            public void saveOrderAdjustment(OrderAdjustment adjustment, WorkSession session) {
                if (failOnce.getAndSet(false)) throw new IllegalStateException("调整记录失败");
                super.saveOrderAdjustment(adjustment, session);
            }
        };
        InMemoryPrintRoutingRepository targets = new InMemoryPrintRoutingRepository();
        targets.insertBatch(batch());
        OrderMaintenanceService replay = new OrderMaintenanceService(failingDocuments,
                new InMemoryPrintJobRepository(), targets, new PermissionGuard(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        String revision = replay.search("J-1", admin).stream()
                .filter(row -> "PRINT_TARGET".equals(row.targetType())).findFirst().orElseThrow().revision();
        var command = new OrderMaintenanceCommands.PrintCorrection(2, "A", "Q1", "核对份数",
                "req-print-memory-rollback", revision);
        assertThatThrownBy(() -> replay.correctPrintTarget("J-1", command, admin))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("调整记录失败");
        assertThat(targets.jobAny("J-1").orElseThrow().copies()).isEqualTo(1);
        assertThat(failingDocuments.listOrderAdjustments("PRINT_TARGET", "J-1")).isEmpty();
        replay.correctPrintTarget("J-1", command, admin);
        assertThat(targets.jobAny("J-1").orElseThrow().copies()).isEqualTo(2);
    }

    @Test
    void 母单相同请求回放一次且旧页面和改内容请求被拒绝() {
        documents.saveMotherOrder(mother());
        OrderMaintenanceService replay = replayService();
        String revision = replay.search("MD-1", admin).get(0).revision();
        var command = motherCorrection("新货品", "req-maint-mother-001", revision);

        replay.correctMother("MD-1", command, admin);
        replay.correctMother("MD-1", command, admin);
        assertThat(documents.listOrderAdjustments("MOTHER", "MD-1")).hasSize(1);
        assertThatThrownBy(() -> replay.correctMother("MD-1",
                motherCorrection("另一货品", "req-maint-mother-001", revision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("内容不一致");
        assertThatThrownBy(() -> replay.correctMother("MD-1",
                motherCorrection("另一货品", "req-maint-mother-002", revision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已被修改");
    }

    @Test
    void 已入账母子单事实不能直接覆盖且失败请求不占号() {
        documents.saveMotherOrder(mother());
        documents.saveChildWorkOrder(child());
        OrderMaintenanceService replay = replayService();
        String motherRevision = replay.search("MD-1", admin).get(0).revision();
        String childRevision = replay.search("ZD-1", admin).stream()
                .filter(row -> "WORK_ORDER".equals(row.targetType())).findFirst().orElseThrow().revision();
        assertThat(replay.search("MD-1", admin).get(0).postedFactsLocked()).isTrue();

        var motherChange = new OrderMaintenanceCommands.MotherCorrection(null, "张三", "PT", "戒指",
                new BigDecimal("3"), new BigDecimal("10"), "抛光", "2026-11-01", "", "材质数量核对",
                "req-posted-mother", motherRevision);
        assertThatThrownBy(() -> replay.correctMother("MD-1", motherChange, admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("差额或冲正");
        assertThatThrownBy(() -> replay.correctMother("MD-1", new OrderMaintenanceCommands.MotherCorrection(
                2L, "张三", "AU", "戒指", new BigDecimal("2"), new BigDecimal("10"),
                "抛光", "2026-11-01", "", "客户核对", "req-posted-customer", motherRevision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("差额或冲正");
        assertThatThrownBy(() -> replay.correctMother("MD-1", new OrderMaintenanceCommands.MotherCorrection(
                null, "张三", "AU", "戒指", new BigDecimal("2"), new BigDecimal("11"),
                "抛光", "2026-11-01", "", "重量核对", "req-posted-weight", motherRevision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("差额或冲正");
        var childChange = new OrderMaintenanceCommands.ChildCorrection(null, "张三", "AU", "戒指",
                new BigDecimal("2"), new BigDecimal("10"), "抛光", "2026-11-01", "镶嵌", "工序核对",
                "req-posted-child", childRevision);
        assertThatThrownBy(() -> replay.correctChild("ZD-1", childChange, admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("差额或冲正");
        assertThat(documents.findMotherOrderAny("MD-1").orElseThrow().quantity()).isEqualByComparingTo("2");
        assertThat(documents.findChildWorkOrderAny("ZD-1").orElseThrow().processes()).isEqualTo("抛光");
        assertThat(documents.listOrderAdjustments("MOTHER", "MD-1")).isEmpty();
        assertThat(documents.listOrderAdjustments("WORK_ORDER", "ZD-1")).isEmpty();

        replay.correctMother("MD-1", new OrderMaintenanceCommands.MotherCorrection(null, "张三", "AU", "戒指",
                new BigDecimal("2"), new BigDecimal("10"), "修改要求", "2026-11-01", "", "核对描述",
                "req-posted-mother", motherRevision), admin);
        assertThat(documents.listOrderAdjustments("MOTHER", "MD-1")).hasSize(1);
    }

    @Test
    void 旧单缺失流水编号时仍按实际库存流水保护入账事实() {
        MotherOrderRecord original = mother();
        documents.saveMotherOrder(new MotherOrderRecord(original.code(), original.customerId(),
                original.ownerCustomerId(), original.materialCode(), original.inboundDate(),
                original.expressCompany(), original.courierNo(), original.senderName(), original.receiverName(),
                original.unpackSigner(), original.goodsName(), original.ownerType(), original.customerOwned(),
                original.warehouseCode(), original.quantity(), original.quantityUnit(), original.weight(),
                original.stockStatus(), "", original.sourceRegisterType(), original.sourceRegisterLineNo(),
                original.sourceRegisterSequence(), original.operator(), original.evidenceCode(), original.remark(),
                original.status(), original.createdAt(), original.processingRequirement(), original.dueDate(),
                original.customerName()));
        ChildWorkOrderRecord split = child();
        documents.saveChildWorkOrder(new ChildWorkOrderRecord(split.code(), split.motherOrderCode(), split.customerId(),
                split.customerName(), split.processingRequirement(), split.dueDate(), split.materialCode(),
                split.goodsName(), split.quantity(), split.quantityUnit(), split.weight(), split.warehouseCode(),
                split.processes(), split.verificationCode(), "", split.operator(), split.createdAt()));
        StockLedgerService stocks = new StockLedgerService(new InMemoryStockLedgerRepository());
        StockDimension raw = new StockDimension(OwnerType.CUSTOMER, 1L, 1L, "MD-1", null, "AU", "WH", StockStatus.RAW_WAITING);
        StockDimension wip = new StockDimension(OwnerType.CUSTOMER, 1L, 1L, "MD-1", "ZD-1", "AU", "WH", StockStatus.WIP);
        stocks.transfer(new StockTransferCommand("inbound_confirm", "MD-1", null, raw,
                new BigDecimal("2"), new BigDecimal("10"), "历史收货"));
        stocks.transfer(new StockTransferCommand("work_order_split", "ZD-1", raw, wip,
                new BigDecimal("2"), new BigDecimal("10"), "历史拆单"));
        OrderMaintenanceService guarded = new OrderMaintenanceService(documents, printJobs, routing,
                new PermissionGuard(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), stocks);
        MaintainedOrder motherView = guarded.search("MD-1", admin).get(0);
        MaintainedOrder childView = guarded.search("ZD-1", admin).stream()
                .filter(row -> "WORK_ORDER".equals(row.targetType())).findFirst().orElseThrow();
        assertThat(motherView.postedFactsLocked()).isTrue();
        assertThat(childView.postedFactsLocked()).isTrue();
        assertThatThrownBy(() -> guarded.correctMother("MD-1", new OrderMaintenanceCommands.MotherCorrection(
                null, "张三", "PT", "戒指", new BigDecimal("2"), new BigDecimal("10"), "抛光", "2026-11-01", "",
                "材质核对", "req-legacy-mother", motherView.revision()), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("差额或冲正");
        assertThatThrownBy(() -> guarded.correctChild("ZD-1", new OrderMaintenanceCommands.ChildCorrection(
                null, "张三", "AU", "戒指", new BigDecimal("2"), new BigDecimal("10"), "抛光", "2026-11-01", "镶嵌",
                "工序核对", "req-legacy-child", childView.revision()), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("差额或冲正");
    }

    @Test
    void 发货单号流水仍保护缺失流水编号的母单和子单() {
        MotherOrderRecord original = mother();
        documents.saveMotherOrder(new MotherOrderRecord(original.code(), original.customerId(),
                original.ownerCustomerId(), original.materialCode(), original.inboundDate(),
                original.expressCompany(), original.courierNo(), original.senderName(), original.receiverName(),
                original.unpackSigner(), original.goodsName(), original.ownerType(), original.customerOwned(),
                original.warehouseCode(), original.quantity(), original.quantityUnit(), original.weight(),
                original.stockStatus(), "", original.sourceRegisterType(), original.sourceRegisterLineNo(),
                original.sourceRegisterSequence(), original.operator(), original.evidenceCode(), original.remark(),
                original.status(), original.createdAt(), original.processingRequirement(), original.dueDate(),
                original.customerName()));
        ChildWorkOrderRecord split = child();
        documents.saveChildWorkOrder(new ChildWorkOrderRecord(split.code(), split.motherOrderCode(), split.customerId(),
                split.customerName(), split.processingRequirement(), split.dueDate(), split.materialCode(),
                split.goodsName(), split.quantity(), split.quantityUnit(), split.weight(), split.warehouseCode(),
                split.processes(), split.verificationCode(), "", split.operator(), split.createdAt()));
        StockLedgerService stocks = new StockLedgerService(new InMemoryStockLedgerRepository());
        StockDimension shipped = new StockDimension(OwnerType.CUSTOMER, 1L, 1L, "MD-1", "ZD-1", "AU", "WH",
                StockStatus.SHIPPED);
        stocks.transfer(new StockTransferCommand("outbound_shipment", "OUT-1", null, shipped,
                new BigDecimal("2"), new BigDecimal("10"), "历史发货"));
        OrderMaintenanceService guarded = new OrderMaintenanceService(documents, printJobs, routing,
                new PermissionGuard(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), stocks);
        MaintainedOrder motherView = guarded.search("MD-1", admin).get(0);
        MaintainedOrder childView = guarded.search("ZD-1", admin).stream()
                .filter(row -> "WORK_ORDER".equals(row.targetType())).findFirst().orElseThrow();
        assertThat(motherView.postedFactsLocked()).isTrue();
        assertThat(childView.postedFactsLocked()).isTrue();
    }

    @Test
    void 未拆单母单改正数量会冲正原收货并回放一次() {
        documents.saveMotherOrder(mother());
        InMemoryStockLedgerRepository repository = new InMemoryStockLedgerRepository();
        StockLedgerService stocks = new StockLedgerService(repository);
        StockDimension raw = new StockDimension(OwnerType.CUSTOMER, 1L, 1L, "MD-1", null, "AU", "WH", StockStatus.RAW_WAITING);
        stocks.transfer(new StockTransferCommand("inbound_confirm", "MD-1", null, raw,
                new BigDecimal("2"), new BigDecimal("10"), "收货"));
        MotherOrderRecord current = documents.findMotherOrderAny("MD-1").orElseThrow();
        documents.saveMotherOrder(new MotherOrderRecord(current.code(), current.customerId(), current.ownerCustomerId(),
                current.materialCode(), current.inboundDate(), current.expressCompany(), current.courierNo(),
                current.senderName(), current.receiverName(), current.unpackSigner(), current.goodsName(),
                current.ownerType(), current.customerOwned(), current.warehouseCode(), current.quantity(),
                current.quantityUnit(), current.weight(), current.stockStatus(), stocks.ledgers().get(0).code(),
                current.sourceRegisterType(), current.sourceRegisterLineNo(), current.sourceRegisterSequence(),
                current.operator(), current.evidenceCode(), current.remark(), current.status(), current.createdAt(),
                current.processingRequirement(), current.dueDate(), current.customerName()));
        OrderMaintenanceService service = new OrderMaintenanceService(documents, printJobs, routing, new PermissionGuard(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), stocks,
                new com.zhubao.erp.finance.infra.InMemoryFinanceRepository(null, null));
        String revision = service.search("MD-1", admin).get(0).revision();
        var command = new OrderMaintenanceCommands.MotherCorrection(null, "张三", "AU", "戒指",
                new BigDecimal("3"), new BigDecimal("10"), "抛光", "2026-11-01", "", "录错数量",
                "req-reverse-qty", revision);
        service.correctMother("MD-1", command, admin);
        service.correctMother("MD-1", command, admin);
        assertThat(documents.findMotherOrderAny("MD-1").orElseThrow().quantity()).isEqualByComparingTo("3");
        assertThat(repository.findBalance(raw).quantity()).isEqualByComparingTo("3");
        assertThat(stocks.ledgers()).extracting(com.zhubao.erp.inventory.domain.StockLedgerEntry::documentType)
                .containsExactly("inbound_confirm", "inbound_reversal", "inbound_confirm");
        assertThat(documents.listOrderAdjustments("MOTHER", "MD-1")).hasSize(1);
    }

    @Test
    void 已拆单母单不能按收货录入错误冲正() {
        documents.saveMotherOrder(mother());
        documents.saveChildWorkOrder(child());
        StockLedgerService stocks = new StockLedgerService(new InMemoryStockLedgerRepository());
        StockDimension raw = new StockDimension(OwnerType.CUSTOMER, 1L, 1L, "MD-1", null, "AU", "WH", StockStatus.RAW_WAITING);
        stocks.transfer(new StockTransferCommand("inbound_confirm", "MD-1", null, raw,
                new BigDecimal("2"), new BigDecimal("10"), "收货"));
        OrderMaintenanceService service = new OrderMaintenanceService(documents, printJobs, routing, new PermissionGuard(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), stocks);
        String revision = service.search("MD-1", admin).get(0).revision();
        assertThatThrownBy(() -> service.correctMother("MD-1", new OrderMaintenanceCommands.MotherCorrection(
                null, "张三", "AU", "戒指", new BigDecimal("3"), new BigDecimal("10"), "抛光", "2026-11-01", "",
                "录错数量", "req-reverse-child", revision), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能按收货录入错误冲正");
        assertThat(documents.findMotherOrderAny("MD-1").orElseThrow().quantity()).isEqualByComparingTo("2");
    }

    @Test
    void 并发子单改正只有一个旧页面快照可以提交() throws Exception {
        documents.saveMotherOrder(mother());
        documents.saveChildWorkOrder(child());
        OrderMaintenanceService replay = replayService();
        String revision = replay.search("ZD-1", admin).stream()
                .filter(row -> "WORK_ORDER".equals(row.targetType())).findFirst().orElseThrow().revision();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> correctChildAfterStart(replay, start,
                    childCorrection("改正甲", "req-maint-child-001", revision)));
            Future<Boolean> second = pool.submit(() -> correctChildAfterStart(replay, start,
                    childCorrection("改正乙", "req-maint-child-002", revision)));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(documents.listOrderAdjustments("WORK_ORDER", "ZD-1")).hasSize(1);
    }

    private boolean correctChildAfterStart(OrderMaintenanceService replay, CountDownLatch start,
                                           OrderMaintenanceCommands.ChildCorrection command) throws InterruptedException {
        start.await();
        try {
            replay.correctChild("ZD-1", command, admin);
            return true;
        } catch (BusinessException ex) {
            assertThat(ex).hasMessageContaining("已被修改");
            return false;
        }
    }

    private OrderMaintenanceService replayService() {
        return new OrderMaintenanceService(documents, printJobs, routing, new PermissionGuard(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
    }

    private OrderMaintenanceCommands.MotherCorrection motherCorrection(String goodsName, String requestId,
                                                                        String revision) {
        return new OrderMaintenanceCommands.MotherCorrection(null, "张三", "AU", "戒指",
                new BigDecimal("2"), new BigDecimal("10"), goodsName, "2026-11-01", "", "核对后改正",
                requestId, revision);
    }

    private OrderMaintenanceCommands.ChildCorrection childCorrection(String goodsName, String requestId,
                                                                      String revision) {
        return new OrderMaintenanceCommands.ChildCorrection(null, "张三", "AU", "戒指",
                new BigDecimal("2"), new BigDecimal("10"), goodsName, "2026-11-01", "抛光", "核对后改正",
                requestId, revision);
    }

    private MotherOrderRecord mother() {
        return new MotherOrderRecord("MD-1", 1L, null, "AU", "2026-10-16", "", "SF1", "", "", "",
                "戒指", "CUSTOMER", true, "WH", new BigDecimal("2"), "件", new BigDecimal("10"),
                "RAW_WAITING", "L1", null, null, null, "收货员", "", "", "OPEN", "2026-10-16T10:00",
                "抛光", "2026-11-01", "张三");
    }

    private ChildWorkOrderRecord child() {
        return new ChildWorkOrderRecord("ZD-1", "MD-1", 1L, "张三", "抛光", "2026-11-01", "AU", "戒指",
                new BigDecimal("2"), "件", new BigDecimal("10"), "WH", "抛光", "1234", "CL1", "收货员", "2026-10-16T11:00");
    }

    private PrintTargetJob target(String id, String batchId, String stationId, String queueId, int copies, String at) {
        return new PrintTargetJob(id, batchId, stationId, queueId, copies, PrintJobStatus.PENDING,
                "", "", "", "", "", "", at, at);
    }

    private PrintBatch targetBatch(String id, String requestId, List<PrintTargetJob> jobs, String snapshot, String at) {
        return new PrintBatch(id, requestId, "MD-1", "MOTHER", jobs.get(0).copies(), "hash", snapshot, "收货员", at, jobs);
    }

    private String sticker(String requirement, int copies) {
        return "{\"copies\":" + copies + ",\"qr\":\"JG|mother|MD-1|1|LI1\",\"fields\":[{\"name\":\"要求\",\"value\":\""
                + requirement + "\"}]}";
    }

    private PrintBatch batch() {
        PrintTargetJob job = new PrintTargetJob("J-1", "B-1", "A", "Q1", 1, PrintJobStatus.PENDING,
                "", "", "", "", "", "", "2026-10-16T12:00", "2026-10-16T12:00");
        return new PrintBatch("B-1", "R-1", "ZD-1", "WORK", 1, "hash", "snapshot", "收货员", "2026-10-16T12:00", List.of(job));
    }

    private PrintJob legacyJob() {
        return new PrintJob("P-1", "REQ-1", "MD-1", "MOTHER", 1, "snapshot", PrintJobStatus.PENDING,
                "", "", "", "", "", "收货员", "2026-10-16T12:00", "2026-10-16T12:00");
    }
}
