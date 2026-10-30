package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 袋只记录同一目标、材质、要求和来源下的份额。关闭的父袋不计入可办理量。
 */
public class ActivityBagService {

    private final ActivityBagRepository repository;

    public ActivityBagService(ActivityBagRepository repository) {
        this.repository = repository;
    }

    public ActivityBag open(String targetCode, String materialCode, String requirementVersion, String sourceKey,
                            BigDecimal quantity, StockMeasurement.Weight weight, WorkSession session) {
        ActivityBag bag = new ActivityBag(
                newId(), required(targetCode), required(materialCode), required(requirementVersion), required(sourceKey),
                "", false, quantity, weight, 1);
        repository.save(bag, 0, session);
        return bag;
    }

    public List<ActivityBag> activeLeaves(String targetCode) {
        return repository.listByTarget(targetCode).stream().filter(ActivityBag::activeLeaf).toList();
    }

    public ActivityBag close(String id, WorkSession session) {
        ActivityBag current = repository.find(id).orElseThrow(() -> new BusinessException("袋不存在"));
        ActivityBag closed = new ActivityBag(
                current.id(), current.targetCode(), current.materialCode(), current.requirementVersion(),
                current.sourceKey(), current.parentId(), true, current.quantity(), current.weight(), current.version() + 1);
        repository.save(closed, current.version(), session);
        return closed;
    }

    private static String newId() {
        return "BAG-" + UUID.randomUUID();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("袋缺少目标、材质、要求或来源");
        }
        return value.trim();
    }
}
