package com.zhubao.erp.business.infra;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcExecutionRoundListTest {

    @Test
    void listAllReadsEveryRoundWithoutMergingIdentities() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:rounds-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table biz_execution_round (
                        id varchar(40) primary key,
                        mother_order_code varchar(90) not null,
                        source_work_order_code varchar(90) not null,
                        round_work_order_code varchar(90) not null,
                        process_name varchar(32) not null,
                        change_id varchar(40) not null,
                        supersedes_order_code varchar(90) not null,
                        created_at varchar(40) not null
                    )
                    """);
            statement.execute("""
                    insert into biz_execution_round values
                    ('1', 'MD-1', 'ZD-1', 'RW-1', '抛光', 'C1', 'ZD-1', '2026-10-23T09:00:00'),
                    ('2', 'MD-1', 'ZD-2', 'RW-2', '镶嵌', 'C2', 'ZD-2', '2026-10-23T10:00:00')
                    """);
        }

        var rounds = new JdbcOrderRequirementRepository(dataSource).listAll();
        assertThat(rounds).extracting(round -> round.roundWorkOrderCode()).containsExactly("RW-1", "RW-2");
        assertThat(new JdbcOrderRequirementRepository(dataSource).listByMother("MD-1")).hasSize(2);
    }
}
