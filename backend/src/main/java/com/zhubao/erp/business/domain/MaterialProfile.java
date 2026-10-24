package com.zhubao.erp.business.domain;

import java.util.List;

public record MaterialProfile(String materialCode, String name, String aliases,
                              String remark, boolean enabled, List<String> storageConditions, String storageInstruction) {
    public MaterialProfile(String materialCode, String name, String aliases, String remark, boolean enabled) {
        this(materialCode, name, aliases, remark, enabled, null, null);
    }
    public MaterialProfile { if (storageConditions != null) storageConditions = List.copyOf(storageConditions); }
}
