package com.zhubao.erp.scan.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.domain.ScanResolveRequest;
import com.zhubao.erp.scan.domain.ScanResolveResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private final ScanCodeParser scanCodeParser;

    public ScanController(ScanCodeParser scanCodeParser) {
        this.scanCodeParser = scanCodeParser;
    }

    @PostMapping("/resolve")
    public ApiResponse<ScanResolveResponse> resolve(@Valid @RequestBody ScanResolveRequest request) {
        return ApiResponse.ok(scanCodeParser.resolve(request.content(), request.scene()));
    }
}
