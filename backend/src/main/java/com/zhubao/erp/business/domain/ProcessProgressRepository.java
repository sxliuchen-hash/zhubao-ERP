package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface ProcessProgressRepository {

    Optional<ProcessProgressHead> find(String orderCode, String processName);

    Optional<ProcessProgressHead> find(String orderCode, String processName, WorkSession session);

    List<ProcessProgressHead> listAll();

    void save(ProcessProgressHead head, WorkSession session);

    ProcessProgressEvent append(ProcessProgressEvent event, WorkSession session);

    List<ProcessProgressEvent> history(String orderCode, String processName);
}
