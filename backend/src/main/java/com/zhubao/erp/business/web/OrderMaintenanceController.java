package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.MaintainedMotherBrowse;
import com.zhubao.erp.business.app.MaintainedOrder;
import com.zhubao.erp.business.app.OrderMaintenanceCommands;
import com.zhubao.erp.business.app.OrderMaintenanceService;
import com.zhubao.erp.business.app.MotherOrderAdminService;
import com.zhubao.erp.business.app.OrderOperationLogService;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/order-maintenance")
public class OrderMaintenanceController {
    private final OrderMaintenanceService service;
    private final OperatorContextResolver operators;
    private final MotherOrderAdminService motherAdmin;
    private final OrderOperationLogService operationLogs;

    public OrderMaintenanceController(OrderMaintenanceService service, OperatorContextResolver operators,
                                      MotherOrderAdminService motherAdmin, OrderOperationLogService operationLogs) {
        this.service = service;
        this.operators = operators;
        this.motherAdmin = motherAdmin;
        this.operationLogs = operationLogs;
    }

    @PostMapping("/mother-orders/{code}/basic-info")
    public ApiResponse<MaintainedOrder> correctBasicInfo(@PathVariable String code,
                                                        @RequestBody MotherOrderAdminService.BasicInfo command,
                                                        HttpServletRequest request) {
        var operator = operators.requireSession(request);
        motherAdmin.correct(code, command, operator);
        return ApiResponse.ok(service.search(code, operator).stream()
                .filter(row -> "MOTHER".equals(row.targetType())).findFirst().orElseThrow());
    }

    @GetMapping("/operation-logs")
    public ApiResponse<List<OrderOperationLogService.Entry>> operationLogs(@RequestParam String code, HttpServletRequest request) {
        return ApiResponse.ok(operationLogs.list(code, operators.requireSession(request)));
    }

    @GetMapping
    public ApiResponse<List<MaintainedOrder>> search(@RequestParam String code, HttpServletRequest request) {
        return ApiResponse.ok(service.search(code, operators.requireSession(request)));
    }

    @GetMapping("/mothers")
    public ApiResponse<MaintainedMotherBrowse> mothers(@RequestParam(required = false) String from,
                                                        @RequestParam(required = false) String to,
                                                        @RequestParam(required = false) String customer,
                                                        @RequestParam(required = false) String operator,
                                                        HttpServletRequest request) {
        return ApiResponse.ok(service.listMothers(from, to, customer, operator, operators.requireSession(request)));
    }

    @GetMapping("/recycle-bin")
    public ApiResponse<List<MaintainedOrder>> recycleBin(HttpServletRequest request) {
        return ApiResponse.ok(service.recycleBin(operators.requireSession(request)));
    }

    @PostMapping("/mother-orders/{code}")
    public ApiResponse<MaintainedOrder> correctMother(@PathVariable String code,
                                                       @RequestBody OrderMaintenanceCommands.MotherCorrection command,
                                                       HttpServletRequest request) {
        return ApiResponse.ok(service.correctMother(code, command, operators.requireSession(request)));
    }

    @PostMapping("/work-orders/{code}")
    public ApiResponse<MaintainedOrder> correctChild(@PathVariable String code,
                                                      @RequestBody OrderMaintenanceCommands.ChildCorrection command,
                                                      HttpServletRequest request) {
        return ApiResponse.ok(service.correctChild(code, command, operators.requireSession(request)));
    }

    @PostMapping("/print-jobs/{id}")
    public ApiResponse<MaintainedOrder> correctPrintJob(@PathVariable String id,
                                                         @RequestBody OrderMaintenanceCommands.PrintCorrection command,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(service.correctPrintJob(id, command, operators.requireSession(request)));
    }

    @PostMapping("/print-targets/{id}")
    public ApiResponse<MaintainedOrder> correctPrintTarget(@PathVariable String id,
                                                            @RequestBody OrderMaintenanceCommands.PrintCorrection command,
                                                            HttpServletRequest request) {
        return ApiResponse.ok(service.correctPrintTarget(id, command, operators.requireSession(request)));
    }

    @PostMapping("/recycle")
    public ApiResponse<Void> recycle(@RequestBody OrderMaintenanceCommands.RecycleCommand command, HttpServletRequest request) {
        service.recycle(command, operators.requireSession(request));
        return ApiResponse.ok();
    }

    @PostMapping("/restore")
    public ApiResponse<Void> restore(@RequestBody OrderMaintenanceCommands.RestoreCommand command, HttpServletRequest request) {
        service.restore(command, operators.requireSession(request));
        return ApiResponse.ok();
    }
}
