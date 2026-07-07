package com.zhubao.erp.register.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.register.app.RegisterImportPreviewService;
import com.zhubao.erp.register.app.RegisterImportTemplateService;
import com.zhubao.erp.register.domain.RegisterImportPreview;
import com.zhubao.erp.register.domain.RegisterImportTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public RegisterImportController(
            RegisterImportTemplateService templateService,
            RegisterImportPreviewService previewService
    ) {
        this.templateService = templateService;
        this.previewService = previewService;
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
}
