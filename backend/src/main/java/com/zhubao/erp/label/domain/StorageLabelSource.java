package com.zhubao.erp.label.domain;

import com.zhubao.erp.business.domain.StorageRequirements;
import java.util.List;

/** 独立保存标识载荷，来源及完整说明跟随业务快照冻结。 */
public record StorageLabelSource(String sourceCode, String sourceLabel, String materialCode,
                                 boolean recorded, List<String> marks, String instruction) {
    public StorageLabelSource { marks = marks == null ? List.of() : List.copyOf(marks); instruction = instruction == null ? "" : instruction; }
    public boolean required() { return !marks.isEmpty() || !instruction.isBlank(); }
    public static StorageLabelSource of(String sourceCode, String sourceLabel, String material, StorageRequirements storage) {
        List<String> codes = List.of("COLD", "FROZEN", "VACUUM", "DARK", "DRY");
        List<String> marks = List.of("冷", "冻", "真", "光", "潮");
        return new StorageLabelSource(sourceCode, sourceLabel, material, storage != null,
                storage == null ? List.of() : storage.conditions().stream().map(code -> marks.get(codes.indexOf(code))).toList(), storage == null ? "" : storage.instruction());
    }
    public static double height(List<StorageLabelSource> sources, double width) {
        int chars = Math.max(1, (int)((width - 3) / 2.2));
        return sources.stream().filter(StorageLabelSource::required).mapToDouble(source -> 5.0 + (source.instruction().isBlank() ? 0 : Math.ceil((double)source.instruction().length() / chars) * 3.0)).sum();
    }
}
