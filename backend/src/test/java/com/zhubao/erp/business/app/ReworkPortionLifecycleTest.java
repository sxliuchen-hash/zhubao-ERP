package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.ReworkTask;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryPortionInspectionRepository;
import com.zhubao.erp.business.infra.InMemoryReworkTaskRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReworkPortionLifecycleTest {

    @Test
    void 复检只作用于返工份额且不新建母单() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryPortionInspectionRepository inspections = new InMemoryPortionInspectionRepository();
        InMemoryReworkTaskRepository tasks = new InMemoryReworkTaskRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        PermissionGuard guard = new PermissionGuard();
        ActivityBagService bagService = new ActivityBagService(bags);
        PortionInspectionService inspectionService = new PortionInspectionService(bags, inspections, guard, unit);
        ReworkPortionService rework = new ReworkPortionService(
                bags, bagService, inspections, inspectionService, tasks, guard, unit);
        OperatorContext inspector = new OperatorContext("质检", RoleCode.QUALITY_INSPECTOR, DataScope.ALL);
        ActivityBag source = bagService.open("MD-20261030-0701", "牙骨", "REQ-3", "LINE-1",
                new BigDecimal("10"), StockMeasurement.unknown(), null);
        inspectionService.record("qc-c07-01", source.id(), source.version(), "REQ-3",
                new BigDecimal("6"), new BigDecimal("4"), BigDecimal.ZERO, BigDecimal.ZERO, java.util.List.of("裂纹"), inspector);

        ReworkTask opened = rework.open("rw-c07-01", source.id(), source.version(), 1, inspector);
        ReworkTask replay = rework.open("rw-c07-01", source.id(), source.version(), 1, inspector);
        assertThat(replay.reworkBagId()).isEqualTo(opened.reworkBagId());
        assertThat(opened.targetCode()).isEqualTo("MD-20261030-0701");
        assertThat(opened.requirementVersion()).isEqualTo("REQ-3");
        assertThat(opened.round()).isEqualTo(1);
        assertThat(opened.quantity()).isEqualByComparingTo("4");
        assertThat(bags.find(source.id()).orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(bags.find(source.id()).orElseThrow().closed()).isTrue();
        ActivityBag reworkBag = bags.find(opened.reworkBagId()).orElseThrow();
        assertThat(reworkBag.quantity()).isEqualByComparingTo("4");
        assertThat(reworkBag.targetCode()).isEqualTo(source.targetCode());

        PortionInspection recheck = rework.reinspect("qc-c07-02", reworkBag.id(), reworkBag.version(),
                new BigDecimal("4"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, inspector);
        assertThat(recheck.counted()).isEqualByComparingTo("4");
        assertThat(inspections.listByBag(reworkBag.id())).hasSize(1);
        ActivityBag remainder = bagService.activeLeaves(source.targetCode()).stream()
                .filter(bag -> !bag.id().equals(reworkBag.id())).findFirst().orElseThrow();
        assertThatThrownBy(() -> rework.reinspect("qc-c07-03", remainder.id(), remainder.version(),
                new BigDecimal("6"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessage("复检只能作用于返工份额");
        assertThat(inspections.listByBag(remainder.id())).isEmpty();
    }
}
