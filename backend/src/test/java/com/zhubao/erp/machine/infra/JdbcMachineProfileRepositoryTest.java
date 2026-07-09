package com.zhubao.erp.machine.infra;

import com.zhubao.erp.machine.domain.MachineProfile;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMachineProfileRepositoryTest {

    private JdbcMachineProfileRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        repository = new JdbcMachineProfileRepository(dataSource);
    }

    @Test
    void 新增返回自增主键并可按编号查询() {
        MachineProfile created = repository.create(profile("ENG-03", "雕刻机 3 号"));

        assertThat(created.id()).isNotNull();
        assertThat(repository.findByMachineNo("ENG-03")).isPresent();
        assertThat(repository.findById(created.id())).isPresent();
    }

    @Test
    void 更新按主键生效() {
        MachineProfile created = repository.create(profile("ENG-03", "雕刻机 3 号"));

        repository.update(new MachineProfile(
                created.id(), "ENG-03", "雕刻机 3 号改名", "雕刻", "新车间", false, "检修", "李华", created.createdAt()));

        assertThat(repository.findById(created.id()))
                .get()
                .satisfies(profile -> {
                    assertThat(profile.machineName()).isEqualTo("雕刻机 3 号改名");
                    assertThat(profile.enabled()).isFalse();
                });
    }

    @Test
    void 全部查询按编号排序() {
        repository.create(profile("ENG-05", "雕刻机 5 号"));
        repository.create(profile("ENG-01", "雕刻机 1 号"));

        assertThat(repository.findAll())
                .extracting(MachineProfile::machineNo)
                .containsExactly("ENG-01", "ENG-05");
    }

    private MachineProfile profile(String machineNo, String machineName) {
        return new MachineProfile(null, machineNo, machineName, "雕刻", "雕刻车间", true, "评审", "李华", "2026-07-09T10:00:00");
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table sys_machine (
                        id bigint primary key auto_increment,
                        machine_no varchar(80) not null,
                        machine_name varchar(120) not null,
                        machine_type varchar(80),
                        location varchar(120),
                        enabled tinyint(1) not null default 1,
                        remark varchar(255),
                        created_by varchar(80),
                        created_at varchar(40),
                        unique key uk_machine_no (machine_no)
                    )
                    """);
        }
    }
}
