package com.zhubao.erp.register.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.register.app.RegisterImportConfirmationService;
import com.zhubao.erp.register.app.RegisterImportPostingExecutionService;
import com.zhubao.erp.register.app.RegisterImportPostingPlanService;
import com.zhubao.erp.register.app.RegisterImportPreviewService;
import com.zhubao.erp.register.app.RegisterImportTemplateService;
import com.zhubao.erp.register.domain.ConfirmRegisterImportCommand;
import com.zhubao.erp.register.domain.CorrectRegisterImportRowCommand;
import com.zhubao.erp.register.domain.PostRegisterImportBatchCommand;
import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportPostingExecutionResult;
import com.zhubao.erp.register.domain.RegisterImportPostingPlan;
import com.zhubao.erp.register.domain.RegisterImportPreview;
import com.zhubao.erp.register.domain.RegisterImportTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/register-import")
public class RegisterImportController {

    private final RegisterImportTemplateService templateService;
    private final RegisterImportPreviewService previewService;
    private final RegisterImportConfirmationService confirmationService;
    private final RegisterImportPostingPlanService postingPlanService;
    private final RegisterImportPostingExecutionService postingExecutionService;

    public RegisterImportController(
            RegisterImportTemplateService templateService,
            RegisterImportPreviewService previewService,
            RegisterImportConfirmationService confirmationService,
            RegisterImportPostingPlanService postingPlanService,
            RegisterImportPostingExecutionService postingExecutionService
    ) {
        this.templateService = templateService;
        this.previewService = previewService;
        this.confirmationService = confirmationService;
        this.postingPlanService = postingPlanService;
        this.postingExecutionService = postingExecutionService;
    }

    @GetMapping("/templates")
    public ApiResponse<List<RegisterImportTemplate>> templates() {
        return ApiResponse.ok(templateService.findTemplates());
    }

    @GetMapping("/summary")
    public ApiResponse<RegisterImportTemplateService.RegisterImportSummary> summary() {
        return ApiResponse.ok(templateService.summary());
    }

    @PostMapping(value = "/previews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RegisterImportPreview> preview(
            @RequestParam String templateCode,
            @RequestParam(required = false) String businessDate,
            @RequestParam MultipartFile file
    ) throws IOException {
        return ApiResponse.ok(previewService.preview(
                templateCode,
                businessDate,
                file.getOriginalFilename(),
                file.getBytes()
        ));
    }

    @PostMapping("/batches/confirm")
    public ApiResponse<RegisterImportBatch> confirm(@RequestBody ConfirmRegisterImportCommand command) {
        return ApiResponse.ok(confirmationService.confirm(command));
    }

    @GetMapping("/batches")
    public ApiResponse<List<RegisterImportBatch>> batches(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(confirmationService.recentBatches(limit));
    }

    @GetMapping("/batches/{importBatchNo}")
    public ApiResponse<RegisterImportBatch> batch(@PathVariable String importBatchNo) {
        return ApiResponse.ok(confirmationService.batch(importBatchNo));
    }

    @PostMapping("/batches/{importBatchNo}/rows/{sourceLineNo}/corrections")
    public ApiResponse<RegisterImportBatch> correctRowFields(
            @PathVariable String importBatchNo,
            @PathVariable int sourceLineNo,
            @RequestBody CorrectRegisterImportRowCommand command
    ) {
        return ApiResponse.ok(confirmationService.correctRowFields(importBatchNo, sourceLineNo, command));
    }

    @GetMapping("/batches/{importBatchNo}/posting-plan")
    public ApiResponse<RegisterImportPostingPlan> postingPlan(@PathVariable String importBatchNo) {
        return ApiResponse.ok(postingPlanService.plan(importBatchNo));
    }

    @PostMapping("/batches/{importBatchNo}/posting-commands")
    public ApiResponse<RegisterImportPostingExecutionResult> postBatch(
            @PathVariable String importBatchNo,
            @RequestBody(required = false) PostRegisterImportBatchCommand command
    ) {
        return ApiResponse.ok(postingExecutionService.post(importBatchNo, command));
    }
}
