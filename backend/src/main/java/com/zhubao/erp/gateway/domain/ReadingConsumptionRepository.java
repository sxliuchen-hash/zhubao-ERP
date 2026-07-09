package com.zhubao.erp.gateway.domain;

import java.util.List;
import java.util.Optional;

public interface ReadingConsumptionRepository {

    ReadingConsumption save(ReadingConsumption consumption);

    Optional<ReadingConsumption> findByReadingCode(String readingCode);

    List<ReadingConsumption> findRecent(int limit);
}
