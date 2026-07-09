package com.zhubao.erp.gateway.infra;

import com.zhubao.erp.gateway.domain.DeviceReading;
import com.zhubao.erp.gateway.domain.DeviceReadingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

@Repository
@ConditionalOnProperty(name = "zhubao.gateway.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryDeviceReadingRepository implements DeviceReadingRepository {

    private static final int MAX_RETAINED = 1000;

    private final Deque<DeviceReading> readings = new ConcurrentLinkedDeque<>();

    @Override
    public DeviceReading save(DeviceReading reading) {
        readings.addFirst(reading);
        while (readings.size() > MAX_RETAINED) {
            readings.pollLast();
        }
        return reading;
    }

    @Override
    public Optional<DeviceReading> findByCode(String readingCode) {
        return readings.stream()
                .filter(reading -> reading.readingCode().equals(readingCode))
                .findFirst();
    }

    @Override
    public List<DeviceReading> findRecent(int limit) {
        return readings.stream()
                .sorted(Comparator.comparing(DeviceReading::receivedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<DeviceReading> findByDeviceCode(String deviceCode, int limit) {
        List<DeviceReading> matched = new ArrayList<>(readings.stream()
                .filter(reading -> deviceCode.equals(reading.deviceCode()))
                .sorted(Comparator.comparing(DeviceReading::receivedAt).reversed())
                .limit(limit)
                .toList());
        return List.copyOf(matched);
    }
}
