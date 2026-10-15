package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.MasterDataService;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/master-data")
public class MasterDataController {
    private final MasterDataService service;
    private final OperatorContextResolver operators;
    private final PermissionGuard permissions;

    public MasterDataController(MasterDataService service, OperatorContextResolver operators, PermissionGuard permissions) {
        this.service = service;
        this.operators = operators;
        this.permissions = permissions;
    }

    @GetMapping("/customers")
    public ApiResponse<List<CustomerProfile>> customers(HttpServletRequest request) {
        OperatorContext operator = operators.requireSession(request);
        return ApiResponse.ok(service.customers().stream().filter(profile -> operator.dataScope() == DataScope.ALL
                || operator.customerScope().contains(String.valueOf(profile.customerId()))
                || operator.customerScope().contains(profile.name())).toList());
    }

    @PostMapping("/customers")
    public ApiResponse<CustomerProfile> saveCustomer(@RequestBody CustomerProfile input, HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.MASTER_DATA_MANAGE, operators.requireSession(request));
        return ApiResponse.ok(service.saveCustomer(input));
    }

    @GetMapping("/materials")
    public ApiResponse<List<MaterialProfile>> materials(HttpServletRequest request) {
        operators.requireSession(request);
        return ApiResponse.ok(service.materials());
    }

    @PostMapping("/materials")
    public ApiResponse<MaterialProfile> saveMaterial(@RequestBody MaterialProfile input, HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.MASTER_DATA_MANAGE, operators.requireSession(request));
        return ApiResponse.ok(service.saveMaterial(input));
    }
}
