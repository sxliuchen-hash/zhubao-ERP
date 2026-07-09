package com.zhubao.erp.gateway.infra;

import com.zhubao.erp.gateway.domain.ReadingConsumption;
import com.zhubao.erp.gateway.domain.ReadingConsumptionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "zhubao.gateway.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryReadingConsumptionRepository implements ReadingConsumptionRepository {

    private final ConcurrentMap<String, ReadingConsumption> consumptions = new ConcurrentHashMap<>();

    @Override
    public ReadingConsumption save(ReadingConsumption consumption) {
        consumptions.put(consumption.readingCode(), consumption);
        return consumption;
    }

    @Override
    public Optional<ReadingConsumption> findByReadingCode(String readingCode) {
        return Optional.ofNullable(consumptions.get(readingCode));
    }

    @Override
    public List<ReadingConsumption> findRecent(int limit) {
        return consumptions.values().stream()
                .sorted(Comparator.comparing(ReadingConsumption::consumedAt).reversed())
                .limit(limit)
                .toList();
    }
}
