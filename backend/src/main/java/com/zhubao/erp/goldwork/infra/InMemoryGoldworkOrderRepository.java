package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialIntent;
import com.zhubao.erp.goldwork.domain.GoldworkOrder;
import com.zhubao.erp.goldwork.domain.GoldworkOrderRepository;
import com.zhubao.erp.goldwork.domain.GoldworkOrderStatus;
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
@ConditionalOnProperty(name = "zhubao.goldwork-order.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGoldworkOrderRepository implements GoldworkOrderRepository {

    private final ConcurrentHashMap<String, GoldworkOrder> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(GoldworkOrder order, WorkSession session) {
        GoldworkOrder previous = rows.putIfAbsent(order.goldworkCode(), order);
        if (previous != null) {
            throw new BusinessException("金工单号冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(order.goldworkCode(), order));
        }
    }

    @Override
    public Optional<GoldworkOrder> find(String goldworkCode) {
        if (goldworkCode == null || goldworkCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(goldworkCode.trim()));
    }

    @Override
    public List<GoldworkOrder> list() {
        return rows.values().stream().sorted(Comparator.comparing(GoldworkOrder::goldworkCode)).toList();
    }

    @Override
    public List<String> lockDay(LocalDate day, WorkSession session) {
        String prefix = "GJ-" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        return rows.keySet().stream().filter(code -> code.startsWith(prefix)).sorted().toList();
    }
}
