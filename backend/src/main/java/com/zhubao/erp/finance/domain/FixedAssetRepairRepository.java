package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/** 资产维修记录。确认费用时才写入唯一应付号。 */
public interface FixedAssetRepairRepository {

    void insert(FixedAssetRepair repair, WorkSession session);

    Optional<FixedAssetRepair> find(String repairId);

    Optional<FixedAssetRepair> lock(String repairId, WorkSession session);

    void confirm(String repairId, String evidenceCode, String payableCode, WorkSession session);

    List<FixedAssetRepair> list(String assetCode);

    void savePayment(String repairId, FixedAssetRepairPayment payment, WorkSession session);

    List<FixedAssetRepairPayment> payments(String repairId);
}
