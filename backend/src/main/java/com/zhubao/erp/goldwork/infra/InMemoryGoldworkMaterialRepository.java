package com.zhubao.erp.goldwork.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLine;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.goldwork-material.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryGoldworkMaterialRepository implements GoldworkMaterialRepository {

    private final ConcurrentHashMap<String, GoldworkMaterialLine> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(GoldworkMaterialLine line, WorkSession session) {
        GoldworkMaterialLine previous = rows.putIfAbsent(line.lineId(), line);
        if (previous != null) {
            throw new BusinessException("金料明细冲突，请稍后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(line.lineId(), line));
        }
    }

    @Override
    public Optional<GoldworkMaterialLine> find(String lineId) {
        if (lineId == null || lineId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(lineId.trim()));
    }

    @Override
    public List<GoldworkMaterialLine> list(String goldworkCode) {
        return rows.values().stream()
                .filter(line -> line.goldworkCode().equals(goldworkCode))
                .sorted(Comparator.comparing(GoldworkMaterialLine::lineId))
                .toList();
    }
}
