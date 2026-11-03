package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.domain.CustodyMode;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.ToolLoan;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.InMemoryToolCustodyRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.inventory.infra.JdbcToolCustodyRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolIssueCustodyTest {

    private final OperatorContext admin = new OperatorContext("库管", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 同名人员按稳定身份分开且借用不计耗() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryToolCustodyRepository tools = new InMemoryToolCustodyRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FactoryItemService catalog = new FactoryItemService(items, unit, guard);
        var knife = catalog.register("f07-knife01", admin, FactoryItemCategory.REUSABLE_TOOL, "车刀", "3mm", "把", null, CustodyMode.QUANTITY);
        var undecided = catalog.register("f07-undec01", admin, FactoryItemCategory.REUSABLE_TOOL, "夹具", "A", "件", null, null);
        ToolCustodyService service = new ToolCustodyService(items, tools, unit, guard);
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        service.place("f07-place01", admin, knife.itemId(), "柜-1", new BigDecimal("10"));
        service.place("f07-place01", admin, knife.itemId(), "柜-1", new BigDecimal("10"));
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("10");
        ToolLoan first = service.borrow("f07-borr-01", admin, knife.itemId(), "柜-1", "P-01", "张三", new BigDecimal("6"), "");
        ToolLoan second = service.borrow("f07-borr-02", admin, knife.itemId(), "柜-1", "P-02", "张三", new BigDecimal("3"), "");
        assertThat(first.holderId()).isNotEqualTo(second.holderId());
        assertThat(first.holderName()).isEqualTo(second.holderName());
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("1");
        assertThat(service.outstanding(knife.itemId(), "P-01")).isEqualByComparingTo("6");
        assertThat(service.outstanding(knife.itemId(), "P-02")).isEqualByComparingTo("3");
        assertThat(finance.listPayables()).isEmpty();
        assertThat(finance.listReceivables()).isEmpty();
        service.borrow("f07-borr-01", admin, knife.itemId(), "柜-1", "P-01", "张三", new BigDecimal("6"), "");
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("1");
        assertThatThrownBy(() -> service.borrow("f07-borr-03", admin, knife.itemId(), "柜-1", "P-01", "张三", new BigDecimal("2"), ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("可借数量不足");
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("1");
        service.place("f07-und-pl", admin, undecided.itemId(), "柜-1", new BigDecimal("2"));
        ToolLoan undecidedLoan = service.borrow("f07-und-001", admin, undecided.itemId(), "柜-1", "P-01", "张三", BigDecimal.ONE, "");
        assertThat(undecidedLoan.quantity()).isEqualByComparingTo("1");
        assertThat(service.stock(undecided.itemId(), "柜-1")).isEqualByComparingTo("1");

        var mold = catalog.register("f07-mold-01", admin, FactoryItemCategory.REUSABLE_TOOL, "模具", "M1", "件", null, CustodyMode.PIECE);
        assertThatThrownBy(() -> service.borrow("f07-piece-b1", admin, mold.itemId(), "柜-2", "P-01", "张三", BigDecimal.ONE, "MJ-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("本版工具按数量借还");
    }

    @Test
    void jdbc借出往返不双借() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcToolCustodyRepository tools = new JdbcToolCustodyRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        var knife = new FactoryItemService(items, unit, guard).register(
                "f07-jdbc-it", admin, FactoryItemCategory.REUSABLE_TOOL, "车刀", "3mm", "把", null, CustodyMode.QUANTITY);
        ToolCustodyService service = new ToolCustodyService(items, tools, unit, guard);
        service.place("f07-jdbc-pl", admin, knife.itemId(), "柜-1", new BigDecimal("10"));
        ToolLoan loan = service.borrow("f07-jdbc-br", admin, knife.itemId(), "柜-1", "P-01", "张三", new BigDecimal("6"), "");
        service.borrow("f07-jdbc-br", admin, knife.itemId(), "柜-1", "P-01", "张三", new BigDecimal("6"), "");
        assertThat(tools.find(loan.loanId()).orElseThrow().holderId()).isEqualTo("P-01");
        assertThat(service.stock(knife.itemId(), "柜-1")).isEqualByComparingTo("4");
        assertThat(service.outstanding(knife.itemId(), "P-01")).isEqualByComparingTo("6");
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{"V44__m3_command_request.sql", "V97__m3_factory_item.sql", "V103__m3_tool_custody.sql", "V104__m3_tool_disposition.sql", "V138__m3_tool_piece_disposition.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    if (item.trim().regionMatches(true, 0, "create", 0, 6) || item.trim().regionMatches(true, 0, "alter", 0, 5)) {
                        statement.execute(item);
                    }
                }
            }
        }
        return dataSource;
    }
}
