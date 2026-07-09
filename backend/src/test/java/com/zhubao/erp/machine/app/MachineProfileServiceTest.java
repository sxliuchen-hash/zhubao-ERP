package com.zhubao.erp.machine.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.machine.domain.MachineProfile;
import com.zhubao.erp.machine.domain.SaveMachineProfileCommand;
import com.zhubao.erp.machine.infra.InMemoryMachineProfileRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineProfileServiceTest {

    private final MachineProfileService service =
            new MachineProfileService(new InMemoryMachineProfileRepository());

    @Test
    void 新增机器档案分配自增主键() {
        MachineProfile profile = service.save(new SaveMachineProfileCommand(
                null, "ENG-03", "雕刻机 3 号", "雕刻", "雕刻车间", true, "评审", "李华"));

        assertThat(profile.id()).isNotNull();
        assertThat(profile.machineNo()).isEqualTo("ENG-03");
        assertThat(profile.enabled()).isTrue();
        assertThat(service.findAll()).extracting(MachineProfile::machineNo).contains("ENG-03");
    }

    @Test
    void 机器编号重复时拒绝新增() {
        service.save(new SaveMachineProfileCommand(null, "ENG-03", "雕刻机 3 号", "雕刻", "", true, "", "李华"));

        assertThatThrownBy(() -> service.save(new SaveMachineProfileCommand(
                null, "ENG-03", "重复编号", "雕刻", "", true, "", "李华")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机器编号已存在");
    }

    @Test
    void 更新机器档案保留创建人并允许保持自身编号() {
        MachineProfile created = service.save(new SaveMachineProfileCommand(
                null, "ENG-03", "雕刻机 3 号", "雕刻", "雕刻车间", true, "评审", "李华"));

        MachineProfile updated = service.save(new SaveMachineProfileCommand(
                created.id(), "ENG-03", "雕刻机 3 号改名", "雕刻", "新车间", false, "停用检修", "王强"));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.machineName()).isEqualTo("雕刻机 3 号改名");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.createdBy()).isEqualTo("李华");
    }

    @Test
    void 校验必填机器编号() {
        assertThatThrownBy(() -> service.save(new SaveMachineProfileCommand(
                null, "", "无编号", "雕刻", "", true, "", "李华")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("machineNo");
    }
}
