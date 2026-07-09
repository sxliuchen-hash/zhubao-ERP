package com.zhubao.erp.machine.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.machine.domain.MachineProfile;
import com.zhubao.erp.machine.domain.MachineProfileRepository;
import com.zhubao.erp.machine.domain.SaveMachineProfileCommand;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 机器档案主数据应用服务：为设备产量绑定提供可维护的机器档案。
 */
@Service
public class MachineProfileService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final MachineProfileRepository repository;

    public MachineProfileService(MachineProfileRepository repository) {
        this.repository = repository;
    }

    public List<MachineProfile> findAll() {
        return repository.findAll();
    }

    /**
     * 新增或更新机器档案。{@code id} 为空表示新增，非空表示更新。机器编号在启用范围内唯一。
     */
    public MachineProfile save(SaveMachineProfileCommand command) {
        if (command == null) {
            throw new BusinessException("机器档案命令不能为空");
        }
        require(command.machineNo(), "machineNo");
        require(command.machineName(), "machineName");
        String machineNo = command.machineNo().trim();

        // 校验机器编号唯一：新增时不得与已有编号冲突，更新时不得与其他档案冲突
        repository.findByMachineNo(machineNo).ifPresent(existing -> {
            if (command.id() == null || !existing.id().equals(command.id())) {
                throw new BusinessException("机器编号已存在：" + machineNo);
            }
        });

        boolean enabled = command.enabled() == null || command.enabled();
        String operator = blankToDefault(command.operator(), "机器档案维护员");
        if (command.id() == null) {
            MachineProfile profile = new MachineProfile(
                    null, machineNo, command.machineName().trim(), blankToEmpty(command.machineType()),
                    blankToEmpty(command.location()), enabled, blankToEmpty(command.remark()),
                    operator, TIME_FORMATTER.format(LocalDateTime.now()));
            return repository.create(profile);
        }
        MachineProfile existing = repository.findById(command.id())
                .orElseThrow(() -> new BusinessException("机器档案不存在：" + command.id()));
        MachineProfile updated = new MachineProfile(
                existing.id(), machineNo, command.machineName().trim(), blankToEmpty(command.machineType()),
                blankToEmpty(command.location()), enabled, blankToEmpty(command.remark()),
                existing.createdBy(), existing.createdAt());
        return repository.update(updated);
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " 不能为空");
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
