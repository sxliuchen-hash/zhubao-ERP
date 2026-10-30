package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;

public interface PortionInspectionRepository {

    void save(PortionInspection inspection, WorkSession session);

    List<PortionInspection> listByBag(String bagId);
}
