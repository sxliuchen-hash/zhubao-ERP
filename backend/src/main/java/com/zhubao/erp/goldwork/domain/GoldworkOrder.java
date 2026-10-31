package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.common.error.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * 独立金工单。路线快照只含金工节点，抛光（金）与抛光（珠）分开。
 */
public record GoldworkOrder(
        String goldworkCode,
        long customerId,
        String customerName,
        GoldworkMaterialIntent materialIntent,
        String requirement,
        String ownerName,
        String routeSteps,
        GoldworkOrderStatus status,
        long version,
        String openedBy,
        String openedOn
) {
    public GoldworkOrder {
        BusinessTargetReference reference = BusinessTargetReference.fromCode(goldworkCode);
        if (reference.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("金工单必须使用金工单号");
        }
        goldworkCode = reference.code();
        if (customerId <= 0) {
            throw new BusinessException("请选择客户");
        }
        customerName = limited(customerName, "客户名称不能为空", "客户名称超过可保存长度", 80);
        if (materialIntent == null) {
            throw new BusinessException("请选择来料或公司自备");
        }
        requirement = limited(requirement, "请填写金工要求", "金工要求超过可保存长度", 500);
        ownerName = limited(ownerName, "请填写负责人", "负责人超过可保存长度", 40);
        routeSteps = canonicalRoute(routeSteps);
        if (status == null) {
            throw new BusinessException("金工单状态不能为空");
        }
        if (version < 1) {
            throw new BusinessException("金工单版本无效");
        }
        openedBy = limited(openedBy, "操作人不能为空", "操作人超过可保存长度", 64);
        openedOn = openedOn == null ? "" : openedOn.trim();
        if (!openedOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("建单日期无效");
        }
    }

    public BusinessTargetReference reference() {
        return BusinessTargetReference.fromCode(goldworkCode);
    }

    public List<ProcessStep> route() {
        List<ProcessStep> steps = new ArrayList<>();
        for (String id : routeSteps.split(",")) {
            steps.add(ProcessCatalog.require(id));
        }
        return List.copyOf(steps);
    }

    /** 空路线使用当前金工默认顺序，并立刻冻结。 */
    public static String canonicalRoute(String raw) {
        String text = raw == null ? "" : raw.trim();
        List<ProcessStep> allowed = ProcessCatalog.defaults(BusinessKind.GOLDWORK, "");
        if (text.isEmpty()) {
            return join(allowed);
        }
        List<String> ids = new ArrayList<>();
        for (String part : text.split(",")) {
            String id = part.trim();
            if (id.isEmpty()) {
                continue;
            }
            ProcessStep step;
            try {
                step = ProcessCatalog.require(id);
            } catch (BusinessException ex) {
                throw new BusinessException("金工不使用打样或普通加工工序");
            }
            if (step.kind() != BusinessKind.GOLDWORK || allowed.stream().noneMatch(item -> item.id().equals(step.id()))) {
                throw new BusinessException(step.id().equals("POLISH_BEAD")
                        ? "金工抛光与珠料抛光不同"
                        : "金工不使用打样或普通加工工序");
            }
            if (ids.contains(step.id())) {
                throw new BusinessException("金工路线不能重复节点");
            }
            ids.add(step.id());
        }
        if (ids.isEmpty()) {
            throw new BusinessException("请选择金工路线");
        }
        if (!ids.contains(ProcessCatalog.POLISH_GOLD.id())) {
            throw new BusinessException("金工路线必须包含抛光（金）");
        }
        return String.join(",", ids);
    }

    private static String join(List<ProcessStep> steps) {
        return String.join(",", steps.stream().map(ProcessStep::id).toList());
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
