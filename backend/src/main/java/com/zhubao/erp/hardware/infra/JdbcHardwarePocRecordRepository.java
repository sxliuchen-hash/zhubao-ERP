package com.zhubao.erp.hardware.infra;

import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import com.zhubao.erp.hardware.domain.HardwarePocRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.hardware.poc.repository", havingValue = "jdbc")
public class JdbcHardwarePocRecordRepository implements HardwarePocRecordRepository {

    private final DataSource dataSource;

    public JdbcHardwarePocRecordRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<HardwarePocRecord> findAll() {
        String sql = """
                select poc_code, device_code, scene, test_window, result, evidence, blocker, owner_name, next_action
                  from hw_poc_record
                 order by id asc
                """;
        List<HardwarePocRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                records.add(new HardwarePocRecord(
                        rs.getString("poc_code"),
                        rs.getString("device_code"),
                        rs.getString("scene"),
                        rs.getString("test_window"),
                        rs.getString("result"),
                        rs.getString("evidence"),
                        rs.getString("blocker"),
                        rs.getString("owner_name"),
                        rs.getString("next_action")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load hardware POC records", ex);
        }
        return records;
    }
}
