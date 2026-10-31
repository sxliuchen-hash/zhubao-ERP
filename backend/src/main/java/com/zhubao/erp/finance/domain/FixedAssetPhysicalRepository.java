package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

/**
 * 原资产编码上的实物字段。不负责财务原值。
 */
public interface FixedAssetPhysicalRepository {

    Optional<FixedAssetPhysical> find(String assetCode);

    Optional<FixedAssetPhysical> lock(String assetCode, WorkSession session);

    void insert(FixedAssetPhysical row, WorkSession session);

    void replace(FixedAssetPhysical row, long expectedVersion, WorkSession session);
}
