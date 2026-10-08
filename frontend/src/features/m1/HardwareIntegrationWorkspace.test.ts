import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import HardwareIntegrationWorkspace from './HardwareIntegrationWorkspace.vue';

describe('HardwareIntegrationWorkspace', () => {
  it('renders devices and gateway readings without protocol review boards', () => {
    const wrapper = mount(HardwareIntegrationWorkspace);

    expect(wrapper.text()).toContain('软硬件联动作业台');
    expect(wrapper.text()).toContain('设备兼容池');
    expect(wrapper.text()).toContain('设备网关读数接入');
    expect(wrapper.find('table.hardware-device-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('拆包台摄像头');
    expect(wrapper.text()).not.toContain('PROTOCOL REVIEW');
    expect(wrapper.text()).not.toContain('SIGNOFF READINESS');
    expect(wrapper.text()).not.toContain('ADAPTER PLANS');
  });
});
