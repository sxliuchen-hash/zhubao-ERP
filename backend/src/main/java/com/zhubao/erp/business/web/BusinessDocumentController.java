package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.BusinessDocumentService;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business/documents")
public class BusinessDocumentController {

    private final BusinessDocumentService service;

    public BusinessDocumentController(BusinessDocumentService service) {
        this.service = service;
    }

    @GetMapping("/mother-orders/{code}")
    public ApiResponse<MotherOrderRecord> motherOrder(@PathVariable String code) {
        return ApiResponse.ok(service.motherOrder(code));
    }

    @GetMapping("/outbound-orders/{code}")
    public ApiResponse<OutboundOrderRecord> outboundOrder(@PathVariable String code) {
        return ApiResponse.ok(service.outboundOrder(code));
    }
}
