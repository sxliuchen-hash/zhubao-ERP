package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
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

class BagRepositoryTest {

    @Test
    void 内存版本冲突拒绝旧版本() {
        InMemoryActivityBagRepository repository = new InMemoryActivityBagRepository();
        ActivityBag bag = sample("BAG-1");
        repository.save(bag, 0, null);
        assertThatThrownBy(() -> repository.save(sample("BAG-1"), 0, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void jdbc往返一致且失败回滚() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{"V44__m3_command_request.sql", "V81__m3_activity_bag.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    statement.execute(item);
                }
            }
        }
        JdbcActivityBagRepository repository = new JdbcActivityBagRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        unit.execute(call("bag-ok-01"), session -> {
            repository.save(sample("BAG-OK"), 0, session);
            return "BAG-OK";
        });
        ActivityBag loaded = repository.find("BAG-OK").orElseThrow();
        assertThat(loaded.quantity()).isEqualByComparingTo("10");
        assertThat(loaded.weight().state()).isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(unit.execute(call("bag-ok-01"), session -> {
            repository.save(sample("BAG-AGAIN"), 0, session);
            return "SHOULD-NOT";
        })).isEqualTo("BAG-OK");
        assertThat(repository.find("BAG-AGAIN")).isEmpty();
        assertThatThrownBy(() -> unit.execute(call("bag-fail01"), session -> {
            repository.save(sample("BAG-FAIL"), 0, session);
            throw new IllegalStateException("失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.find("BAG-FAIL")).isEmpty();
    }

    private static ActivityBag sample(String id) {
        return new ActivityBag(id, "ZD-20261030-0001", "AU9999", "REQ-1", "LINE-1", "", false,
                new BigDecimal("10.000"), StockMeasurement.unknown(), 1);
    }

    private static CommandCall call(String requestId) {
        return new CommandCall(requestId, "wh", "BAG", "WORK_ORDER", "ZD-20261030-0001", "save");
    }
}
