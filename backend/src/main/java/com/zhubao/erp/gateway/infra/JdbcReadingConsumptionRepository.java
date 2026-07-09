package com.zhubao.erp.gateway.infra;

import com.zhubao.erp.gateway.domain.DeviceReadingType;
import com.zhubao.erp.gateway.domain.ReadingConsumption;
import com.zhubao.erp.gateway.domain.ReadingConsumptionIntent;
import com.zhubao.erp.gateway.domain.ReadingConsumptionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.gateway.repository", havingValue = "jdbc")
public class JdbcReadingConsumptionRepository implements ReadingConsumptionRepository {

    private final DataSource dataSource;

    public JdbcReadingConsumptionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ReadingConsumption save(ReadingConsumption consumption) {
        String sql = """
                insert into hw_gateway_reading_consumption (
                    consumption_code, reading_code, device_code, reading_type, intent,
                    normalized_value, normalized_unit, normalized_code, business_ref_type,
                    business_ref_code, operator, role_code, note, ledger_code, consumed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, consumption.consumptionCode());
            statement.setString(2, consumption.readingCode());
            statement.setString(3, consumption.deviceCode());
            statement.setString(4, consumption.readingType().name());
            statement.setString(5, consumption.intent().name());
            statement.setString(6, consumption.normalizedValue());
            statement.setString(7, consumption.normalizedUnit());
            statement.setString(8, consumption.normalizedCode());
            statement.setString(9, consumption.businessRefType());
            statement.setString(10, consumption.businessRefCode());
            statement.setString(11, consumption.operator());
            statement.setString(12, consumption.role());
            statement.setString(13, consumption.note());
            statement.setString(14, consumption.ledgerCode());
            statement.setString(15, consumption.consumedAt());
            statement.executeUpdate();
            return consumption;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save reading consumption: " + consumption.consumptionCode(), ex);
        }
    }

    @Override
    public Optional<ReadingConsumption> findByReadingCode(String readingCode) {
        String sql = selectSql() + " where reading_code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, readingCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapConsumption(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load reading consumption: " + readingCode, ex);
        }
    }

    @Override
    public List<ReadingConsumption> findRecent(int limit) {
        String sql = selectSql() + " order by id desc limit ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<ReadingConsumption> consumptions = new ArrayList<>();
                while (rs.next()) {
                    consumptions.add(mapConsumption(rs));
                }
                return List.copyOf(consumptions);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load recent reading consumptions", ex);
        }
    }

    private String selectSql() {
        return """
                select consumption_code, reading_code, device_code, reading_type, intent,
                       normalized_value, normalized_unit, normalized_code, business_ref_type,
                       business_ref_code, operator, role_code, note, ledger_code, consumed_at
                  from hw_gateway_reading_consumption
                """;
    }

    private ReadingConsumption mapConsumption(ResultSet rs) throws SQLException {
        return new ReadingConsumption(
                rs.getString("consumption_code"),
                rs.getString("reading_code"),
                rs.getString("device_code"),
                DeviceReadingType.valueOf(rs.getString("reading_type")),
                ReadingConsumptionIntent.valueOf(rs.getString("intent")),
                rs.getString("normalized_value"),
                rs.getString("normalized_unit"),
                rs.getString("normalized_code"),
                rs.getString("business_ref_type"),
                rs.getString("business_ref_code"),
                rs.getString("operator"),
                rs.getString("role_code"),
                rs.getString("note"),
                rs.getString("ledger_code"),
                rs.getString("consumed_at")
        );
    }
}
