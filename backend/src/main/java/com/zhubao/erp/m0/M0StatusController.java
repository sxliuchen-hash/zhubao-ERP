package com.zhubao.erp.m0;

import com.zhubao.erp.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/m0")
public class M0StatusController {

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "phase", "M0",
                "architecture", "frontend-backend-separated",
                "backend", "Spring Boot 3 / Java 17",
                "frontend", "Vue 3 / PWA",
                "storageDefault", "NAS first, S3-compatible optional",
                "capabilities", List.of("scan", "inventory-ledger", "video-task", "label-data", "rbac-foundation")
        ));
    }
}
