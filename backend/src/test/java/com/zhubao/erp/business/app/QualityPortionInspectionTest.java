package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryPortionInspectionRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityPortionInspectionTest {

    @Test
    void 同一份多条不合格不重复计数且不覆盖其他袋() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryPortionInspectionRepository inspections = new InMemoryPortionInspectionRepository();
        ActivityBagService service = new ActivityBagService(bags);
        ActivityBag first = service.open("MD-20261030-0501", "牙骨", "REQ-9", "LINE-1",
                new BigDecimal("10"), StockMeasurement.unknown(), null);
        ActivityBag second = service.open("MD-20261030-0501", "牙骨", "REQ-9", "LINE-2",
                new BigDecimal("8"), StockMeasurement.unknown(), null);
        PortionInspectionService inspectionsService = new PortionInspectionService(
                bags, inspections, new PermissionGuard(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        OperatorContext inspector = new OperatorContext("质检", RoleCode.QUALITY_INSPECTOR, DataScope.ALL);
        PortionInspection recorded = inspectionsService.record(
                "qc-c05-01", first.id(), first.version(), "REQ-9",
                BigDecimal.ZERO, new BigDecimal("5"), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of("孔径", "色差", "裂纹"), inspector);
        PortionInspection again = inspectionsService.record(
                "qc-c05-01", first.id(), first.version(), "REQ-9",
                BigDecimal.ZERO, new BigDecimal("5"), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of("孔径", "色差", "裂纹"), inspector);

        assertThat(again.inspectionId()).isEqualTo(recorded.inspectionId());
        assertThat(recorded.counted()).isEqualByComparingTo("5");
        assertThat(recorded.failedClauses()).hasSize(3);
        assertThat(recorded.requirementVersion()).isEqualTo("REQ-9");
        assertThat(inspections.listByBag(second.id())).isEmpty();
        assertThat(inspections.listByBag(first.id())).hasSize(1);
        assertThatThrownBy(() -> inspectionsService.record(
                "qc-c05-02", first.id(), first.version(), "REQ-OLD",
                new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessage("质量检查必须使用袋子上的要求版本");
        assertThatThrownBy(() -> inspectionsService.record(
                "qc-c05-03", first.id(), first.version(), "REQ-9",
                BigDecimal.ZERO, new BigDecimal("5"), BigDecimal.ZERO, BigDecimal.ZERO, List.of("孔径"),
                new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL)))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
