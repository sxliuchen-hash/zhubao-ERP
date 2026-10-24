package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.DeskTask;
import com.zhubao.erp.business.domain.DeskTaskKind;
import com.zhubao.erp.business.domain.DeskTaskStatus;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDeskTaskRepositoryTest {

    private JdbcDataSource dataSource;
    private JdbcDeskTaskRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        String sql = new String(getClass().getClassLoader().getResourceAsStream("db/mysql/V72__m3_desk_task.sql").readAllBytes(),
                StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String part : MigrationCatalog.statements(sql)) {
                statement.execute(part);
            }
        }
        repository = new JdbcDeskTaskRepository(dataSource);
    }

    @Test
    void 保存后按请求号回读且请求号不能重复() throws SQLException {
        DeskTask first = open("DT-20261022-0001", "req-1");
        repository.insert(first);
        JdbcDeskTaskRepository reloaded = new JdbcDeskTaskRepository(dataSource);

        assertThat(reloaded.findByRequestId("req-1")).get().satisfies(saved -> {
            assertThat(saved.taskCode()).isEqualTo("DT-20261022-0001");
            assertThat(saved.kind()).isEqualTo(DeskTaskKind.MODELING);
            assertThat(saved.customerId()).isEqualTo(1001L);
            assertThat(saved.status()).isEqualTo(DeskTaskStatus.OPEN);
            assertThat(saved.completeRequestId()).isEmpty();
        });
        repository.insert(open("DT-20261022-0002", "req-2"));
        assertThat(reloaded.listAll()).hasSize(2);
        assertThatThrownBy(() -> repository.insert(open("DT-20261022-0003", "req-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求编号已使用");
    }

    @Test
    void 版本不符不覆盖完成请求号唯一() {
        repository.insert(open("DT-20261022-0001", "req-1"));
        DeskTask done = done("DT-20261022-0001", "req-1", "finish-1");

        assertThat(repository.updateIfVersion(done, 0)).isEmpty();
        assertThat(repository.findByCode("DT-20261022-0001").orElseThrow().status()).isEqualTo(DeskTaskStatus.OPEN);
        assertThat(repository.updateIfVersion(done, 1)).isPresent();
        assertThat(repository.findByCompleteRequestId("finish-1").orElseThrow().status()).isEqualTo(DeskTaskStatus.DONE);

        repository.insert(open("DT-20261022-0002", "req-2"));
        assertThatThrownBy(() -> repository.updateIfVersion(done("DT-20261022-0002", "req-2", "finish-1"), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("完成请求编号已使用");
    }

    private DeskTask open(String code, String requestId) {
        return new DeskTask(code, DeskTaskKind.MODELING, "戒托建模", "", 1001L, "恒信珠宝", "MD-HX-001",
                "designer", "建模员", "2026-11-01", DeskTaskStatus.OPEN, "sales", "2026-10-22T00:00:00Z",
                requestId, 1, "", "", "", "", "", "", "", "");
    }

    private DeskTask done(String code, String requestId, String completeRequestId) {
        return new DeskTask(code, DeskTaskKind.MODELING, "戒托建模", "", 1001L, "恒信珠宝", "MD-HX-001",
                "designer", "建模员", "2026-11-01", DeskTaskStatus.DONE, "sales", "2026-10-22T00:00:00Z",
                requestId, 2, "2026-10-22T01:00:00Z", "designer", "做好了", completeRequestId, "", "", "", "");
    }
}
