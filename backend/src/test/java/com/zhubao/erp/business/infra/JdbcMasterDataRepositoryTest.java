package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.app.MasterDataService;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.domain.RequirementShortcut;
import com.zhubao.erp.common.error.BusinessException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
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

    private CustomerProfile customer(String name) {
        return new CustomerProfile(0, "", name, "", "", "", "", true);
    }
}
