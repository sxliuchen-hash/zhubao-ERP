package com.zhubao.erp.gateway.domain;

import java.util.List;

public interface DeviceReadingRepository {

    DeviceReading save(DeviceReading reading);

    List<DeviceReading> findRecent(int limit);

    List<DeviceReading> findByDeviceCode(String deviceCode, int limit);
}
