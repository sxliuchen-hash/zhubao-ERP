package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRouteSnapshotTest {

    @Test
    void 三类路线各自保存且已确认快照不随模板身份混淆() {
        InMemoryRouteSnapshotRepository repository = new InMemoryRouteSnapshotRepository();
        RouteSnapshotService service = new RouteSnapshotService(repository);

        RouteSnapshot bead = service.capture(BusinessKind.PROCESSING, "MD-20261030-0001", "牙骨", null);
        RouteSnapshot star = service.capture(BusinessKind.PROCESSING, "MD-20261030-0002", "星月", null);
        RouteSnapshot walnut = service.capture(BusinessKind.PROCESSING, "MD-20261030-0003", "大核桃", null);
        RouteSnapshot sampling = service.capture(BusinessKind.SAMPLING, "DY-20261030-0001", "", null);
        RouteSnapshot gold = service.capture(BusinessKind.GOLDWORK, "GJ-20261030-0001", "", null);
        RouteSnapshot confirmed = service.confirm(bead.snapshotId(), null);

        assertThat(bead.stepIds()).hasSize(15).contains("POLISH_BEAD").doesNotContain("POLISH_GOLD");
        assertThat(star.stepIds()).hasSize(11).startsWith("SHAPE").endsWith("STRING");
        assertThat(walnut.stepIds()).containsExactly("SCREEN", "GROOVE", "INLAY_MARK", "REMOVE_GLUE", "PHOTO", "COUNT", "INSPECT", "STRING");
        assertThat(sampling.stepIds()).doesNotContain("POLISH_BEAD", "POLISH_GOLD");
        assertThat(gold.stepIds()).contains("POLISH_GOLD").doesNotContain("POLISH_BEAD");
        assertThat(ProcessCatalog.POLISH_BEAD.id()).isNotEqualTo(ProcessCatalog.POLISH_GOLD.id());
        assertThat(confirmed.confirmed()).isTrue();
        assertThat(confirmed.targetCode()).isEqualTo("MD-20261030-0001");
        assertThat(confirmed.stepIds()).isEqualTo(bead.stepIds());
        assertThat(repository.list(BusinessKind.SAMPLING, "MD-20261030-0001")).isEmpty();
        assertThat(repository.list(BusinessKind.GOLDWORK, "DY-20261030-0001")).isEmpty();
    }

    @Test
    void 再次确认不另造版本() {
        RouteSnapshotService service = new RouteSnapshotService(new InMemoryRouteSnapshotRepository());
        RouteSnapshot captured = service.capture(BusinessKind.PROCESSING, "ZD-20261030-0001", "木料", null);
        RouteSnapshot once = service.confirm(captured.snapshotId(), null);
        RouteSnapshot twice = service.confirm(captured.snapshotId(), null);
        assertThat(twice).isEqualTo(once);
        assertThat(List.of(once.version())).containsExactly(1);
    }
}
