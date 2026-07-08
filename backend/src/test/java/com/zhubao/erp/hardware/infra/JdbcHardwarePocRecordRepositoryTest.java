package com.zhubao.erp.hardware.infra;

import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcHardwarePocRecordRepositoryTest {

    private JdbcHardwarePocRecordRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchemaAndSeed(dataSource);
        repository = new JdbcHardwarePocRecordRepository(dataSource);
    }

    @Test
    void readsHardwarePocRecordsFromJdbcContract() {
        assertThat(repository.findAll())
                .extracting(HardwarePocRecord::code)
                .containsExactly("POC-LBL-20260706-001", "POC-SCAN-20260706-001");
        assertThat(repository.findAll())
                .anySatisfy(record -> {
                    assertThat(record.deviceCode()).isEqualTo("PWA-CAM-01");
                    assertThat(record.result()).isEqualTo("阻断");
                    assertThat(record.nextAction()).contains("弱光 POC");
                });
    }

    private void createSchemaAndSeed(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table hw_poc_record (
                        id bigint primary key auto_increment,
                        poc_code varchar(80) not null unique,
                        device_code varchar(80) not null,
                        scene varchar(160) not null,
                        test_window varchar(120) not null,
                        result varchar(40) not null,
                        evidence varchar(500) not null,
                        blocker varchar(500) not null,
                        owner_name varchar(80) not null,
                        next_action varchar(500) not null
                    )
                    """);
            statement.execute("""
                    insert into hw_poc_record (
                        poc_code, device_code, scene, test_window, result, evidence, blocker, owner_name, next_action
                    ) values
                    ('POC-LBL-20260706-001', 'PRN-LABEL-01', '母单/子工单标签打印', '2026-07-06 10:30-10:45', '通过', '浏览器打印成功，PDF 模板可兜底', '无阻断', '库管员', '锁定标签模板尺寸并补打印审计'),
                    ('POC-SCAN-20260706-001', 'PWA-CAM-01', 'PWA 弱光扫码', '2026-07-06 15:20-15:45', '阻断', '弱光下识别率不足，手工输入可继续业务', '需要补充 ZXing 兜底和现场光照标准', '车间组长', '安排第二轮弱光 POC 并记录失败样本')
                    """);
        }
    }
}
