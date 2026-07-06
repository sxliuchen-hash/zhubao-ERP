package com.zhubao.erp.approval.web;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalDecisionCommand;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ApprovalTask>> tasks() {
        return ApiResponse.ok(approvalService.findAll());
    }

    @GetMapping("/tasks/pending")
    public ApiResponse<List<ApprovalTask>> pendingTasks() {
        return ApiResponse.ok(approvalService.findPending());
    }

    @GetMapping("/tasks/{approvalCode}")
    public ApiResponse<ApprovalTask> task(@PathVariable String approvalCode) {
        return ApiResponse.ok(approvalService.findByCode(approvalCode));
    }

    @PostMapping("/tasks/{approvalCode}/decisions")
    public ApiResponse<ApprovalTask> decide(
            @PathVariable String approvalCode,
            @Valid @RequestBody ApprovalDecisionCommand command
    ) {
        return ApiResponse.ok(approvalService.decide(approvalCode, command));
    }
}
