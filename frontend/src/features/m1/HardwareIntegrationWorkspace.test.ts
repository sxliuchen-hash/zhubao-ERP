import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import HardwareIntegrationWorkspace from './HardwareIntegrationWorkspace.vue';

describe('HardwareIntegrationWorkspace', () => {
  it('renders device compatibility and fallback management', () => {
    const wrapper = mount(HardwareIntegrationWorkspace);

    expect(wrapper.text()).toContain('软硬件联动作业台');
    expect(wrapper.text()).toContain('联动阻断队列');
    expect(wrapper.text()).toContain('设备兼容池');
    expect(wrapper.text()).toContain('POC 仓储可切换');
    expect(wrapper.text()).toContain('zhubao.hardware.poc.repository');
    expect(wrapper.text()).toContain('/api/hardware/poc-records 接口不变');
    expect(wrapper.find('table.hardware-device-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('POC RECORDS');
    expect(wrapper.text()).toContain('POC EVIDENCE');
    expect(wrapper.text()).toContain('ADAPTER PLANS');
    expect(wrapper.text()).toContain('低成本优先');
    expect(wrapper.text()).toContain('串口/TCP');
    expect(wrapper.text()).toContain('POC-SCAN-20260706-001');
    expect(wrapper.text()).toContain('ATT-HW-20260706-001');
    expect(wrapper.text()).toContain('拆包台摄像头');
    expect(wrapper.text()).toContain('人工兜底');
  });
});
