package com.zhubao.erp.sampling.domain;

/** 母单上的可选样品引用。没有关联时 linked 为 false，不阻止原登记和打印。 */
public record SamplingProductionLinkView(
        boolean linked,
        String motherCode,
        String samplingCode,
        String versionKind,
        int versionNo,
        String snapshotText,
        String linkedOn
) {
    public static SamplingProductionLinkView absent(String motherCode) {
        return new SamplingProductionLinkView(false, motherCode, "", "", 0, "", "");
    }
}
