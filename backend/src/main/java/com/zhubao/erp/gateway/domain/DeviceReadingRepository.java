package com.zhubao.erp.gateway.domain;

import java.util.List;
import java.util.Optional;

public interface DeviceReadingRepository {

    DeviceReading save(DeviceReading reading);

    Optional<DeviceReading> findByCode(String readingCode);

    List<DeviceReading> findRecent(int limit);

    List<DeviceReading> findByDeviceCode(String deviceCode, int limit);
}
