package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

/**
 * 独立打样单。身份只接受打样单号，不保存普通加工母单号。
 */
public record SamplingOrder(
        String samplingCode,
        long customerId,
        String customerName,
        SamplingMaterialIntent materialIntent,
        String requirement,
        String ownerName,
        SamplingOrderStatus status,
        long version,
        String openedBy,
        String openedOn
) {
    public SamplingOrder {
        BusinessTargetReference reference = BusinessTargetReference.fromCode(samplingCode);
        if (reference.kind() != BusinessKind.SAMPLING) {
            throw new BusinessException("打样单必须使用打样单号");
        }
        samplingCode = reference.code();
        if (customerId <= 0) {
            throw new BusinessException("请选择客户");
        }
        customerName = limited(customerName, "客户名称不能为空", "客户名称超过可保存长度", 80);
        if (materialIntent == null) {
            throw new BusinessException("请选择来料或公司自备");
        }
        requirement = limited(requirement, "请填写样品要求", "样品要求超过可保存长度", 500);
        ownerName = limited(ownerName, "请填写负责人", "负责人超过可保存长度", 40);
        if (status == null) {
            throw new BusinessException("打样单状态不能为空");
        }
        if (version < 1) {
            throw new BusinessException("打样单版本无效");
        }
        openedBy = limited(openedBy, "操作人不能为空", "操作人超过可保存长度", 64);
        openedOn = required(openedOn, "建单日期不能为空");
        if (!openedOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("建单日期无效");
        }
    }

    public BusinessTargetReference reference() {
        return BusinessTargetReference.fromCode(samplingCode);
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }

    private static String limited(String value, String empty, String tooLong, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(empty);
        }
        if (text.length() > max) {
            throw new BusinessException(tooLong);
        }
        return text;
    }
}
