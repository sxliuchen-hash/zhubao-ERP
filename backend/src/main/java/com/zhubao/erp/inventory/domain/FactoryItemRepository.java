package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/** 本厂物品档案。查询不写库存或费用。 */
public interface FactoryItemRepository {

    void insert(FactoryItem item, WorkSession session);

    void updatePolicy(FactoryItem next, int expectedVersion, WorkSession session);

    Optional<FactoryItem> find(String itemId);

    Optional<FactoryItem> findIdentity(FactoryItemCategory category, String materialName, String specCode, String unit);

    List<FactoryItem> list(String materialName);
}
