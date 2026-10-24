package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import java.util.List;
import java.util.Arrays;

/** 来料保存条件快照；null 快照表示历史未记录，空列表表示已记录但未配置。 */
public record StorageRequirements(List<String> conditions, String instruction) {
    public static final List<String> ORDER = List.of("COLD", "FROZEN", "VACUUM", "DARK", "DRY");
    public StorageRequirements {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        instruction = instruction == null ? "" : instruction.trim();
        if (conditions.stream().anyMatch(value -> !ORDER.contains(value)) || conditions.stream().distinct().count() != conditions.size())
            throw new BusinessException("保存条件包含未知值或重复值");
        if (conditions.contains("COLD") && conditions.contains("FROZEN")) throw new BusinessException("冷藏与冷冻不能同时选择");
        if (instruction.length() > 500) throw new BusinessException("保存说明不能超过 500 字");
        var selected = conditions;
        conditions = ORDER.stream().filter(selected::contains).toList();
    }
    public String encoded() { return String.join("|", conditions); }
    public static StorageRequirements read(String conditions, String instruction) {
        return conditions == null ? null : new StorageRequirements(conditions.isEmpty() ? List.of() : Arrays.asList(conditions.split("\\|")), instruction);
    }
    public static StorageRequirements of(MaterialProfile profile) {
        return profile == null ? null : new StorageRequirements(profile.storageConditions(), profile.storageInstruction());
    }
}
