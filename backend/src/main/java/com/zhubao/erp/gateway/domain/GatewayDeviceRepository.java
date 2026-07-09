package com.zhubao.erp.gateway.domain;

import java.util.List;
import java.util.Optional;

public interface GatewayDeviceRepository {

    Optional<GatewayDevice> findByCode(String deviceCode);

    List<GatewayDevice> findAll();
}
