package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.ProcessAvailableQuantity;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.CoverDuties;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessAvailableQuantityTest {

    @Test
    void 首道按袋份额可办理且查询不改袋和份额() {
        Fixture fixture = fixture(new BigDecimal("500"));
        int version = fixture.bag().version();
        int portions = fixture.portions().size();

        ProcessAvailableQuantity result = fixture.query().lookup(fixture.bag().id(), operator());

        assertThat(result.actionable()).isTrue();
        assertThat(result.stepId()).isEqualTo("CUT");
        assertThat(result.stepName()).isEqualTo("切料");
        assertThat(result.availableQuantity()).isEqualByComparingTo("500");
        assertThat(result.nextStepId()).isEqualTo("BLANK");
        assertThat(result.nextStepName()).isEqualTo("取胚");
        assertThat(result.blockingReasons()).isEmpty();
        assertThat(fixture.bags().find(fixture.bag().id()).orElseThrow().version()).isEqualTo(version);
        assertThat(fixture.bags().find(fixture.bag().id()).orElseThrow().weight().state())
                .isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(fixture.portions().size()).isEqualTo(portions);
    }

    @Test
    void 上道未到时不用原袋数量补齐() {
        Fixture fixture = fixture(new BigDecimal("500"));
        fixture.portions().save(new NodePortionBalance(fixture.bag().id(), "CUT", new BigDecimal("500"), new BigDecimal("500")), null);

        ProcessAvailableQuantity blocked = fixture.query().lookup(fixture.bag().id(), operator());

        assertThat(blocked.availableQuantity()).isEqualByComparingTo("0");
        assertThat(blocked.availableQuantity()).isNotEqualByComparingTo(fixture.bag().quantity());
        assertThat(blocked.stepId()).isEqualTo("BLANK");
        assertThat(blocked.nextStepId()).isEqualTo("DRILL");
        assertThat(blocked.blockingReasons()).containsExactly("上道未到，不能用原袋数量补齐");
        assertThat(blocked.actionable()).isFalse();

        fixture.portions().save(new NodePortionBalance(fixture.bag().id(), "BLANK", new BigDecimal("480"), BigDecimal.ZERO), null);
        ProcessAvailableQuantity arrived = fixture.query().lookup(fixture.bag().id(), operator());
        assertThat(arrived.actionable()).isTrue();
        assertThat(arrived.stepId()).isEqualTo("BLANK");
        assertThat(arrived.availableQuantity()).isEqualByComparingTo("480");
        assertThat(arrived.availableQuantity()).isNotEqualByComparingTo("500");
        assertThat(arrived.nextStepId()).isEqualTo("DRILL");
    }

    @Test
    void 无报工权限仍返回可处理量并给出阻断原因() {
        Fixture fixture = fixture(new BigDecimal("120"));
        OperatorContext manager = new OperatorContext(
                "业务", RoleCode.BUSINESS_MANAGER, DataScope.ALL, false, "", List.of(), List.of());
        OperatorContext reporter = new OperatorContext(
                "业务", RoleCode.BUSINESS_MANAGER, DataScope.ALL, false, "", List.of(), List.of(CoverDuties.REPORT));
        OperatorContext inspector = new OperatorContext("质检", RoleCode.QUALITY_INSPECTOR, DataScope.ALL);

        ProcessAvailableQuantity denied = fixture.query().lookup(fixture.bag().id(), manager);
        ProcessAvailableQuantity allowed = fixture.query().lookup(fixture.bag().id(), reporter);
        ProcessAvailableQuantity otherPost = fixture.query().lookup(fixture.bag().id(), inspector);

        assertThat(denied.availableQuantity()).isEqualByComparingTo("120");
        assertThat(denied.blockingReasons()).containsExactly("缺少生产报工权限");
        assertThat(denied.actionable()).isFalse();
        assertThat(allowed.actionable()).isTrue();
        assertThat(allowed.availableQuantity()).isEqualByComparingTo("120");
        assertThat(otherPost.blockingReasons()).containsExactly("缺少生产报工权限");
        assertThat(fixture.bags().find(fixture.bag().id()).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void 关闭父袋未确认路线和多道待处理都不能办理() {
        Fixture fixture = fixture(new BigDecimal("500"));
        ActivityBag parent = fixture.bag();
        fixture.bags().save(new ActivityBag(
                "BAG-LEAF", parent.targetCode(), parent.materialCode(), parent.requirementVersion(),
                parent.sourceKey(), parent.id(), false, new BigDecimal("120"),
                StockMeasurement.known(new BigDecimal("7.260")), 1), 0, null);
        fixture.service().close(parent.id(), null);

        ProcessAvailableQuantity closed = fixture.query().lookup(parent.id(), operator());
        ProcessAvailableQuantity leaf = fixture.query().lookup("BAG-LEAF", operator());

        assertThat(closed.availableQuantity()).isEqualByComparingTo("0");
        assertThat(closed.blockingReasons()).contains("已关闭的父袋不计入可办理量");
        assertThat(leaf.availableQuantity()).isEqualByComparingTo("120");
        assertThat(leaf.actionable()).isTrue();
        assertThat(fixture.bags().find("BAG-LEAF").orElseThrow().weight().value()).isEqualByComparingTo("7.260");

        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        routes.insert(new RouteSnapshot("RS-DRAFT", BusinessKind.PROCESSING, parent.targetCode(), 2,
                List.of("CUT", "BLANK"), false), null);
        ProcessAvailabilityQuery draftOnly = new ProcessAvailabilityQuery(
                fixture.bags(), routes, fixture.portions(), new PermissionGuard());
        assertThat(draftOnly.lookup("BAG-LEAF", operator()).blockingReasons()).containsExactly("路线尚未确认");
        assertThat(draftOnly.lookup("BAG-LEAF", operator()).availableQuantity()).isEqualByComparingTo("0");

        fixture.portions().save(new NodePortionBalance(parent.id(), "CUT", new BigDecimal("20"), BigDecimal.ZERO), null);
        fixture.portions().save(new NodePortionBalance(parent.id(), "BLANK", new BigDecimal("20"), BigDecimal.ZERO), null);
        ActivityBag reopened = new ActivityBag(
                parent.id(), parent.targetCode(), parent.materialCode(), parent.requirementVersion(),
                parent.sourceKey(), "", false, parent.quantity(), parent.weight(), 3);
        fixture.bags().save(reopened, 2, null);
        ProcessAvailableQuantity split = fixture.query().lookup(parent.id(), operator());
        assertThat(split.availableQuantity()).isEqualByComparingTo("0");
        assertThat(split.blockingReasons()).contains("同一袋不能同时有多道待处理数量");
    }

    @Test
    void 袋不存在时拒绝且不制造份额() {
        Fixture fixture = fixture(new BigDecimal("1"));
        assertThatThrownBy(() -> fixture.query().lookup("BAG-MISSING", operator()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("袋不存在");
        assertThat(fixture.portions().size()).isZero();
    }

    private static OperatorContext operator() {
        return new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL);
    }

    private static Fixture fixture(BigDecimal quantity) {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        ActivityBagService service = new ActivityBagService(bags);
        ActivityBag bag = service.open(
                "MD-20261030-0101", "牙骨", "REQ-1", "LINE-1", quantity, StockMeasurement.unknown(), null);
        routes.insert(new RouteSnapshot(
                "RS-1", BusinessKind.PROCESSING, bag.targetCode(), 1, List.of("CUT", "BLANK", "DRILL"), true), null);
        ProcessAvailabilityQuery query = new ProcessAvailabilityQuery(bags, routes, portions, new PermissionGuard());
        return new Fixture(bags, portions, service, bag, query);
    }

    private record Fixture(
            InMemoryActivityBagRepository bags,
            InMemoryNodePortionReader portions,
            ActivityBagService service,
            ActivityBag bag,
            ProcessAvailabilityQuery query
    ) {
    }
}
