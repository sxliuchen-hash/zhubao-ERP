package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FixedAssetStocktake;
import com.zhubao.erp.finance.domain.FixedAssetStocktakeRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.fixed-asset-stocktake.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryFixedAssetStocktakeRepository implements FixedAssetStocktakeRepository {

    private final ConcurrentHashMap<String, FixedAssetStocktake> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(FixedAssetStocktake stocktake, WorkSession session) {
        if (rows.putIfAbsent(stocktake.stocktakeId(), stocktake) != null) {
            throw new BusinessException("盘点单已存在");
        }
        if (session != null) {
            session.onRollback(() -> rows.remove(stocktake.stocktakeId(), stocktake));
        }
    }

    @Override
    public Optional<FixedAssetStocktake> find(String stocktakeId) {
        return Optional.ofNullable(rows.get(stocktakeId));
    }

    @Override
    public void count(String stocktakeId, String seen, String location, String custodian, String exceptionText,
                      long expectedVersion, WorkSession session) {
        replace(stocktakeId, expectedVersion, current -> {
            if (!FixedAssetStocktake.OPEN.equals(current.status())) {
                throw new BusinessException("盘点已经确认");
            }
            return new FixedAssetStocktake(
                    current.stocktakeId(), current.assetCode(), current.countedAt(), current.bookLocation(),
                    current.bookCustodian(), current.bookStatus(), current.bookVersion(), seen, location, custodian,
                    exceptionText, current.status(), current.version() + 1);
        }, session);
    }

    @Override
    public void confirm(String stocktakeId, long expectedVersion, WorkSession session) {
        replace(stocktakeId, expectedVersion, current -> {
            if (!FixedAssetStocktake.OPEN.equals(current.status()) || current.seen() == null) {
                throw new BusinessException(current.seen() == null ? "请先登记实盘" : "盘点已经确认");
            }
            return new FixedAssetStocktake(
                    current.stocktakeId(), current.assetCode(), current.countedAt(), current.bookLocation(),
                    current.bookCustodian(), current.bookStatus(), current.bookVersion(), current.seen(),
                    current.countLocation(), current.countCustodian(), current.exceptionText(),
                    FixedAssetStocktake.CONFIRMED, current.version() + 1);
        }, session);
    }

    private void replace(String stocktakeId, long expectedVersion, java.util.function.UnaryOperator<FixedAssetStocktake> next,
                         WorkSession session) {
        FixedAssetStocktake current = rows.get(stocktakeId);
        if (current == null || current.version() != expectedVersion) {
            throw new BusinessException("盘点单已被其他人修改，请刷新后重试");
        }
        FixedAssetStocktake updated = next.apply(current);
        if (!rows.replace(stocktakeId, current, updated)) {
            throw new BusinessException("盘点单已被其他人修改，请刷新后重试");
        }
        if (session != null) {
            session.onRollback(() -> rows.put(stocktakeId, current));
        }
    }
}
