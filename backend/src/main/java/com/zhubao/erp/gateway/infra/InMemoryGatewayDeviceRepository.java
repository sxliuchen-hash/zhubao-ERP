package com.zhubao.erp.gateway.infra;

import com.zhubao.erp.gateway.domain.GatewayDevice;
import com.zhubao.erp.gateway.domain.GatewayDeviceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 内存设备登记表，设备由管理员显式登记。 */
@Repository
@ConditionalOnProperty(name = "zhubao.gateway.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGatewayDeviceRepository implements GatewayDeviceRepository {

    private final java.util.concurrent.ConcurrentMap<String, GatewayDevice> devices = new java.util.concurrent.ConcurrentHashMap<>();


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
