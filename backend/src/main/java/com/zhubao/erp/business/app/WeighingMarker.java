package com.zhubao.erp.business.app;

import com.zhubao.erp.work.domain.WorkSession;

/** 数量变化后的新袋标成待复称。默认什么都不做，避免旧测试被迫写重量。 */
public interface WeighingMarker {

    WeighingMarker NONE = (bagId, session) -> {
    };

    void markPending(String bagId, WorkSession session);
}
