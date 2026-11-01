package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.PortionRelease;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.ShippableView;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBagWeightRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryPortionInspectionRepository;
import com.zhubao.erp.business.infra.InMemoryPortionReleaseRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityPortionReleaseTest {

    @Test
    void 重复放行不双计且未完成穿串外协或冻结不能当可发() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryPortionInspectionRepository inspections = new InMemoryPortionInspectionRepository();
        InMemoryPortionReleaseRepository releases = new InMemoryPortionReleaseRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        InMemoryBagWeightRepository weightRecords = new InMemoryBagWeightRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        ActivityBag bag = new ActivityBagService(bags).open(
                "MD-20261030-0601", "牙骨", "REQ-1", "LINE-1", new BigDecimal("10"), StockMeasurement.unknown(), null);
        routes.insert(new RouteSnapshot("RS-1", BusinessKind.PROCESSING, bag.targetCode(), 1,
                List.of("INSPECT", "STRING"), true), null);
        PermissionGuard guard = new PermissionGuard();
        PortionInspectionService inspectionService = new PortionInspectionService(bags, inspections, guard, unit);
        WeighingService weighing = new WeighingService(bags, weightRecords, unit);
        PortionReleaseService releasesService = new PortionReleaseService(
                bags, inspections, releases, routes, portions, weighing, guard, unit);
        OperatorContext inspector = new OperatorContext("质检", RoleCode.QUALITY_INSPECTOR, DataScope.ALL);
        inspectionService.record("qc-c06-01", bag.id(), bag.version(), "REQ-1",
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), inspector);
        PortionRelease first = releasesService.release("rel-c06-01", bag.id(), bag.version(), new BigDecimal("10"), inspector);
        PortionRelease replay = releasesService.release("rel-c06-01", bag.id(), bag.version(), new BigDecimal("10"), inspector);
        assertThat(replay.released()).isEqualByComparingTo(first.released()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> releasesService.release("rel-c06-02", bag.id(), bag.version(), new BigDecimal("10"), inspector))
                .isInstanceOf(BusinessException.class)
                .hasMessage("放行数量不能超过已检合格数量");
        assertThat(releases.find(bag.id()).orElseThrow().released()).isEqualByComparingTo("10");

        ShippableView unfinished = releasesService.shippable(bag.id(), PortionReleaseService.Location.IN_HOUSE);
        assertThat(unfinished.shippable()).isFalse();
        assertThat(unfinished.blockingReasons()).contains("穿串未完，不能仅凭质检合格发出");
        portions.save(new NodePortionBalance(bag.id(), "STRING", new BigDecimal("10"), new BigDecimal("10")), null);
        weighing.supplement("w-c06-01", bag.id(), bag.version(), new BigDecimal("1.200"), inspector);
        ActivityBag weighed = bags.find(bag.id()).orElseThrow();
        ShippableView ready = releasesService.shippable(weighed.id(), PortionReleaseService.Location.IN_HOUSE);
        assertThat(ready.shippable()).isTrue();
        assertThat(ready.quantity()).isEqualByComparingTo("10");
        ShippableView outside = releasesService.shippable(weighed.id(), PortionReleaseService.Location.OUTSOURCING);
        assertThat(outside.blockingReasons()).contains("外协未回，不能按厂内现货发出");
        releasesService.freeze("frz-c06-01", weighed.id(), weighed.version(), new BigDecimal("10"), inspector);
        ShippableView frozen = releasesService.shippable(weighed.id(), PortionReleaseService.Location.IN_HOUSE);
        assertThat(frozen.shippable()).isFalse();
        assertThat(frozen.blockingReasons()).contains("冻结份额不能发出");
        assertThatThrownBy(() -> releasesService.unfreeze("unfreeze-over",weighed.id(),weighed.version(),new BigDecimal("11"),inspector)).hasMessageContaining("不能超过");
        assertThatThrownBy(() -> releasesService.unfreeze("unfreeze-scale",weighed.id(),weighed.version(),new BigDecimal("0.0001"),inspector)).hasMessageContaining("三位");
        var thawed=releasesService.unfreeze("unfreeze-one",weighed.id(),weighed.version(),new BigDecimal("4"),inspector);
        assertThat(thawed.released()).isEqualByComparingTo("10");
        assertThat(thawed.frozen()).isEqualByComparingTo("6");
        assertThat(releasesService.unfreeze("unfreeze-one",weighed.id(),weighed.version(),new BigDecimal("4"),inspector).frozen()).isEqualByComparingTo("6");
        assertThat(releasesService.shippable(weighed.id(),PortionReleaseService.Location.IN_HOUSE).quantity()).isEqualByComparingTo("4");
    }
}
