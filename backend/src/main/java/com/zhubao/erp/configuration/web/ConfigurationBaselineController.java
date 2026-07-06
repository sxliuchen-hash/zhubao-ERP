package com.zhubao.erp.configuration.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.configuration.app.ConfigurationBaselineService;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.BackupPolicy;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.CodeRule;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.ComponentBaseline;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.ConfigurationBaselineSummary;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.ProcessTemplate;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.RolePermissionBaseline;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.SystemParameter;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.VideoParameter;
import com.zhubao.erp.configuration.domain.ConfigurationBaselineData.WarehouseState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/configuration/baseline")
public class ConfigurationBaselineController {

    private final ConfigurationBaselineService configurationBaselineService;

    public ConfigurationBaselineController(ConfigurationBaselineService configurationBaselineService) {
        this.configurationBaselineService = configurationBaselineService;
    }

    @GetMapping("/summary")
    public ApiResponse<ConfigurationBaselineSummary> summary() {
        return ApiResponse.ok(configurationBaselineService.summary());
    }

    @GetMapping("/system-parameters")
    public ApiResponse<List<SystemParameter>> systemParameters() {
        return ApiResponse.ok(configurationBaselineService.systemParameters());
    }

    @GetMapping("/code-rules")
    public ApiResponse<List<CodeRule>> codeRules() {
        return ApiResponse.ok(configurationBaselineService.codeRules());
    }

    @GetMapping("/role-permissions")
    public ApiResponse<List<RolePermissionBaseline>> rolePermissions() {
        return ApiResponse.ok(configurationBaselineService.rolePermissions());
    }

    @GetMapping("/process-templates")
    public ApiResponse<List<ProcessTemplate>> processTemplates() {
        return ApiResponse.ok(configurationBaselineService.processTemplates());
    }

    @GetMapping("/warehouse-states")
    public ApiResponse<List<WarehouseState>> warehouseStates() {
        return ApiResponse.ok(configurationBaselineService.warehouseStates());
    }

    @GetMapping("/video-parameters")
    public ApiResponse<List<VideoParameter>> videoParameters() {
        return ApiResponse.ok(configurationBaselineService.videoParameters());
    }

    @GetMapping("/components")
    public ApiResponse<List<ComponentBaseline>> components() {
        return ApiResponse.ok(configurationBaselineService.components());
    }

    @GetMapping("/backup-policies")
    public ApiResponse<List<BackupPolicy>> backupPolicies() {
        return ApiResponse.ok(configurationBaselineService.backupPolicies());
    }
}
