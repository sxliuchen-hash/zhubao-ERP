package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;

/** 资产处置事实。不负责删除卡片或生成收款。 */
public interface FixedAssetDispositionRepository {

    void insert(FixedAssetDisposition disposition, WorkSession session);

    boolean coversReceipt(String receiptCode, WorkSession session);

    List<FixedAssetDisposition> list(String assetCode);
}
