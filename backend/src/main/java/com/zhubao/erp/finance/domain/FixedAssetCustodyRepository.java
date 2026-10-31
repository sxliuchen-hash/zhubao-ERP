package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;

/** 资产交接历史。只追加，不覆盖原卡片金额。 */
public interface FixedAssetCustodyRepository {

    void insert(FixedAssetCustodyTransfer transfer, WorkSession session);

    List<FixedAssetCustodyTransfer> list(String assetCode);
}
