package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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
                1L, "张三", "PT", "铂金戒", new BigDecimal("1"), new BigDecimal("8"),
                "改镶嵌", "2026-12-01", "镶嵌", "客户改了要求"), admin);
        assertThat(corrected.requirement()).isEqualTo("改镶嵌");
        assertThat(documents.findChildWorkOrderByCode("ZD-1").orElseThrow().materialCode()).isEqualTo("PT");
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
        assertThat(documents.findChildWorkOrderByCode("ZD-1").orElseThrow().materialCode()).isEqualTo("PT");
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
    void 已领取的打印任务仍可改份数和工位() {
        routing.insertBatch(batch());
        PrintTargetJob leased = routing.claim("A", "lease", "now").orElseThrow();
        MaintainedOrder updated = service.correctPrintTarget(leased.id(), new OrderMaintenanceCommands.PrintCorrection(
                3, "B", "Q2", "补打两份"), admin);
        assertThat(updated.copies()).isEqualTo(3);
        assertThat(updated.stationId()).isEqualTo("B");
        assertThat(routing.jobAny(leased.id()).orElseThrow().targetQueueId()).isEqualTo("Q2");
        assertThat(routing.jobAny(leased.id()).orElseThrow().status()).isEqualTo(PrintJobStatus.LEASED);
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
