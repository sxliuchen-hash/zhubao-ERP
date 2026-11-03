package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 一次按原发外行收回。实收、未加工退回和已确认损耗分开记。
 * 已确认损耗按数量成立，不要求另附凭据。少回且未确认的仍在外，不能直接发给客户。
 * 未知重量保持未知，不按颗数估算，也不写成 0。
 */
public record OutsourcingReturn(
        String returnId,
        String requestId,
        String dispatchId,
        String partyId,
        int dispatchVersion,
        BigDecimal receivedQty,
        BigDecimal unprocessedQty,
        BigDecimal lossQty,
        StockMeasurement.Weight weight,
        String receivedBagId,
        String unprocessedBagId,
        String lossBagId,
        String remainderBagId
) {
    private static final Pattern RETURN_ID = Pattern.compile("^OR[0-9a-f]{32}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    public OutsourcingReturn {
        if (returnId == null || !RETURN_ID.matcher(returnId).matches()) {
            throw new BusinessException("外协收回编号无效");
        }
        String request = requestId == null ? "" : requestId.trim();
        if (!REQUEST_ID.matcher(request).matches()) {
            throw new BusinessException("请求编号无效");
        }
        requestId = request;
        if (dispatchId == null || dispatchId.isBlank() || dispatchId.length() > 40) {
            throw new BusinessException("外协发外不存在");
        }
        dispatchId = dispatchId.trim();
        partyId = OutsourcingParty.requireId(partyId);
        if (dispatchVersion < 2) {
            throw new BusinessException("外协发外版本不正确");
        }
        receivedQty = quantity(receivedQty);
        unprocessedQty = quantity(unprocessedQty);
        lossQty = quantity(lossQty);
        if (receivedQty.signum() == 0 && unprocessedQty.signum() == 0 && lossQty.signum() == 0) {
            throw new BusinessException("收回数量必须为正");
        }
        weight = weight == null ? StockMeasurement.unknown() : weight;
        receivedBagId = bag(receivedBagId);
        unprocessedBagId = bag(unprocessedBagId);
        lossBagId = bag(lossBagId);
        remainderBagId = bag(remainderBagId);
    }

    private static BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new BusinessException("收回数量不能为负");
        }
        return StockMeasurement.processingScale(value);
    }

    private static String bag(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() > 40) {
            throw new BusinessException("外协任务缺少所发袋");
        }
        return text;
    }
}
