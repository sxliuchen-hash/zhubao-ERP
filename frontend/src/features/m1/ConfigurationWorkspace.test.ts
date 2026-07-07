import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ConfigurationWorkspace from './ConfigurationWorkspace.vue';

describe('ConfigurationWorkspace', () => {
  it('renders configuration baseline management content', () => {
    const wrapper = mount(ConfigurationWorkspace);

    expect(wrapper.text()).toContain('配置与构型基线台');
    expect(wrapper.text()).toContain('系统参数');
    expect(wrapper.text()).toContain('编码规则');
    expect(wrapper.text()).toContain('角色权限基线');
    expect(wrapper.text()).toContain('inventory.negative.allowed');
    expect(wrapper.text()).toContain('报工更正');
    expect(wrapper.text()).toContain('PROC-RING-STD');
    expect(wrapper.text()).toContain('PROC-ENGRAVE-DAY');
    expect(wrapper.text()).toContain('PROC-POLISH-DAILY');
    expect(wrapper.text()).toContain('机器编号');
    expect(wrapper.text()).toContain('交单审核');
    expect(wrapper.text()).toContain('video.retry.max');
    expect(wrapper.text()).toContain('配置变更审批');
    expect(wrapper.text()).toContain('CFG-CR-20260706-001');
    expect(wrapper.text()).toContain('审计日志');
    expect(wrapper.text()).toContain('TRACE-CFG-002');
    expect(wrapper.text()).toContain('当前权限 / 数据范围边界');
    expect(wrapper.text()).toContain('请求头上下文');
    expect(wrapper.text()).toContain('X-Zhubao-Role');
    expect(wrapper.text()).toContain('X-Zhubao-Data-Scope');
    expect(wrapper.text()).toContain('尚未接入真实登录');
    expect(wrapper.text()).toContain('BUSINESS_LOSS_APPROVAL');
  });
});
