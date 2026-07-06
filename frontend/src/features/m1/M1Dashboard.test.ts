import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import M1Dashboard from './M1Dashboard.vue';

describe('M1Dashboard', () => {
  it('renders a shop-floor ERP/MES workbench instead of a thin review page', () => {
    const wrapper = mount(M1Dashboard);

    expect(wrapper.text()).toContain('M3.25 现场作业台');
    expect(wrapper.text()).toContain('来料加工现场总控');
    expect(wrapper.text()).toContain('今日单据池');
    expect(wrapper.text()).toContain('当前焦点工单');
    expect(wrapper.text()).toContain('WEIGH HANDOFF');
    expect(wrapper.text()).toContain('轻量审批任务');
    expect(wrapper.text()).toContain('证据链健康度');
    expect(wrapper.text()).toContain('软硬件结合管理');
    expect(wrapper.text()).toContain('样例与开源依据');
    expect(wrapper.text()).toContain('ERPNext Work Order / Job Card');
    expect(wrapper.text()).toContain('Odoo Shop Floor / Quality Checks');
    expect(wrapper.text()).toContain('InvenTree Stock / Build Orders');
    expect(wrapper.text()).toContain('工位运行状态');
    expect(wrapper.text()).toContain('模块完成度与真实 API 契约');
    expect(wrapper.text()).toContain('来料待办');
    expect(wrapper.text()).toContain('loss-approvals');
    expect(wrapper.text()).toContain('工资试算');
    expect(wrapper.text()).toContain('售后复核');
    expect(wrapper.find('table.floor-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('.process-lane-strip article')).toHaveLength(6);
    expect(wrapper.findAll('.command-stat-grid article')).toHaveLength(5);
  });
});
