package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Component;

/** 内部交付按所有真实来源授权，持公开码不授予内部访问。 */
@Component
public class DeliveryDataAccess {
    private final DeliveryDraftRepository drafts;
    private final BusinessDataAccess access;
    public DeliveryDataAccess(DeliveryDraftRepository drafts, BusinessDataAccess access) { this.drafts = drafts; this.access = access; }
    public DeliveryDraft require(OperatorContext actor, String id) {
        if (actor.role() == RoleCode.CUSTOMER_PORTAL) throw new PermissionDeniedException("客户账号不能进入内部交付办理");
        DeliveryDraft draft = drafts.find(id).orElseThrow(() -> new BusinessException("交付不存在"));
        if (drafts.lines(id).isEmpty()) throw new BusinessException("交付缺少实际来源");
        access.assertCustomer(actor,draft.customerId(),drafts.lines(id).get(0).customerName());
        for (DeliveryDraftLine line : drafts.lines(id)) access.assertCanRead(actor, line.targetCode());
        return draft;
    }
}
