package com.zhubao.erp.gateway.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.DeviceReadingRepository;
import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.domain.DeviceReadingType;
import com.zhubao.erp.gateway.domain.GatewayDevice;
import com.zhubao.erp.gateway.domain.GatewayDeviceRepository;
import com.zhubao.erp.gateway.domain.GatewayDeviceView;
import com.zhubao.erp.gateway.domain.GatewayIngestionSummary;
import com.zhubao.erp.gateway.domain.SubmitDeviceReadingCommand;
import com.zhubao.erp.gateway.error.DeviceAuthenticationException;
import com.zhubao.erp.security.app.PasswordHasher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Application service for the low-cost hardware ingestion gateway.
 *
 * <p>Devices authenticate with a shared device key (never a human login), submit a standardized
 * reading, and the service normalizes and persists it. This keeps cheap hardware bridges out of
 * the RBAC path while still auditing every reading.</p>
 */
@Service
public class DeviceGatewayService {

    private static final DateTimeFormatter CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int RECENT_WINDOW = 200;

    private final GatewayDeviceRepository deviceRepository;
    private final DeviceReadingRepository readingRepository;
    private final DeviceReadingNormalizer normalizer;
    private final PasswordHasher passwordHasher;

    public DeviceGatewayService(
            GatewayDeviceRepository deviceRepository,
            DeviceReadingRepository readingRepository,
            DeviceReadingNormalizer normalizer,
            PasswordHasher passwordHasher
    ) {
        this.deviceRepository = deviceRepository;
        this.readingRepository = readingRepository;
        this.normalizer = normalizer;
        this.passwordHasher = passwordHasher;
    }

    public DeviceReading ingest(SubmitDeviceReadingCommand command) {
        if (command == null) {
            throw new BusinessException("设备读数命令不能为空");
        }
        GatewayDevice device = authenticate(command.deviceCode(), command.deviceKey());
        DeviceReadingType type = DeviceReadingType.fromString(command.readingType());
        if (type == null) {
            throw new BusinessException("不支持的读数类型：" + command.readingType());
        }
        if (!device.accepts(type)) {
            throw new BusinessException(
                    "设备 " + device.deviceCode() + " 不接受读数类型 " + type.name());
        }

        DeviceReadingNormalizer.NormalizationResult result = normalizer.normalize(type, command.rawPayload());
        String now = TIME_FORMATTER.format(LocalDateTime.now());
        DeviceReading reading = new DeviceReading(
                nextReadingCode(device.deviceCode()),
                device.deviceCode(),
                type,
                command.rawPayload().trim(),
                result.value(),
                result.unit(),
                result.code(),
                result.status(),
                result.message(),
                blankToEmpty(command.businessRefType()),
                blankToEmpty(command.businessRefCode()),
                blankToDefault(command.capturedAt(), now),
                now
        );
        return readingRepository.save(reading);
    }

    public List<DeviceReading> recentReadings(int limit) {
        return readingRepository.findRecent(clampLimit(limit));
    }

    public List<DeviceReading> readingsByDevice(String deviceCode, int limit) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new BusinessException("缺少设备编码");
        }
        return readingRepository.findByDeviceCode(deviceCode.trim(), clampLimit(limit));
    }

    public List<GatewayDeviceView> devices() {
        List<DeviceReading> recent = readingRepository.findRecent(RECENT_WINDOW);
        return deviceRepository.findAll().stream()
                .map(device -> toView(device, recent))
                .sorted(Comparator.comparing(GatewayDeviceView::deviceCode))
                .toList();
    }

    public GatewayIngestionSummary summary() {
        List<GatewayDevice> devices = deviceRepository.findAll();
        List<DeviceReading> recent = readingRepository.findRecent(RECENT_WINDOW);
        long ready = recent.stream().filter(r -> r.status() == DeviceReadingStatus.READY).count();
        long warning = recent.stream().filter(r -> r.status() == DeviceReadingStatus.WARNING).count();
        long blocked = recent.stream().filter(r -> r.status() == DeviceReadingStatus.BLOCKED).count();
        return new GatewayIngestionSummary(
                devices.size(),
                (int) devices.stream().filter(GatewayDevice::enabled).count(),
                recent.size(),
                ready,
                warning,
                blocked
        );
    }

    private GatewayDevice authenticate(String deviceCode, String deviceKey) {
        GatewayDevice device = deviceRepository.findByCode(deviceCode.trim())
                .orElseThrow(() -> new DeviceAuthenticationException("未注册的设备：" + deviceCode));
        if (!device.enabled()) {
            throw new DeviceAuthenticationException("设备已停用：" + deviceCode);
        }
        if (!passwordHasher.matches(deviceKey, device.deviceKeyHash())) {
            throw new DeviceAuthenticationException("设备密钥校验失败：" + deviceCode);
        }
        return device;
    }

    private GatewayDeviceView toView(GatewayDevice device, List<DeviceReading> recent) {
        List<DeviceReading> deviceReadings = recent.stream()
                .filter(r -> device.deviceCode().equals(r.deviceCode()))
                .toList();
        String lastReadingAt = deviceReadings.stream()
                .map(DeviceReading::receivedAt)
                .max(Comparator.naturalOrder())
                .orElse("");
        return new GatewayDeviceView(
                device.deviceCode(),
                device.deviceName(),
                device.acceptedTypes(),
                device.enabled(),
                device.location(),
                device.fallbackNote(),
                deviceReadings.size(),
                lastReadingAt
        );
    }

    private String nextReadingCode(String deviceCode) {
        return "DR-" + deviceCode + "-" + CODE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, RECENT_WINDOW));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
