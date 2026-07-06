import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import HardwareIntegrationWorkspace from './HardwareIntegrationWorkspace.vue';

describe('HardwareIntegrationWorkspace', () => {
  it('renders device compatibility and fallback management', () => {
    const wrapper = mount(HardwareIntegrationWorkspace);

    expect(wrapper.text()).toContain('软硬件联动指挥台');
    expect(wrapper.text()).toContain('设备兼容矩阵');
    expect(wrapper.text()).toContain('现场 POC 记录');
    expect(wrapper.text()).toContain('POC-SCAN-20260706-001');
    expect(wrapper.text()).toContain('拆包台摄像头');
    expect(wrapper.text()).toContain('人工兜底');
  });
});
