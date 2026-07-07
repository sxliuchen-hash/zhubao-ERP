import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import M1Dashboard from './M1Dashboard.vue';

describe('M1Dashboard', () => {
  it('renders a shop-floor ERP/MES workbench instead of a thin review page', () => {
    const wrapper = mount(M1Dashboard);

    expect(wrapper.text()).toContain('M3.49 CONTROL DESK');
    expect(wrapper.text()).toContain('来料加工现场作业台');
    expect(wrapper.text()).toContain('当前处理单');
    expect(wrapper.text()).toContain('真实链路');
    expect(wrapper.text()).toContain('今日单据池');
    expect(wrapper.text()).toContain('焦点工单');
    expect(wrapper.text()).toContain('称重交接');
    expect(wrapper.text()).toContain('轻量审批任务');
    expect(wrapper.text()).toContain('证据链健康度');
    expect(wrapper.text()).toContain('软硬件结合管理');
    expect(wrapper.text()).toContain('样例与开源依据');
    expect(wrapper.text()).toContain('ERPNext Work Order / Job Card');
    expect(wrapper.text()).toContain('Odoo Shop Floor / Quality Checks');
    expect(wrapper.text()).toContain('InvenTree Stock / Build Orders');
    expect(wrapper.text()).toContain('工位运行状态');
    expect(wrapper.text()).toContain('模块完成度与真实 API 契约');
    expect(wrapper.text()).toContain('M3.49 UI 重构');
    expect(wrapper.text()).toContain('来料待处理');
    expect(wrapper.text()).toContain('loss-approvals');
    expect(wrapper.text()).toContain('工资试算');
    expect(wrapper.text()).toContain('售后复核');
    expect(wrapper.find('table.floor-dispatch-table').exists()).toBe(true);
    expect(wrapper.find('.active-record-panel').exists()).toBe(true);
    expect(wrapper.find('.operator-checklist').exists()).toBe(true);
    expect(wrapper.findAll('.flow-step-list article')).toHaveLength(6);
    expect(wrapper.findAll('.mission-kpi-grid article')).toHaveLength(5);
    expect(wrapper.findAll('.focus-card-v2')).toHaveLength(3);
  });
});
