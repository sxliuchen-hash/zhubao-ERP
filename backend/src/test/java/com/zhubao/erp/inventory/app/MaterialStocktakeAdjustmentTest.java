package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CustodyMode;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.MaterialStocktake;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.InMemoryMaterialStocktakeRepository;
import com.zhubao.erp.inventory.infra.InMemoryToolCustodyRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialStocktakeAdjustmentTest {

    private final OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 实盘不覆盖账面且在外数量不进快照() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryToolCustodyRepository tools = new InMemoryToolCustodyRepository();
        InMemoryMaterialStocktakeRepository stocktakes = new InMemoryMaterialStocktakeRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        var knife = new FactoryItemService(items, unit, guard).register(
                "f09-knife01", admin, FactoryItemCategory.REUSABLE_TOOL, "车刀", "3mm", "把", null, CustodyMode.QUANTITY);
        ToolCustodyService custody = new ToolCustodyService(items, tools, unit, guard);
        custody.place("f09-place01", admin, knife.itemId(), "柜-1", new BigDecimal("10"));
        custody.borrow("f09-borr-01", admin, knife.itemId(), "柜-1", "P-01", "张三", new BigDecimal("6"), "");
        MaterialStocktakeService service = new MaterialStocktakeService(
                items, new com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository(), tools, stocktakes, unit, guard);
        assertThatThrownBy(() -> service.open(
                "f09-open-00", admin, knife.itemId(), "", "柜-1", "", true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请填写盘点时点");
        MaterialStocktake opened = service.open(
                "f09-open-01", admin, knife.itemId(), "", "柜-1", "NOW", true);
        assertThat(opened.bookQuantity()).isEqualByComparingTo("4");
        MaterialStocktake counted = service.count("f09-count01", admin, opened.stocktakeId(), 1, new BigDecimal("5"));
        assertThat(counted.bookQuantity()).isEqualByComparingTo("4");
        assertThat(counted.countedQuantity()).isEqualByComparingTo("5");
        OperatorContext warehouse = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThat(guard.allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER)).doesNotContain(com.zhubao.erp.security.domain.PermissionCode.FACTORY_STOCKTAKE_CONFIRM);
        assertThatThrownBy(() -> service.confirm("f09-conf-00", warehouse, opened.stocktakeId(), 2))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
        MaterialStocktake confirmed = service.confirm("f09-conf-01", admin, opened.stocktakeId(), 2);
        assertThat(confirmed.status()).isEqualTo(MaterialStocktake.CONFIRMED);
        assertThat(confirmed.bookQuantity()).isEqualByComparingTo("4");
        assertThat(custody.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("5");
        service.confirm("f09-conf-01", admin, opened.stocktakeId(), 2);
        assertThat(custody.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("5");
        assertThat(custody.outstanding(knife.itemId(), "P-01")).isEqualByComparingTo("6");
    }
}
