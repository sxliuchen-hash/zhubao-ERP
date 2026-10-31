package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/** 资产购置来源。不保存第二份原值。 */
public interface FixedAssetAcquisitionRepository {

    void insert(FixedAssetAcquisition acquisition, WorkSession session);

    Optional<FixedAssetAcquisition> find(String acquisitionId);

    Optional<FixedAssetAcquisition> lockByAsset(String assetCode, WorkSession session);

    boolean coversPayable(String payableCode, WorkSession session);

    List<FixedAssetAcquisition> list();
}
