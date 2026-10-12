package com.zhubao.erp.document.domain;

/**
 * 把已上传附件绑到一次真实操作。操作人取登录会话，不接受客户端姓名。
 */
public record BindEvidenceCommand(
        String attachmentCode,
        String targetType,
        String targetCode,
        String stage,
        String scene,
        String operationType,
        String operationCode,
        String processName,
        String clauseNo
) {
}
