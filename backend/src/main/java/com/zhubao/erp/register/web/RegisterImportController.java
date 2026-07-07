package com.zhubao.erp.register.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.register.app.RegisterImportTemplateService;
import com.zhubao.erp.register.domain.RegisterImportTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/register-import")
public class RegisterImportController {

    private final RegisterImportTemplateService templateService;

    public RegisterImportController(RegisterImportTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/templates")
    public ApiResponse<List<RegisterImportTemplate>> templates() {
        return ApiResponse.ok(templateService.findTemplates());
    }

    @GetMapping("/summary")
    public ApiResponse<RegisterImportTemplateService.RegisterImportSummary> summary() {
        return ApiResponse.ok(templateService.summary());
    }
}
