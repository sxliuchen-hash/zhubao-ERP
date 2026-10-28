package com.zhubao.erp.hardware.infra;

import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import com.zhubao.erp.hardware.domain.HardwarePocRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.hardware.poc.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryHardwarePocRecordRepository implements HardwarePocRecordRepository {

    @Override
    public List<HardwarePocRecord> findAll() {
        return List.of();
    }
}
