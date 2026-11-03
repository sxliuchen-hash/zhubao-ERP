package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 打样单仓储。不保存第二套实物，也不保存普通加工母单。 */
public interface SamplingOrderRepository {

    void insert(SamplingOrder order, WorkSession session);

    Optional<SamplingOrder> find(String samplingCode);

    List<SamplingOrder> list();

    /** 锁住当天已有打样号，供同一事务分配下一个流水。 */
    List<String> lockDay(LocalDate day, WorkSession session);

    /** 按原版本替换状态。版本不一致时拒绝，不覆盖其他字段。 */
    void replace(SamplingOrder order, long expectedVersion, WorkSession session);
}
