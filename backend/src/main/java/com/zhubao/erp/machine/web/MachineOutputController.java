package com.zhubao.erp.machine.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.machine.app.MachineOutputService;
import com.zhubao.erp.machine.domain.BindMachineOutputCommand;
import com.zhubao.erp.machine.domain.MachineOutputBindingResult;
import com.zhubao.erp.machine.domain.MachineOutputMachineMatchSuggestion;
import com.zhubao.erp.machine.domain.MachineOutputRecord;
import com.zhubao.erp.machine.domain.MachineOutputSummary;
import com.zhubao.erp.machine.domain.SubmitMachineOutputCommand;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/machine-outputs")
public class MachineOutputController {

    private final MachineOutputService service;

    public MachineOutputController(MachineOutputService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<MachineOutputRecord>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.recent(limit));
    }

    @GetMapping("/summary")
    public ApiResponse<MachineOutputSummary> summary(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.summary(limit));
    }

    @GetMapping("/machine-match-suggestions")
    public ApiResponse<List<MachineOutputMachineMatchSuggestion>> machineMatchSuggestions(
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.ok(service.machineMatchSuggestions(limit));
    }

    @GetMapping("/import-batches/{importBatchNo}")
    public ApiResponse<List<MachineOutputRecord>> byImportBatch(@PathVariable String importBatchNo) {
        return ApiResponse.ok(service.byImportBatchNo(importBatchNo));
    }

    @PostMapping
    public ApiResponse<MachineOutputRecord> submit(@Valid @RequestBody SubmitMachineOutputCommand command) {
        return ApiResponse.ok(service.submit(command));
    }

    @PostMapping("/machine-bindings")
    public ApiResponse<MachineOutputBindingResult> bindMachine(@Valid @RequestBody BindMachineOutputCommand command) {
        return ApiResponse.ok(service.bindMachine(command));
    }
}
