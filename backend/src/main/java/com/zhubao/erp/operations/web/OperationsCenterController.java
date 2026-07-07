package com.zhubao.erp.operations.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.operations.app.OperationsCenterService;
import com.zhubao.erp.operations.domain.OperationsCenterSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
public class OperationsCenterController {

    private final OperationsCenterService operationsCenterService;

    public OperationsCenterController(OperationsCenterService operationsCenterService) {
        this.operationsCenterService = operationsCenterService;
    }

    @GetMapping("/center")
    public ApiResponse<OperationsCenterSnapshot> center() {
        return ApiResponse.ok(operationsCenterService.snapshot());
    }
}
