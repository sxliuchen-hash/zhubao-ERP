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

import static org.assertj.core.api.Assertions.assertThat;

class JdbcNavigationMenuRepositoryTest {

    private JdbcNavigationMenuRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
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
