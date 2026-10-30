package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

/**
 * 交付草稿。保存草稿不写出库、库存或客户账。
 */
public interface DeliveryDraftRepository {

    void save(DeliveryDraft draft, List<DeliveryDraftLine> lines, WorkSession session);

    Optional<DeliveryDraft> find(String deliveryId);

    Optional<DeliveryDraft> findByRequest(String requestId);

    List<DeliveryDraftLine> lines(String deliveryId);

    List<String> listIds(WorkSession session);
}
