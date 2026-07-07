import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import SampleLibraryWorkspace from './SampleLibraryWorkspace.vue';

describe('SampleLibraryWorkspace', () => {
  it('renders reusable acceptance sample scenarios', () => {
    const wrapper = mount(SampleLibraryWorkspace);

    expect(wrapper.text()).toContain('样例库与验收脚本');
    expect(wrapper.text()).toContain('M3.50 样例映射');
    expect(wrapper.text()).toContain('客户来料主链路');
    expect(wrapper.text()).toContain('质检返工样例');
    expect(wrapper.text()).toContain('验收证据');
    expect(wrapper.text()).toContain('开源样例映射');
    expect(wrapper.text()).toContain('ERPNext Work Order / Job Card');
    expect(wrapper.text()).toContain('Odoo Manufacturing Quality Checks');
    expect(wrapper.text()).toContain('InvenTree Build Orders / Stock');
    expect(wrapper.text()).toContain('真实验收数据集');
    expect(wrapper.text()).toContain('REAL-FLOW-20260706-001');
    expect(wrapper.text()).toContain('BusinessOperationRealFlowTest');
    expect(wrapper.findAll('.sample-source-grid article')).toHaveLength(4);
    expect(wrapper.findAll('.acceptance-data-grid article')).toHaveLength(2);
  });
});
