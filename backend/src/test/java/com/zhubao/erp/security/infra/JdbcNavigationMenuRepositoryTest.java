package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.NavigationMenuItem;
import com.zhubao.erp.security.domain.RoleCode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcNavigationMenuRepositoryTest {

    private JdbcNavigationMenuRepository repository;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        repository = new JdbcNavigationMenuRepository(dataSource);
    }

    @Test
    void loadsMenuItemsOrderedWithButtonCodes() {
        assertThat(repository.findMenuItems())
                .extracting(NavigationMenuItem::code)
                .containsExactly("dashboard", "production", "configuration");

        assertThat(repository.findMenuItems())
                .filteredOn(item -> item.code().equals("production"))
                .singleElement()
                .satisfies(item -> assertThat(item.buttonCodes())
                        .containsExactly("BUSINESS_PRODUCTION_REPORT", "MACHINE_OUTPUT_SUBMIT"));
    }

    @Test
    void loadsRoleMenuBindings() {
        assertThat(repository.findRoleMenuBindings().get(RoleCode.OPERATOR))
                .containsExactlyInAnyOrder("dashboard", "production");
        assertThat(repository.findRoleMenuBindings().get(RoleCode.ADMIN))
                .contains("dashboard", "production", "configuration");
    }

    @Test
    void skipsDisabledMenuItems() {
        assertThat(repository.findMenuItems())
                .extracting(NavigationMenuItem::code)
                .doesNotContain("legacy");
    }

    @Test
    void 用户菜单迁移可重复执行且只授予管理员() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             var input = new ClassPathResource("db/mysql/V48__m3_users_menu.sql").getInputStream()) {
            String script = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (int round = 0; round < 2; round++) {
                for (String sql : script.split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
        }
        assertThat(repository.findMenuItems()).filteredOn(item -> item.code().equals("users"))
                .singleElement().satisfies(item -> {
                    assertThat(item.route()).isEqualTo("/users");
                    assertThat(item.buttonCodes()).containsExactly("AUTH_ACCOUNT_MANAGE", "CUSTOMER_AUTHORIZATION_MANAGE");
                });
        assertThat(repository.findRoleMenuBindings().get(RoleCode.ADMIN)).contains("users");
        repository.findRoleMenuBindings().forEach((role, menus) -> {
            if (role != RoleCode.ADMIN) assertThat(menus).doesNotContain("users");
        });
    }

    @Test
    void 客户档案菜单可重复执行且不给操作工() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             var input = new ClassPathResource("db/mysql/V68__m3_customer_archive_menu.sql").getInputStream()) {
            String script = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (int round = 0; round < 2; round++) {
                for (String sql : script.split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
        }
        assertThat(repository.findMenuItems()).filteredOn(item -> item.code().equals("customers"))
                .singleElement().satisfies(item -> {
                    assertThat(item.label()).isEqualTo("客户档案");
                    assertThat(item.buttonCodes()).containsExactly("MASTER_DATA_MANAGE");
                });
        assertThat(repository.findRoleMenuBindings().get(RoleCode.ADMIN)).contains("customers");
        assertThat(repository.findRoleMenuBindings().get(RoleCode.FINANCE_STATISTICIAN)).contains("customers");
        assertThat(repository.findRoleMenuBindings().get(RoleCode.OPERATOR)).doesNotContain("customers");
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table sys_navigation_menu (
                        id bigint primary key auto_increment,
                        code varchar(60) not null,
                        label varchar(120) not null,
                        module varchar(120),
                        route varchar(120),
                        button_codes varchar(500),
                        sort_order int not null default 0,
                        enabled tinyint(1) not null default 1
                    )
                    """);
            statement.execute("""
                    create table sys_role_menu (
                        id bigint primary key auto_increment,
                        role_code varchar(40) not null,
                        menu_code varchar(60) not null
                    )
                    """);
            statement.execute("insert into sys_navigation_menu (code, label, module, route, button_codes, sort_order, enabled) values "
                    + "('dashboard', '首页', '调度', '/dashboard', '', 10, 1),"
                    + "('production', '生产工单', '报工', '/production', 'BUSINESS_PRODUCTION_REPORT,MACHINE_OUTPUT_SUBMIT', 20, 1),"
                    + "('configuration', '配置基线', '构型', '/configuration', 'AUTH_ACCOUNT_MANAGE', 30, 1),"
                    + "('legacy', '停用菜单', '旧', '/legacy', '', 40, 0)");
            statement.execute("insert into sys_role_menu (role_code, menu_code) values "
                    + "('ADMIN', 'dashboard'), ('ADMIN', 'production'), ('ADMIN', 'configuration'),"
                    + "('OPERATOR', 'dashboard'), ('OPERATOR', 'production')");
        }
    }
}
