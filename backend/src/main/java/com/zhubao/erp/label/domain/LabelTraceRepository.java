package com.zhubao.erp.label.domain;

import java.util.List;

public interface LabelTraceRepository {

    void save(LabelTrace trace);

    List<LabelTrace> listByMother(String motherOrderCode);
}