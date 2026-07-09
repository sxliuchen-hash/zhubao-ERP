package com.zhubao.erp.gateway.infra;

import com.zhubao.erp.gateway.domain.DeviceReadingType;
import com.zhubao.erp.gateway.domain.GatewayDevice;
import com.zhubao.erp.gateway.domain.GatewayDeviceRepository;
import com.zhubao.erp.security.app.PasswordHasher;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(name = "zhubao.gateway.repository", havingValue = "jdbc")
public class JdbcGatewayDeviceRepository implements GatewayDeviceRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DataSource dataSource;
    private final PasswordHasher passwordHasher;
    private final Environment environment;

    public JdbcGatewayDeviceRepository(DataSource dataSource, PasswordHasher passwordHasher, Environment environment) {
        this.dataSource = dataSource;
        this.passwordHasher = passwordHasher;
        this.environment = environment;
    }

    @PostConstruct
    void seedReviewDevicesWhenEmpty() {
        if (!findAll().isEmpty()) {
            return;
        }
        String demoKey = environment.getProperty("zhubao.gateway.demo-device-key", "zhubao-device-demo");
        String keyHash = passwordHasher.hash(demoKey);
        String now = TIME_FORMATTER.format(LocalDateTime.now());
        insert(new GatewayDevice("SCALE-01", "称重台电子秤桥接",
                List.of(DeviceReadingType.SCALE_WEIGHT, DeviceReadingType.HEARTBEAT), keyHash, true,
                "打包称重台", "读数异常或桥接离线时人工读数并拍照留证", now));
        insert(new GatewayDevice("SCAN-PC-01", "PC 扫码枪桥接",
                List.of(DeviceReadingType.SCAN_EVENT, DeviceReadingType.HEARTBEAT), keyHash, true,
                "收货/复核工位", "扫码失败时手工录入编码并二次确认", now));
        insert(new GatewayDevice("PRN-LABEL-01", "标签打印机回执桥接",
                List.of(DeviceReadingType.PRINT_ACK, DeviceReadingType.HEARTBEAT), keyHash, true,
                "标签打印工位", "回执失败时导出 PDF 标签人工打印并记录补打原因", now));
        insert(new GatewayDevice("CAM-UNPACK-01", "拆包摄像头截取标记桥接",
                List.of(DeviceReadingType.VIDEO_CLIP_MARK, DeviceReadingType.HEARTBEAT), keyHash, true,
                "拆包台", "截取失败时人工上传 MP4 兜底", now));
    }

    @Override
    public GatewayDevice save(GatewayDevice device) {
        if (findByCode(device.deviceCode()).isPresent()) {
            return update(device);
        }
        insert(device);
        return device;
    }

    private void insert(GatewayDevice device) {
        String sql = """
                insert into hw_gateway_device (
                    device_code, device_name, accepted_types, device_key_hash, enabled,
                    location, fallback_note, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, device.deviceCode());
            statement.setString(2, device.deviceName());
            statement.setString(3, serializeTypes(device.acceptedTypes()));
            statement.setString(4, device.deviceKeyHash());
            statement.setBoolean(5, device.enabled());
            statement.setString(6, device.location());
            statement.setString(7, device.fallbackNote());
            statement.setString(8, device.createdAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save gateway device: " + device.deviceCode(), ex);
        }
    }

    private GatewayDevice update(GatewayDevice device) {
        String sql = """
                update hw_gateway_device
                   set device_name = ?, accepted_types = ?, device_key_hash = ?, enabled = ?,
                       location = ?, fallback_note = ?
                 where device_code = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, device.deviceName());
            statement.setString(2, serializeTypes(device.acceptedTypes()));
            statement.setString(3, device.deviceKeyHash());
            statement.setBoolean(4, device.enabled());
            statement.setString(5, device.location());
            statement.setString(6, device.fallbackNote());
            statement.setString(7, device.deviceCode());
            statement.executeUpdate();
            return device;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update gateway device: " + device.deviceCode(), ex);
        }
    }

    @Override
    public Optional<GatewayDevice> findByCode(String deviceCode) {
        String sql = selectSql() + " where device_code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapDevice(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load gateway device: " + deviceCode, ex);
        }
    }

    @Override
    public List<GatewayDevice> findAll() {
        String sql = selectSql() + " order by device_code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<GatewayDevice> devices = new ArrayList<>();
            while (rs.next()) {
                devices.add(mapDevice(rs));
            }
            return List.copyOf(devices);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load gateway devices", ex);
        }
    }

    private String selectSql() {
        return """
                select device_code, device_name, accepted_types, device_key_hash, enabled,
                       location, fallback_note, created_at
                  from hw_gateway_device
                """;
    }

    private GatewayDevice mapDevice(ResultSet rs) throws SQLException {
        return new GatewayDevice(
                rs.getString("device_code"),
                rs.getString("device_name"),
                parseTypes(rs.getString("accepted_types")),
                rs.getString("device_key_hash"),
                rs.getBoolean("enabled"),
                rs.getString("location"),
                rs.getString("fallback_note"),
                rs.getString("created_at")
        );
    }

    private String serializeTypes(List<DeviceReadingType> types) {
        return types.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private List<DeviceReadingType> parseTypes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .map(token -> DeviceReadingType.fromString(token.toUpperCase(Locale.ROOT)))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
