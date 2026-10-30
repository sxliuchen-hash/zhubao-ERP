package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/**
 * 外协收回明细。查询只汇总已保存的收回，不写发外、库存、客户账或工资。
 */
public interface OutsourcingReturnRepository {

    void save(OutsourcingReturn row, WorkSession session);

    Optional<OutsourcingReturn> find(String returnId);

    Optional<OutsourcingReturn> findByRequest(String requestId);

    List<OutsourcingReturn> listByDispatch(String dispatchId);

    boolean inHouse(String bagId);

    Optional<String> currentOutParty(String bagId);
}
