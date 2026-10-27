package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderRequirementVersion;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryOrderRequirementRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChildRequirementServiceTest {

    private InMemoryBusinessDocumentRepository documents;
    private InMemoryOrderRequirementRepository requirements;
    private ChildRequirementService service;
    private AuthUserAccount operator;

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        requirements = new InMemoryOrderRequirementRepository();
        documents.saveMotherOrder(mother());
        documents.saveChildWorkOrder(child("内壁刻字"));
        service = new ChildRequirementService(
                documents,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()),
                null,
                requirements
        );
        operator = account("收货员", DataScope.ALL, List.of());
    }

    @Test
    void savesChildRequirementWithoutOrderChangeAndReplaysTheSameRequest() {
        var first = service.edit("ZD-1", "外圈刻字", "req-child-0001", operator);
        var second = service.edit("ZD-1", "外圈刻字", "req-child-0001", operator);
        assertThat(first.processingRequirement()).isEqualTo("外圈刻字");
        assertThat(second.code()).isEqualTo("ZD-1");
        assertThat(documents.findChildWorkOrderByCode("ZD-1").orElseThrow().processingRequirement()).isEqualTo("外圈刻字");
        assertThat(documents.findChildWorkOrderByCode("ZD-1").orElseThrow().dueDate()).isEqualTo("2026-10-20");
        assertThat(documents.listOrderAdjustments("WORK_ORDER", "ZD-1")).hasSize(1);
        assertThat(documents.listOrderAdjustments("WORK_ORDER", "ZD-1").get(0).reason()).isEqualTo(ChildRequirementService.REASON);
        OrderRequirementVersion version = requirements.current("MD-1", "ZD-1").orElseThrow();
        assertThat(version.requirementText()).isEqualTo("外圈刻字");
        assertThat(version.dueDate()).isEqualTo("2026-10-20");
        assertThat(version.versionNo()).isEqualTo(1);

        AuthUserAccount other = account("另一人", DataScope.ALL, List.of());
        assertThatThrownBy(() -> service.edit("ZD-1", "外圈刻字", "req-child-0001", other))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("跨账号");
        assertThatThrownBy(() -> service.edit("ZD-1", "改为另一要求", "req-child-0001", operator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不一致");
        assertThat(documents.listOrderAdjustments("WORK_ORDER", "ZD-1")).hasSize(1);
    }

    @Test
    void keepsDueAndRouteFromEffectiveVersionAndRejectsUnchangedOrMissingChild() {
        requirements.saveVersion(new OrderRequirementVersion(
                "REQ-M", "MD-1", "", 3, "母单旧要求", "2026-11-01", "雕刻、抛光", "CHG-1", "2026-10-11"
        ), null);
        var saved = service.edit("ZD-1", "只改子单文字", "req-child-0002", operator);
        OrderRequirementVersion version = requirements.current("MD-1", "ZD-1").orElseThrow();
        assertThat(saved.processingRequirement()).isEqualTo("只改子单文字");
        assertThat(version.requirementText()).isEqualTo("只改子单文字");
        assertThat(version.dueDate()).isEqualTo("2026-11-01");
        assertThat(version.routeText()).isEqualTo("雕刻、抛光");
        assertThat(version.versionNo()).isEqualTo(1);
        assertThat(requirements.current("MD-1", "").orElseThrow().requirementText()).isEqualTo("母单旧要求");

        assertThatThrownBy(() -> service.edit("ZD-1", "只改子单文字", "req-child-0003", operator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有变化");
        assertThatThrownBy(() -> service.edit("MD-1", "母单不能走这里", "req-child-0004", operator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子单不存在");
        assertThatThrownBy(() -> service.edit("ZD-1", "  ", "req-child-0005", operator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请填写加工要求");
    }

    @Test
    void otherCustomerCannotEdit() {
        BusinessDataAccess access = new BusinessDataAccess(documents, new StockLedgerService(new InMemoryStockLedgerRepository()));
        ChildRequirementService scoped = new ChildRequirementService(
                documents, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), access, requirements);
        AuthUserAccount outsider = account("范围员", DataScope.CUSTOMER_RANGE, List.of("999"));
        assertThatThrownBy(() -> scoped.edit("ZD-1", "越权要求", "req-child-0006", outsider))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(documents.findChildWorkOrderByCode("ZD-1").orElseThrow().processingRequirement()).isEqualTo("内壁刻字");
    }

    private static ChildWorkOrderRecord child(String requirement) {
        return new ChildWorkOrderRecord("ZD-1", "MD-1", 101L, "真实客户", requirement, "2026-10-20",
                "MAT", "戒托", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW", "雕刻、镶嵌、抛光", "verify", "ledger", "开单员", "2026-10-10");
    }

    private static MotherOrderRecord mother() {
        return new MotherOrderRecord("MD-1", 101L, 101L, "MAT", "2026-10-10", "", "SF-MD-1", "", "收货员", "拆包员", "戒托",
                "CUSTOMER", true, "RAW", BigDecimal.TEN, "件", BigDecimal.TEN, "RAW_WAITING", "", "", null, "", "开单员", "", "",
                "INBOUND_CONFIRMED", "2026-10-10", "内壁刻字", "2026-10-20", "真实客户");
    }

    private static AuthUserAccount account(String operator, DataScope scope, List<String> customers) {
        return new AuthUserAccount(
                operator, "hash", operator, RoleCode.RECEIVER, scope, true, true,
                Instant.now(), 0, null, customers, false, false, List.of()
        );
    }
}
