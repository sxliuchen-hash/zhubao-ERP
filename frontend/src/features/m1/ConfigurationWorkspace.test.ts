import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ConfigurationWorkspace from './ConfigurationWorkspace.vue';

describe('ConfigurationWorkspace', () => {
  it('keeps login, accounts and customer scope, and omits unsaved baseline lists', async () => {
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('登录与客户范围');
    expect(wrapper.text()).toContain('登录会话');
    expect(wrapper.text()).toContain('自助改密');
    expect(wrapper.text()).toContain('账号');
    expect(wrapper.text()).toContain('客户范围');
    expect(wrapper.text()).toContain('客户授权');
    expect(wrapper.text()).toContain('授权客户');
    expect(wrapper.text()).toContain('用户管理');
    expect(wrapper.text()).toContain('存储内容');
    expect(wrapper.text()).toContain('创建账号');
    expect(wrapper.text()).not.toContain('系统参数');
    expect(wrapper.text()).not.toContain('角色权限基线');
    expect(wrapper.text()).not.toContain('工序模板');
    expect(wrapper.text()).not.toContain('PROC-RING-STD');
    expect(wrapper.text()).not.toContain('inventory.negative.allowed');
    expect(wrapper.text()).not.toContain('配置变更审批');
  });
});
