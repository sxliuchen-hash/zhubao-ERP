package com.zhubao.erp.label.domain;

/** 同版补打、换版、出纸核实或实物换贴。不表示打印机已经出纸。 */
public record LabelTrace(
        String id,
        String motherOrderCode,
        String businessCode,
        String eventType,
        String versionNo,
        String instanceNo,
        String printJobId,
        String reason,
        String operatorName,
        String createdAt
) {
}