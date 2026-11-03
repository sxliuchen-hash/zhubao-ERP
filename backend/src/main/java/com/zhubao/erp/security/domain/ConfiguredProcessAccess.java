package com.zhubao.erp.security.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.security.error.PermissionDeniedException;

import java.util.List;

/** 账号勾了打样或金工的具体工序时，只允许报这些工序；一个都不勾则该页面里的工序都能做。 */
public final class ConfiguredProcessAccess {

    private ConfiguredProcessAccess() {
    }

    public static void assertStep(OperatorContext actor, ProcessStep step) {
        if (actor.role() == RoleCode.ADMIN) {
            return;
        }
        String prefix = prefix(step.kind());
        if (prefix.isEmpty()) {
            return;
        }
        List<String> selected = actor.allowedProcesses().stream().filter(name -> name.startsWith(prefix)).toList();
        if (selected.isEmpty() || selected.contains(prefix + step.name())) {
            return;
        }
        throw new PermissionDeniedException("没有「" + step.name() + "」工序权限");
    }

    private static String prefix(BusinessKind kind) {
        if (kind == BusinessKind.SAMPLING) {
            return "打样/";
        }
        if (kind == BusinessKind.GOLDWORK) {
            return "金工/";
        }
        return "";
    }
}
