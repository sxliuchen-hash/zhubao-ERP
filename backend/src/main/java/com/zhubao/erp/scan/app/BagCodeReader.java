package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessTargetReference;

import java.util.List;

/**
 * 旧单号和袋号的读取。多袋必须选择，关闭的父袋不能再被当成可办理袋。
 */
public final class BagCodeReader {

    public record Decision(String directBagId, List<String> choices, boolean closedParent) {
    }

    private BagCodeReader() {
    }

    public static Decision read(ActivityBagRepository repository, String code) {
        String businessCode = businessCode(code);
        ActivityBag scanned = repository.find(businessCode).orElse(null);
        String target = scanned == null ? businessCode : scanned.targetCode();
        List<String> leaves = repository.listByTarget(target).stream()
                .filter(ActivityBag::activeLeaf)
                .map(ActivityBag::id)
                .sorted()
                .toList();
        if (leaves.isEmpty()) {
            return null;
        }
        if (scanned != null && scanned.activeLeaf()) {
            return new Decision(scanned.id(), List.of(), false);
        }
        if (scanned != null && scanned.closed()) {
            return new Decision(null, leaves, true);
        }
        if (leaves.size() == 1) {
            return new Decision(leaves.get(0), List.of(), false);
        }
        return new Decision(null, leaves, false);
    }

    public static BusinessTargetReference reference(String code) {
        return BusinessTargetReference.parse(businessCode(code));
    }

    private static String businessCode(String code) {
        String text = code == null ? "" : code.trim();
        if (text.startsWith("JG|")) {
            String[] parts = text.split("\\|", -1);
            return parts.length >= 3 ? parts[2] : text;
        }
        return text;
    }
}
