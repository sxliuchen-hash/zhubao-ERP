package com.zhubao.erp.business.domain;

/**
 * 工作台轻任务。可挂客户或母单，但不写工序、库存、质检或发货。
 */
public record DeskTask(
        String taskCode,
        DeskTaskKind kind,
        String title,
        String note,
        Long customerId,
        String customerName,
        String motherOrderCode,
        String assigneeUsername,
        String assigneeName,
        String dueDate,
        DeskTaskStatus status,
        String creatorUsername,
        String createdAt,
        String requestId,
        int version,
        String completedAt,
        String completedBy,
        String resultNote,
        String completeRequestId,
        String cancelledAt,
        String cancelledBy,
        String cancelReason,
        String cancelRequestId
) {
    public DeskTask {
        taskCode = text(taskCode);
        title = text(title);
        note = text(note);
        customerName = text(customerName);
        motherOrderCode = text(motherOrderCode);
        assigneeUsername = text(assigneeUsername);
        assigneeName = text(assigneeName);
        dueDate = text(dueDate);
        creatorUsername = text(creatorUsername);
        createdAt = text(createdAt);
        requestId = text(requestId);
        completedAt = text(completedAt);
        completedBy = text(completedBy);
        resultNote = text(resultNote);
        completeRequestId = text(completeRequestId);
        cancelledAt = text(cancelledAt);
        cancelledBy = text(cancelledBy);
        cancelReason = text(cancelReason);
        cancelRequestId = text(cancelRequestId);
        if (kind == null) {
            throw new IllegalArgumentException("待办类型不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("待办状态不能为空");
        }
    }

    public DeskTask withCode(String code) {
        return new DeskTask(code, kind, title, note, customerId, customerName, motherOrderCode,
                assigneeUsername, assigneeName, dueDate, status, creatorUsername, createdAt, requestId, version,
                completedAt, completedBy, resultNote, completeRequestId, cancelledAt, cancelledBy, cancelReason,
                cancelRequestId);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
