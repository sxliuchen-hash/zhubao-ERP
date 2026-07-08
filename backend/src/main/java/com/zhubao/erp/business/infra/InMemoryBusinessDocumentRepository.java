package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "zhubao.business-document.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryBusinessDocumentRepository implements BusinessDocumentRepository {

    private final ConcurrentMap<String, MotherOrderRecord> motherOrders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OutboundOrderRecord> outboundOrders = new ConcurrentHashMap<>();

    @Override
    public boolean motherOrderExists(String code) {
        return motherOrders.containsKey(code);
    }

    @Override
    public MotherOrderRecord saveMotherOrder(MotherOrderRecord record) {
        motherOrders.put(record.code(), record);
        return record;
    }

    @Override
    public Optional<MotherOrderRecord> findMotherOrderByCode(String code) {
        return Optional.ofNullable(motherOrders.get(code));
    }

    @Override
    public boolean outboundOrderExists(String code) {
        return outboundOrders.containsKey(code);
    }

    @Override
    public OutboundOrderRecord saveOutboundOrder(OutboundOrderRecord record) {
        outboundOrders.put(record.code(), record);
        return record;
    }

    @Override
    public Optional<OutboundOrderRecord> findOutboundOrderByCode(String code) {
        return Optional.ofNullable(outboundOrders.get(code));
    }
}
