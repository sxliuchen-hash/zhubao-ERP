package com.zhubao.erp.gateway.infra;

import com.zhubao.erp.gateway.domain.DeviceReadingType;
import com.zhubao.erp.gateway.domain.GatewayDevice;
import com.zhubao.erp.gateway.domain.GatewayDeviceRepository;
import com.zhubao.erp.security.app.PasswordHasher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default in-memory registry of gateway devices.
 *
 * <p>Seeds a small set of low-cost review devices whose shared key is hashed at startup. The raw
 * review key defaults to {@code zhubao-device-demo} and can be overridden via
 * {@code zhubao.gateway.demo-device-key}. These review devices are for evaluation only and must be
 * replaced or disabled before production, exactly like the demo login accounts.</p>
 */
@Repository
@ConditionalOnProperty(name = "zhubao.gateway.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGatewayDeviceRepository implements GatewayDeviceRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ConcurrentMap<String, GatewayDevice> devices = new ConcurrentHashMap<>();

    public InMemoryGatewayDeviceRepository(PasswordHasher passwordHasher, Environment environment) {
        String demoKey = environment.getProperty("zhubao.gateway.demo-device-key", "zhubao-device-demo");
        String keyHash = passwordHasher.hash(demoKey);
        String now = TIME_FORMATTER.format(LocalDateTime.now());
        seed(new GatewayDevice(
                "SCALE-01",
                "称重台电子秤桥接",
                List.of(DeviceReadingType.SCALE_WEIGHT, DeviceReadingType.HEARTBEAT),
                keyHash,
                true,
                "打包称重台",
                "读数异常或桥接离线时人工读数并拍照留证",
                now));
        seed(new GatewayDevice(
                "SCAN-PC-01",
                "PC 扫码枪桥接",
                List.of(DeviceReadingType.SCAN_EVENT, DeviceReadingType.HEARTBEAT),
                keyHash,
                true,
                "收货/复核工位",
                "扫码失败时手工录入编码并二次确认",
                now));
        seed(new GatewayDevice(
                "PRN-LABEL-01",
                "标签打印机回执桥接",
                List.of(DeviceReadingType.PRINT_ACK, DeviceReadingType.HEARTBEAT),
                keyHash,
                true,
                "标签打印工位",
                "回执失败时导出 PDF 标签人工打印并记录补打原因",
                now));
        seed(new GatewayDevice(
                "CAM-UNPACK-01",
                "拆包摄像头截取标记桥接",
                List.of(DeviceReadingType.VIDEO_CLIP_MARK, DeviceReadingType.HEARTBEAT),
                keyHash,
                true,
                "拆包台",
                "截取失败时人工上传 MP4 兜底",
                now));
    }

    private void seed(GatewayDevice device) {
        devices.put(device.deviceCode(), device);
    }

    @Override
    public GatewayDevice save(GatewayDevice device) {
        devices.put(device.deviceCode(), device);
        return device;
    }

    @Override
    public Optional<GatewayDevice> findByCode(String deviceCode) {
        return Optional.ofNullable(devices.get(deviceCode));
    }

    @Override
    public List<GatewayDevice> findAll() {
        return List.copyOf(devices.values());
    }

    Map<String, GatewayDevice> snapshot() {
        return Map.copyOf(devices);
    }
}
