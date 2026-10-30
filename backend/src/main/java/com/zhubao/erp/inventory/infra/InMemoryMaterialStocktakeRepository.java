package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.MaterialStocktake;
import com.zhubao.erp.inventory.domain.MaterialStocktakeRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.material-stocktake.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryMaterialStocktakeRepository implements MaterialStocktakeRepository {

    private final ConcurrentHashMap<String, MaterialStocktake> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(MaterialStocktake stocktake, WorkSession session) {
        if (rows.putIfAbsent(stocktake.stocktakeId(), stocktake) != null) {
            throw new BusinessException("盘点单已存在");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(stocktake.stocktakeId(), stocktake));
        }
    }

    @Override
    public Optional<MaterialStocktake> find(String stocktakeId) {
        return Optional.ofNullable(rows.get(stocktakeId));
    }

    @Override
    public void count(String stocktakeId, BigDecimal countedQuantity, int expectedVersion, WorkSession session) {
        replace(stocktakeId, expectedVersion, current -> {
            if (MaterialStocktake.CONFIRMED.equals(current.status())) {
                throw new BusinessException("盘点已经确认");
            }
            return new MaterialStocktake(
                    current.stocktakeId(), current.countedAt(), current.itemId(), current.batchCode(), current.locationCode(),
                    current.bookQuantity(), countedQuantity, current.status(), current.version() + 1);
        }, session);
    }

    @Override
    public void confirm(String stocktakeId, int expectedVersion, WorkSession session) {
        replace(stocktakeId, expectedVersion, current -> {
            if (current.countedQuantity() == null) {
                throw new BusinessException("请先填写实盘数量");
            }
            if (MaterialStocktake.CONFIRMED.equals(current.status())) {
                throw new BusinessException("盘点已经确认");
            }
            return new MaterialStocktake(
                    current.stocktakeId(), current.countedAt(), current.itemId(), current.batchCode(), current.locationCode(),
                    current.bookQuantity(), current.countedQuantity(), MaterialStocktake.CONFIRMED, current.version() + 1);
        }, session);
    }

    private void replace(String stocktakeId, int expectedVersion, java.util.function.UnaryOperator<MaterialStocktake> next, WorkSession session) {
        synchronized (rows) {
            MaterialStocktake current = rows.get(stocktakeId);
            if (current == null) {
                throw new BusinessException("盘点单不存在");
            }
            if (current.version() != expectedVersion) {
                throw new BusinessException("盘点单已被其他人修改，请刷新后重试");
            }
            MaterialStocktake updated = next.apply(current);
            rows.put(stocktakeId, updated);
            if (session != null) {
                session.onRollback(() -> {
                    synchronized (rows) {
                        rows.put(stocktakeId, current);
                    }
                });
            }
        }
    }
}
