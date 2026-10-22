package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.app.MasterDataService;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.domain.MotherRequirementSource;
import com.zhubao.erp.business.domain.RequirementShortcut;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.work.domain.WorkSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcMasterDataRepositoryTest {
    @Test
    void migrationAndConcurrentCustomerIds() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:master_catalog;MODE=MySQL;DB_CLOSE_DELAY=-1");
        String sql = Files.readString(Path.of("src/main/resources/db/mysql/V59__m3_customer_material_catalog.sql"),
                StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) statement.execute(part);
            }
        }
        MasterDataService service = new MasterDataService(new JdbcMasterDataRepository(dataSource));
        assertThat(service.customers()).hasSize(4);
        assertThat(service.materials()).hasSize(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            var left = pool.submit(() -> { start.await(); return service.saveCustomer(customer("甲客户")); });
            var right = pool.submit(() -> { start.await(); return service.saveCustomer(customer("乙客户")); });
            start.countDown();
            assertThat(left.get().customerId()).isNotEqualTo(right.get().customerId());
        } finally {
            pool.shutdownNow();
        }
        assertThatThrownBy(() -> service.saveCustomer(customer("甲客户")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已存在");
        MaterialProfile material = service.saveMaterial(new MaterialProfile("", "18K 白金", "", "", true));
        assertThat(service.materialForIntake(material.materialCode()).name()).isEqualTo("18K 白金");
    }

    @Test
    void requirementShortcutSurvivesRestartOfTheSameTable() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:requirement_shortcut;MODE=MySQL;DB_CLOSE_DELAY=-1");
        String sql = Files.readString(Path.of("src/main/resources/db/mysql/V67__m3_requirement_shortcut.sql"), StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) statement.execute(part);
            }
        }
        MasterDataService service = new MasterDataService(new JdbcMasterDataRepository(dataSource));
        RequirementShortcut saved = service.saveShortcut(new RequirementShortcut(0, "镜面抛光", 0));
        assertThat(service.shortcuts()).extracting(RequirementShortcut::phrase).containsExactly("镜面抛光");
        assertThat(service.removeShortcut(saved.shortcutId()).shortcutId()).isEqualTo(saved.shortcutId());
        assertThat(service.shortcuts()).isEmpty();
    }

    @Test
    void requirementTemplateRoundTripsAndRejectsStaleVersion() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:requirement_template;MODE=MySQL;DB_CLOSE_DELAY=-1");
        String sql = Files.readString(Path.of("src/main/resources/db/mysql/V69__m3_requirement_template.sql"), StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) statement.execute(part);
            }
        }
        MasterDataService service = new MasterDataService(new JdbcMasterDataRepository(dataSource));
        assertThat(service.templates(false)).extracting(com.zhubao.erp.business.domain.RequirementTemplate::name)
                .contains("常规珠型加工", "珠型雕刻加工");
        var saved = service.saveTemplate("", "现场模板", java.util.List.of("开粗", "抛光"), 9, 0, "jdbc-tpl-1");
        assertThat(service.saveTemplate("", "现场模板", java.util.List.of("开粗", "抛光"), 9, 0, "jdbc-tpl-1").templateCode())
                .isEqualTo(saved.templateCode());
        assertThatThrownBy(() -> service.saveTemplate(saved.templateCode(), "现场模板", java.util.List.of("抛光"), 9, 0, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");
    }

    @Test
    void motherRequirementSourceFollowsTheSessionConnection() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mother_requirement_source;MODE=MySQL;DB_CLOSE_DELAY=-1");
        String sql = Files.readString(Path.of("src/main/resources/db/mysql/V69__m3_requirement_template.sql"), StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) statement.execute(part);
            }
        }
        JdbcMotherRequirementSourceRepository repository = new JdbcMotherRequirementSourceRepository(dataSource);
        MotherRequirementSource source = new MotherRequirementSource("MD-SNAP-1", "TPL-BEAD-BASIC", 1, "先开粗，再抛光");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            repository.save(source, session(connection));
            connection.rollback();
        }
        assertThat(repository.find("MD-SNAP-1")).isEmpty();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            repository.save(source, session(connection));
            connection.commit();
        }
        assertThat(repository.find("MD-SNAP-1").orElseThrow().savedRequirement()).isEqualTo("先开粗，再抛光");
        assertThat(repository.find("MD-SNAP-1").orElseThrow().templateVersion()).isEqualTo(1);
    }

    private static WorkSession session(Connection connection) {
        return new WorkSession() {
            @Override
            public Optional<Connection> connection() {
                return Optional.of(connection);
            }

            @Override
            public void onRollback(Runnable undo) {
            }
        };
    }

    private CustomerProfile customer(String name) {
        return new CustomerProfile(0, "", name, "", "", "", "", true);
    }
}
