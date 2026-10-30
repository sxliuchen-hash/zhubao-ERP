package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.domain.CustodyMode;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.ToolDisposition;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.InMemoryToolCustodyRepository;
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

class ToolReturnDispositionTest {

    private final OperatorContext admin = new OperatorContext("库管", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 归还后库内八件未还两件且报废不扣工资() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryToolCustodyRepository tools = new InMemoryToolCustodyRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        var knife = new FactoryItemService(items, unit, guard).register(
                "f08-knife01", admin, FactoryItemCategory.REUSABLE_TOOL, "车刀", "3mm", "把", null, CustodyMode.QUANTITY);
        ToolCustodyService service = new ToolCustodyService(items, tools, unit, guard);
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        service.place("f08-place01", admin, knife.itemId(), "柜-1", new BigDecimal("10"));
        var loan = service.borrow("f08-borr-01", admin, knife.itemId(), "柜-1", "P-01", "张三", new BigDecimal("6"), "");
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("4");
        assertThat(service.outstanding(knife.itemId(), "P-01")).isEqualByComparingTo("6");

        var returned = service.giveBack("f08-back-01", admin, loan.loanId(), 1, new BigDecimal("4"));
        assertThat(returned.returnedQuantity()).isEqualByComparingTo("4");
        assertThat(returned.outstanding()).isEqualByComparingTo("2");
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("8");
        service.giveBack("f08-back-01", admin, loan.loanId(), 1, new BigDecimal("4"));
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("8");
        assertThat(service.outstanding(knife.itemId(), "P-01")).isEqualByComparingTo("2");
        assertThatThrownBy(() -> service.giveBack("f08-back-02", admin, loan.loanId(), 2, new BigDecimal("3")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("归还数量不能超过未还");
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("8");

        ToolDisposition hurt = service.damage("f08-dmg-001", admin, loan.loanId(), BigDecimal.ONE, "刃口崩了");
        assertThat(hurt.holderId()).isEqualTo("P-01");
        assertThat(service.outstanding(knife.itemId(), "P-01")).isEqualByComparingTo("2");
        ToolDisposition scrapped = service.scrap("f08-scrap01", admin, loan.loanId(), 2, new BigDecimal("2"), "无法修复");
        assertThat(scrapped.kind()).isEqualTo(ToolDisposition.SCRAP);
        assertThat(service.outstanding(knife.itemId(), "P-01")).isEqualByComparingTo("0");
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("8");
        assertThat(tools.dispositionsOf(loan.loanId())).extracting(ToolDisposition::holderId).containsOnly("P-01");
        assertThat(finance.listPayrollLines()).isEmpty();
        assertThat(finance.listPayables()).isEmpty();
    }
}
