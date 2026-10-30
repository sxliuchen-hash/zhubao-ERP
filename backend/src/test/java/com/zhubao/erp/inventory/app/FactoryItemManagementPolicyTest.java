package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.ConsumptionTiming;
import com.zhubao.erp.inventory.domain.CustodyMode;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.inventory.web.FactoryItemController;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FactoryItemManagementPolicyTest {

    private final OperatorContext admin = new OperatorContext("库管主管", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 同材质不同规格分开且未选定方式保持未定() {
        FactoryItemService service = memoryService();
        FactoryItem eight = service.register("f01-bead-08", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        FactoryItem ten = service.register("f01-bead-10", admin, FactoryItemCategory.BEAD, "南红", "10mm", "颗", null, null);
        FactoryItem other = service.register("f01-bead-qj", admin, FactoryItemCategory.BEAD, "青金", "8mm", "颗", null, null);
        assertThat(eight.itemId()).isNotEqualTo(ten.itemId());
        assertThat(eight.specCode()).isEqualTo("8mm");
        assertThat(ten.specCode()).isEqualTo("10mm");
        assertThat(eight.consumptionTiming()).isEqualTo(ConsumptionTiming.ON_ACTUAL_USE);
        assertThat(eight.custodyMode()).isEqualTo(CustodyMode.UNDECIDED);
        assertThat(service.list(admin, "南红")).extracting(FactoryItem::specCode).containsExactlyInAnyOrder("8mm", "10mm");
        assertThat(service.list(admin, "南红")).extracting(FactoryItem::itemId).doesNotContain(other.itemId());
        assertThat(service.list(admin, "南红")).hasSize(2);

        FactoryItem opaque = service.register(
                "f01-spec-raw", admin, FactoryItemCategory.ACCESSORY, "镶标", "8mm;孔2", "颗", null, null);
        assertThat(opaque.specCode()).isEqualTo("8mm;孔2");
        assertThat(service.list(admin, "镶标")).hasSize(1);

        assertThatThrownBy(() -> service.register(
                "f01-bead-08b", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一类别、材质、规格和单位已经建档，不能合并");
        assertThatThrownBy(() -> service.register(
                "f01-bead-issue", admin, FactoryItemCategory.BEAD, "南红", "6mm", "颗", ConsumptionTiming.ON_ISSUE, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("公司珠料和配件的领用不是消耗");

        FactoryItem glue = service.register(
                "f01-glue-01", admin, FactoryItemCategory.CONSUMABLE, "胶水", "50ml", "瓶", null, null);
        assertThat(glue.consumptionTiming()).isEqualTo(ConsumptionTiming.UNDECIDED);
        FactoryItem paper = service.register(
                "f01-paper01", admin, FactoryItemCategory.CONSUMABLE, "砂纸", "240目", "张", ConsumptionTiming.ON_ISSUE, null);
        assertThat(paper.consumptionTiming()).isEqualTo(ConsumptionTiming.ON_ISSUE);
        assertThatThrownBy(() -> service.register(
                "f01-glue-pc", admin, FactoryItemCategory.CONSUMABLE, "胶水", "100ml", "瓶", null, CustodyMode.PIECE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数量或逐件保管只用于重复工具");

        FactoryItem knife = service.register(
                "f01-tool-01", admin, FactoryItemCategory.REUSABLE_TOOL, "车刀", "3mm", "把", null, null);
        assertThat(knife.consumptionTiming()).isEqualTo(ConsumptionTiming.UNDECIDED);
        assertThat(knife.custodyMode()).isEqualTo(CustodyMode.UNDECIDED);
        assertThatThrownBy(() -> service.register(
                "f01-tool-use", admin, FactoryItemCategory.REUSABLE_TOOL, "车刀", "4mm", "把", ConsumptionTiming.ON_ACTUAL_USE, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("重复工具借用不是耗用");

        FactoryItem replay = service.register(
                "f01-bead-08", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        assertThat(replay.itemId()).isEqualTo(eight.itemId());
        assertThatThrownBy(() -> service.register(
                "f01-bead-08", admin, FactoryItemCategory.BEAD, "南红", "12mm", "颗", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一请求的内容不一致");

        FactoryItem chosen = service.recordPolicy(
                "f01-glue-tm", admin, glue.itemId(), 1, "胶水", ConsumptionTiming.ON_ACTUAL_USE, null);
        assertThat(chosen.consumptionTiming()).isEqualTo(ConsumptionTiming.ON_ACTUAL_USE);
        assertThat(chosen.specCode()).isEqualTo("50ml");
        assertThat(chosen.version()).isEqualTo(2);
        FactoryItem piece = service.recordPolicy(
                "f01-tool-md", admin, knife.itemId(), 1, null, null, CustodyMode.PIECE);
        assertThat(piece.custodyMode()).isEqualTo(CustodyMode.PIECE);
        assertThat(piece.materialName()).isEqualTo("车刀");
        assertThatThrownBy(() -> service.recordPolicy(
                "f01-tool-old", admin, knife.itemId(), 1, null, null, CustodyMode.QUANTITY))
                .isInstanceOf(BusinessException.class)
                .hasMessage("物品档案已被其他人修改，请刷新后重试");
        assertThatThrownBy(() -> service.recordPolicy(
                "f01-rename01", admin, eight.itemId(), 1, "南红珠", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("材质名称属于物品身份，不能改成另一条");
        assertThat(service.list(admin, "南红")).hasSize(2);

        OperatorContext warehouse = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThat(guard.allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER)).doesNotContain(PermissionCode.FACTORY_ITEM_MANAGE);
        assertThatThrownBy(() -> service.register(
                "f01-denied1", warehouse, FactoryItemCategory.BEAD, "南红", "3mm", "颗", null, null))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void 查询不建档且失败不留下档案() {
        InMemoryFactoryItemRepository repository = new InMemoryFactoryItemRepository();
        FactoryItemService service = new FactoryItemService(
                repository, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()), guard);
        service.register("f01-list-01", admin, FactoryItemCategory.ACCESSORY, "扣针", "小号", "个", null, null);
        assertThat(service.list(admin, "扣针")).hasSize(1);
        assertThat(service.list(admin, "扣针")).hasSize(1);

        FactoryItemService failing = new FactoryItemService(repository, new MemoryBusinessWorkUnit(
                new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }), guard);
        assertThatThrownBy(() -> failing.register(
                "f01-fail-01", admin, FactoryItemCategory.ACCESSORY, "扣针", "大号", "个", null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.list("扣针")).extracting(FactoryItem::specCode).containsExactly("小号");
    }

    @Test
    void jdbc往返一致且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFactoryItemRepository repository = new JdbcFactoryItemRepository(dataSource);
        FactoryItemService service = new FactoryItemService(
                repository, new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource)), guard);
        FactoryItem saved = service.register(
                "f01-jdbc-01", admin, FactoryItemCategory.CONSUMABLE, "胶水", "50ml", "瓶", null, null);
        FactoryItem loaded = repository.find(saved.itemId()).orElseThrow();
        assertThat(loaded.materialName()).isEqualTo("胶水");
        assertThat(loaded.specCode()).isEqualTo("50ml");
        assertThat(loaded.consumptionTiming()).isEqualTo(ConsumptionTiming.UNDECIDED);
        assertThat(loaded.unit()).isEqualTo("瓶");
        FactoryItem again = service.register(
                "f01-jdbc-01", admin, FactoryItemCategory.CONSUMABLE, "胶水", "50ml", "瓶", null, null);
        assertThat(again.itemId()).isEqualTo(saved.itemId());
        assertThat(repository.list("胶水")).hasSize(1);

        FactoryItemService failing = new FactoryItemService(repository, new JdbcBusinessWorkUnit(
                dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
            throw new IllegalStateException("失败");
        }), guard);
        assertThatThrownBy(() -> failing.register(
                "f01-jdbc-x1", admin, FactoryItemCategory.CONSUMABLE, "胶水", "100ml", "瓶", null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findIdentity(FactoryItemCategory.CONSUMABLE, "胶水", "100ml", "瓶")).isEmpty();
    }

    @Test
    void 接口返回分开的规格且不改成领用即耗() {
        FactoryItemService service = memoryService();
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(admin);
        FactoryItemController controller = new FactoryItemController(service, actors);
        FactoryItem eight = controller.register(new FactoryItemController.RegisterRequest(
                "f01-http-08", "BEAD", "南红", "8mm", "颗", null, null), request).data();
        FactoryItem ten = controller.register(new FactoryItemController.RegisterRequest(
                "f01-http-10", "BEAD", "南红", "10mm", "颗", "", ""), request).data();
        assertThat(controller.list("南红", request).data()).extracting(FactoryItem::itemId)
                .containsExactlyInAnyOrder(eight.itemId(), ten.itemId());
        assertThat(eight.consumptionTiming()).isEqualTo(ConsumptionTiming.ON_ACTUAL_USE);
        assertThatThrownBy(() -> controller.register(new FactoryItemController.RegisterRequest(
                "f01-http-bad", "COLOR", "南红", "8mm", "颗", null, null), request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("管理类别不正确");
    }

    private FactoryItemService memoryService() {
        return new FactoryItemService(
                new InMemoryFactoryItemRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()),
                guard);
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{"V44__m3_command_request.sql", "V97__m3_factory_item.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        return dataSource;
    }
}
