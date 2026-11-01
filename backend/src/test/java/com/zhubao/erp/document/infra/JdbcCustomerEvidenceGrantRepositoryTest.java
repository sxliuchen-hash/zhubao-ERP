package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.CustomerEvidenceGrant;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class JdbcCustomerEvidenceGrantRepositoryTest {
    @Test
    void 撤销后详情与旧票据编号都不存在且不影响其他客户() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            for (String sql : MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql/V107__m3_customer_evidence_grant.sql")))) statement.execute(sql);
        }
        var grants = new JdbcCustomerEvidenceGrantRepository(source);
        grants.save(new CustomerEvidenceGrant("G1", 1, "A1", "IMAGE"));
        grants.save(new CustomerEvidenceGrant("G2", 2, "A2", "IMAGE"));
        grants.revoke("G1");
        grants.revoke("G1");
        var reopened = new JdbcCustomerEvidenceGrantRepository(source);
        assertThat(reopened.findById("G1")).isEmpty();
        assertThat(reopened.listByCustomer(1)).isEmpty();
        assertThat(reopened.listByCustomer(2)).hasSize(1);
    }
}
