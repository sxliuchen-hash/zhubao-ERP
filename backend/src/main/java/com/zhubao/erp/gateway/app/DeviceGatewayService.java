package com.zhubao.erp.gateway.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.gateway.domain.ConsumeReadingCommand;
import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.DeviceReadingRepository;
import com.zhubao.erp.gateway.domain.DeviceReadingStatus;
import com.zhubao.erp.gateway.domain.DeviceReadingType;
import com.zhubao.erp.gateway.domain.GatewayDevice;
import com.zhubao.erp.gateway.domain.GatewayDeviceRepository;
import com.zhubao.erp.gateway.domain.GatewayDeviceView;
import com.zhubao.erp.gateway.domain.GatewayIngestionSummary;
import com.zhubao.erp.gateway.domain.ReadingConsumption;
import com.zhubao.erp.gateway.domain.ReadingConsumptionIntent;
import com.zhubao.erp.gateway.domain.ReadingConsumptionRepository;
import com.zhubao.erp.gateway.domain.RegisterGatewayDeviceCommand;
import com.zhubao.erp.gateway.domain.SubmitDeviceReadingCommand;
import com.zhubao.erp.gateway.error.DeviceAuthenticationException;
import com.zhubao.erp.gateway.error.GatewayRateLimitException;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.OperatorContext;
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
    private final ReadingConsumptionRepository consumptionRepository;
    private final DeviceReadingNormalizer normalizer;
    private final PasswordHasher passwordHasher;
    private final GatewayRateLimiter rateLimiter;

    public DeviceGatewayService(
            GatewayDeviceRepository deviceRepository,
            DeviceReadingRepository readingRepository,
            ReadingConsumptionRepository consumptionRepository,
            DeviceReadingNormalizer normalizer,
            PasswordHasher passwordHasher,
            GatewayRateLimiter rateLimiter
    ) {
        this.deviceRepository = deviceRepository;
        this.readingRepository = readingRepository;
        this.consumptionRepository = consumptionRepository;
        this.normalizer = normalizer;
        this.passwordHasher = passwordHasher;
        this.rateLimiter = rateLimiter;
    }

    public DeviceReading ingest(SubmitDeviceReadingCommand command) {
        if (command == null) {
            throw new BusinessException("设备读数命令不能为空");
        }
        GatewayDevice device = authenticate(command.deviceCode(), command.deviceKey());
        if (!rateLimiter.tryAcquire(device.deviceCode())) {
            throw new GatewayRateLimitException(
                    "设备 " + device.deviceCode() + " 读数频率超过限制（窗口内上限 "
                            + rateLimiter.maxReadings() + " 条），请检查桥接程序或密钥是否异常");
        }
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

    /**
     * Consume a device reading into an accountable business intent. RBAC is enforced at the
     * interceptor; the resolved operator context is passed in so the consumption is attributed.
     */
    public ReadingConsumption consume(ConsumeReadingCommand command, OperatorContext operatorContext) {
        if (command == null) {
            throw new BusinessException("读数消费命令不能为空");
        }
        DeviceReading reading = readingRepository.findByCode(command.readingCode().trim())
                .orElseThrow(() -> new BusinessException("未找到读数：" + command.readingCode()));
        ReadingConsumptionIntent intent = ReadingConsumptionIntent.fromString(command.intent());
        if (intent == null) {
            throw new BusinessException("不支持的消费意图：" + command.intent());
        }
        if (intent.requiredType() != null && intent.requiredType() != reading.readingType()) {
            throw new BusinessException(
                    "意图 " + intent.name() + " 只能用于 " + intent.requiredType().name() + " 读数");
        }
        if (reading.status() == DeviceReadingStatus.BLOCKED && intent != ReadingConsumptionIntent.DISMISS) {
            throw new BusinessException("BLOCKED 读数只能忽略(DISMISS)，无法作为业务信号消费");
        }
        consumptionRepository.findByReadingCode(reading.readingCode()).ifPresent(existing -> {
            throw new BusinessException("读数已被消费：" + existing.consumptionCode());
        });

        String now = TIME_FORMATTER.format(LocalDateTime.now());
        ReadingConsumption consumption = new ReadingConsumption(
                "RC-" + reading.readingCode(),
                reading.readingCode(),
                reading.deviceCode(),
                reading.readingType(),
                intent,
                reading.normalizedValue(),
                reading.normalizedUnit(),
                reading.normalizedCode(),
                blankToDefault(command.businessRefType(), reading.businessRefType()),
                blankToDefault(command.businessRefCode(), reading.businessRefCode()),
                operatorContext.operator(),
                operatorContext.role().name(),
                blankToEmpty(command.note()),
                now
        );
        return consumptionRepository.save(consumption);
    }

    public List<ReadingConsumption> recentConsumptions(int limit) {
        return consumptionRepository.findRecent(clampLimit(limit));
    }

    /**
     * Register a new gateway device or update an existing one. On create the device key is required;
     * on update a blank key keeps the current hash. Returns the safe view (never the key hash).
     */
    public GatewayDeviceView registerDevice(RegisterGatewayDeviceCommand command) {
        if (command == null) {
            throw new BusinessException("设备注册命令不能为空");
        }
        String deviceCode = command.deviceCode().trim();
        List<DeviceReadingType> acceptedTypes = parseTypes(command.acceptedTypes());
        if (acceptedTypes.isEmpty()) {
            throw new BusinessException("至少选择一种读数类型");
        }
        var existing = deviceRepository.findByCode(deviceCode);
        String keyHash;
        String createdAt;
        if (existing.isPresent()) {
            keyHash = command.deviceKey() == null || command.deviceKey().isBlank()
                    ? existing.get().deviceKeyHash()
                    : passwordHasher.hash(command.deviceKey().trim());
            createdAt = existing.get().createdAt();
        } else {
            if (command.deviceKey() == null || command.deviceKey().isBlank()) {
                throw new BusinessException("新建设备必须提供设备密钥");
            }
            keyHash = passwordHasher.hash(command.deviceKey().trim());
            createdAt = TIME_FORMATTER.format(LocalDateTime.now());
        }
        GatewayDevice device = new GatewayDevice(
                deviceCode,
                command.deviceName().trim(),
                acceptedTypes,
                keyHash,
                command.enabled() == null || command.enabled(),
                blankToEmpty(command.location()),
                blankToEmpty(command.fallbackNote()),
                createdAt
        );
        GatewayDevice saved = deviceRepository.save(device);
        return toView(saved, readingRepository.findRecent(RECENT_WINDOW));
    }

    public GatewayDeviceView setDeviceEnabled(String deviceCode, boolean enabled) {
        GatewayDevice device = requireDevice(deviceCode);
        GatewayDevice saved = deviceRepository.save(new GatewayDevice(
                device.deviceCode(), device.deviceName(), device.acceptedTypes(), device.deviceKeyHash(),
                enabled, device.location(), device.fallbackNote(), device.createdAt()));
        return toView(saved, readingRepository.findRecent(RECENT_WINDOW));
    }

    public GatewayDeviceView rotateDeviceKey(String deviceCode, String newDeviceKey) {
        if (newDeviceKey == null || newDeviceKey.isBlank()) {
            throw new BusinessException("新设备密钥不能为空");
        }
        GatewayDevice device = requireDevice(deviceCode);
        GatewayDevice saved = deviceRepository.save(new GatewayDevice(
                device.deviceCode(), device.deviceName(), device.acceptedTypes(),
                passwordHasher.hash(newDeviceKey.trim()), device.enabled(),
                device.location(), device.fallbackNote(), device.createdAt()));
        return toView(saved, readingRepository.findRecent(RECENT_WINDOW));
    }

    private GatewayDevice requireDevice(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new BusinessException("缺少设备编码");
        }
        return deviceRepository.findByCode(deviceCode.trim())
                .orElseThrow(() -> new BusinessException("未注册的设备：" + deviceCode));
    }

    private List<DeviceReadingType> parseTypes(List<String> rawTypes) {
        return rawTypes.stream()
                .map(DeviceReadingType::fromString)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
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
