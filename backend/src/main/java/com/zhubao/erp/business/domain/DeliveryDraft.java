package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.util.regex.Pattern;

/**
 * 独立交付草稿头。编号与加工单号分开，一张草稿只含一种业务、一个客户。
 */
public record DeliveryDraft(
        String deliveryId,
        String requestId,
        long customerId,
        BusinessKind businessKind,
        String logisticsNote,
        String remark,
        DeliveryDraftStatus status,
        int version
) {
    private static final Pattern DELIVERY_ID = Pattern.compile("^FH-\\d{8}-\\d{4}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    public DeliveryDraft {
        if (deliveryId == null || !DELIVERY_ID.matcher(deliveryId).matches()) {
            throw new BusinessException("交付单号无效");
        }
        String request = requestId == null ? "" : requestId.trim();
        if (!REQUEST_ID.matcher(request).matches()) {
            throw new BusinessException("请求编号无效");
        }
        requestId = request;
        if (customerId <= 0) {
            throw new BusinessException("交付草稿缺少客户");
        }
        if (businessKind == null) {
            throw new BusinessException("交付草稿缺少业务种类");
        }
        logisticsNote = bounded(logisticsNote, 200, "物流说明过长");
        remark = bounded(remark, 500, "备注过长");
        if (status != DeliveryDraftStatus.DRAFT) {
            throw new BusinessException("交付草稿状态不正确");
        }
        if (version < 1) {
            throw new BusinessException("交付草稿版本不正确");
        }
    }

    private static String bounded(String value, int max, String tooLong) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) {
            throw new BusinessException(tooLong);
        }
        return text;
    }
}
