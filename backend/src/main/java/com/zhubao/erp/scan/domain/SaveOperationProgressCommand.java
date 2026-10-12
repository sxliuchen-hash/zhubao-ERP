package com.zhubao.erp.scan.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 保存过程说明。操作人只取当前会话，姓名、岗位和完成状态即使传来也忽略。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveOperationProgressCommand(
        String targetType,
        String targetCode,
        String motherOrderCode,
        String stage,
        String process,
        String clauseNo,
        String note,
        String requestId
) {
}
