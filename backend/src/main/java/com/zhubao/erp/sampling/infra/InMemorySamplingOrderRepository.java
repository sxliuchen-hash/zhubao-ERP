package com.zhubao.erp.sampling.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.sampling.domain.SamplingOrder;
import com.zhubao.erp.sampling.domain.SamplingOrderRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.sampling-order.repository", havingValue = "memory", matchIfMissing = true)
public class InMemorySamplingOrderRepository implements SamplingOrderRepository {

    private final ConcurrentHashMap<String, SamplingOrder> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(SamplingOrder order, WorkSession session) {
        SamplingOrder previous = rows.putIfAbsent(order.samplingCode(), order);
        if (previous != null) {
            throw new BusinessException("打样单号冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(order.samplingCode(), order));
        }
    }

    @Override
    public Optional<SamplingOrder> find(String samplingCode) {
        if (samplingCode == null || samplingCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(samplingCode.trim()));
    }

    @Override
    public List<SamplingOrder> list() {
        return rows.values().stream()
                .sorted(Comparator.comparing(SamplingOrder::samplingCode))
                .toList();
    }

    @Override
    public List<String> lockDay(LocalDate day, WorkSession session) {
        String prefix = "DY-" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        return rows.keySet().stream().filter(code -> code.startsWith(prefix)).sorted().toList();
    }
}
