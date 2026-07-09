package com.zhubao.erp.machine.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.machine.app.MachineProfileService;
import com.zhubao.erp.machine.domain.MachineProfile;
import com.zhubao.erp.machine.domain.SaveMachineProfileCommand;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 机器档案主数据接口。新增/更新受 {@code MACHINE_OUTPUT_SUBMIT} 权限守卫（见 PermissionGuardInterceptor）。
 */
@RestController
@RequestMapping("/api/machine-profiles")
public class MachineProfileController {

    private final MachineProfileService service;

    public MachineProfileController(MachineProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<MachineProfile>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping
    public ApiResponse<MachineProfile> save(@Valid @RequestBody SaveMachineProfileCommand command) {
        return ApiResponse.ok(service.save(command));
    }
}
