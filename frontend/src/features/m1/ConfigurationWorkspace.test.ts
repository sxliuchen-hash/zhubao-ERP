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
    expect(wrapper.text()).toContain('video.retry.max');
  });
});
