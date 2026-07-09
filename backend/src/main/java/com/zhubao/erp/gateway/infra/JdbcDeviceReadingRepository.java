package com.zhubao.erp.gateway.infra;

import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.DeviceReadingRepository;
import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.domain.DeviceReadingType;
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
public class JdbcDeviceReadingRepository implements DeviceReadingRepository {

    private final DataSource dataSource;

    public JdbcDeviceReadingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DeviceReading save(DeviceReading reading) {
        String sql = """
                insert into hw_gateway_reading (
                    reading_code, device_code, reading_type, raw_payload, normalized_value,
                    normalized_unit, normalized_code, reading_status, message, business_ref_type,
                    business_ref_code, captured_at, received_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reading.readingCode());
            statement.setString(2, reading.deviceCode());
            statement.setString(3, reading.readingType().name());
            statement.setString(4, reading.rawPayload());
            statement.setString(5, reading.normalizedValue());
            statement.setString(6, reading.normalizedUnit());
            statement.setString(7, reading.normalizedCode());
            statement.setString(8, reading.status().name());
            statement.setString(9, reading.message());
            statement.setString(10, reading.businessRefType());
            statement.setString(11, reading.businessRefCode());
            statement.setString(12, reading.capturedAt());
            statement.setString(13, reading.receivedAt());
            statement.executeUpdate();
            return reading;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save device reading: " + reading.readingCode(), ex);
        }
    }

    @Override
    public Optional<DeviceReading> findByCode(String readingCode) {
        String sql = selectSql() + " where reading_code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, readingCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapReading(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load device reading: " + readingCode, ex);
        }
    }

    @Override
    public List<DeviceReading> findRecent(int limit) {
        String sql = selectSql() + " order by id desc limit ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            return list(statement);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load recent device readings", ex);
        }
    }

    @Override
    public List<DeviceReading> findByDeviceCode(String deviceCode, int limit) {
        String sql = selectSql() + " where device_code = ? order by id desc limit ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceCode);
            statement.setInt(2, limit);
            return list(statement);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load device readings for: " + deviceCode, ex);
        }
    }

    private List<DeviceReading> list(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            List<DeviceReading> readings = new ArrayList<>();
            while (rs.next()) {
                readings.add(mapReading(rs));
            }
            return List.copyOf(readings);
        }
    }

    private String selectSql() {
        return """
                select reading_code, device_code, reading_type, raw_payload, normalized_value,
                       normalized_unit, normalized_code, reading_status, message, business_ref_type,
                       business_ref_code, captured_at, received_at
                  from hw_gateway_reading
                """;
    }

    private DeviceReading mapReading(ResultSet rs) throws SQLException {
        return new DeviceReading(
                rs.getString("reading_code"),
                rs.getString("device_code"),
                DeviceReadingType.valueOf(rs.getString("reading_type")),
                rs.getString("raw_payload"),
                rs.getString("normalized_value"),
                rs.getString("normalized_unit"),
                rs.getString("normalized_code"),
                DeviceReadingStatus.valueOf(rs.getString("reading_status")),
                rs.getString("message"),
                rs.getString("business_ref_type"),
                rs.getString("business_ref_code"),
                rs.getString("captured_at"),
                rs.getString("received_at")
        );
    }
}
