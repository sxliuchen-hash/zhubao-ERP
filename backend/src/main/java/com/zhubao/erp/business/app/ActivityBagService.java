package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 袋只记录同一目标、材质、要求和来源下的份额。关闭的父袋不计入可办理量。
 */
@Service
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

    public List<ActivityBag> openBySource(String sourceKey) {
        return bagsOfSource(sourceKey).stream().filter(ActivityBag::activeLeaf).toList();
    }

    /** 含已关闭的父袋。汇总来源占用时不能只看还开着的子袋。 */
    public List<ActivityBag> bagsOfSource(String sourceKey) {
        return repository.listBySource(sourceKey);
    }

    public void lockSource(String sourceKey, WorkSession session) {
        repository.lockSource(sourceKey, session);
    }

    /**
     * 分出的子袋使用新的袋号，长度与自动袋号相同，避免在原号后追加序号超出字段。
     * 父子关系记在父袋号上。子袋不复制原袋重量。
     */
    public ActivityBag openShare(ActivityBag parent, BigDecimal quantity, WorkSession session) {
        if (parent == null || parent.id() == null || parent.id().isBlank() || parent.id().length() > 40) {
            throw new BusinessException("袋号超出可保存长度");
        }
        String childId = newId();
        if (childId.length() > 40) {
            throw new BusinessException("袋号超出可保存长度");
        }
        ActivityBag child = new ActivityBag(
                childId, parent.targetCode(), parent.materialCode(), parent.requirementVersion(),
                parent.sourceKey(), parent.id(), false, quantity, StockMeasurement.unknown(), 1);
        repository.save(child, 0, session);
        return child;
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
