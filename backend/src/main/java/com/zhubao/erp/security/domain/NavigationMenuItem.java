package com.zhubao.erp.security.domain;

import java.util.List;

public record NavigationMenuItem(
        String code,
        String label,
        String module,
        String route,
        List<String> buttonCodes,
        List<NavigationMenuItem> children
) {
    public NavigationMenuItem {
        buttonCodes = List.copyOf(buttonCodes);
        children = List.copyOf(children);
    }
}
