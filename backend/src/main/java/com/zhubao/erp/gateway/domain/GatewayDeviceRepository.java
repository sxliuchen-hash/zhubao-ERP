package com.zhubao.erp.gateway.domain;

import java.util.List;
import java.util.Optional;

public interface GatewayDeviceRepository {

    /** Insert a new device or update the existing one keyed by device code. */
    GatewayDevice save(GatewayDevice device);

    Optional<GatewayDevice> findByCode(String deviceCode);

    List<GatewayDevice> findAll();
}
