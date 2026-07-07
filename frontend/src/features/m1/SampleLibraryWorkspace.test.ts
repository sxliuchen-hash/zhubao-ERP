import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import SampleLibraryWorkspace from './SampleLibraryWorkspace.vue';

describe('SampleLibraryWorkspace', () => {
  it('renders the sample library as an acceptance-data workbench', () => {
    const wrapper = mount(SampleLibraryWorkspace);

    expect(wrapper.text()).toContain('样例验收指挥台');
    expect(wrapper.text()).toContain('M3.51 管理台重构');
    expect(wrapper.text()).toContain('样例矩阵');
    expect(wrapper.text()).toContain('当前主样例');
    expect(wrapper.text()).toContain('开源调研命中');
    expect(wrapper.text()).toContain('现场登记表导入准备度');
    expect(wrapper.text()).toContain('真实验收数据集');
    expect(wrapper.text()).toContain('现场数据缺口');

    expect(wrapper.findAll('.sample-row')).toHaveLength(4);
    expect(wrapper.findAll('.reference-card')).toHaveLength(3);
    expect(wrapper.findAll('.register-template-row')).toHaveLength(4);
    expect(wrapper.findAll('.fixture-row')).toHaveLength(2);
    expect(wrapper.findAll('.gate-card')).toHaveLength(6);
    expect(wrapper.findAll('.field-gap-list article').length).toBeGreaterThan(0);

    expect(wrapper.text()).toContain('ERPNext Job Card');
    expect(wrapper.text()).toContain('Odoo Quality Checks');
    expect(wrapper.text()).toContain('InvenTree Build Orders');
    expect(wrapper.text()).toContain('REG-OUTBOUND');
    expect(wrapper.text()).toContain('paperExpressText');
    expect(wrapper.text()).toContain('REAL-FLOW-20260706-001');
    expect(wrapper.text()).toContain('BusinessOperationRealFlowTest');
  });
});
