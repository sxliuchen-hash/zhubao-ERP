package com.zhubao.erp.hardware.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.hardware.app.HardwareIntegrationService;
import com.zhubao.erp.hardware.domain.HardwareAdapterPlan;
import com.zhubao.erp.hardware.domain.HardwareDevice;
import com.zhubao.erp.hardware.domain.HardwarePocEvidenceSummary;
import com.zhubao.erp.hardware.domain.HardwarePocRecord;
import com.zhubao.erp.hardware.domain.IntegrationTask;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hardware")
public class HardwareIntegrationController {

    private final HardwareIntegrationService hardwareIntegrationService;

    public HardwareIntegrationController(HardwareIntegrationService hardwareIntegrationService) {
        this.hardwareIntegrationService = hardwareIntegrationService;
    }

    @GetMapping("/devices")
    public ApiResponse<List<HardwareDevice>> devices() {
        return ApiResponse.ok(hardwareIntegrationService.findDevices());
    }

    @GetMapping("/integration-tasks")
    public ApiResponse<List<IntegrationTask>> integrationTasks() {
        return ApiResponse.ok(hardwareIntegrationService.findTasks());
    }

    @GetMapping("/adapter-plans")
    public ApiResponse<List<HardwareAdapterPlan>> adapterPlans() {
        return ApiResponse.ok(hardwareIntegrationService.findAdapterPlans());
    }

    @GetMapping("/poc-records")
    public ApiResponse<List<HardwarePocRecord>> pocRecords() {
        return ApiResponse.ok(hardwareIntegrationService.findPocRecords());
    }

    @GetMapping("/poc-evidence")
    public ApiResponse<List<HardwarePocEvidenceSummary>> pocEvidence() {
        return ApiResponse.ok(hardwareIntegrationService.findPocEvidence());
    }

    @GetMapping("/summary")
    public ApiResponse<HardwareSummary> summary() {
        return ApiResponse.ok(new HardwareSummary(
                hardwareIntegrationService.findDevices().size(),
                hardwareIntegrationService.fallbackRequiredCount(),
                hardwareIntegrationService.averageHealth(),
                hardwareIntegrationService.findPocRecords().size(),
                hardwareIntegrationService.blockedPocCount()
        ));
    }

    public record HardwareSummary(
            int totalDevices,
            long fallbackRequired,
            int averageHealth,
            int pocRecords,
            long pocBlocked
    ) {
    }
}
