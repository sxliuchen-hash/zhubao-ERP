import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import M1Dashboard from './M1Dashboard.vue';

describe('M1Dashboard', () => {
  it('renders a shop-floor ERP/MES workbench instead of a thin review page', () => {
    const wrapper = mount(M1Dashboard);

    expect(wrapper.text()).toContain('M3.23 Shopfloor Console');
    expect(wrapper.text()).toContain('白班生产作业台');
    expect(wrapper.text()).toContain('从评审页转向真实作业台');
    expect(wrapper.text()).toContain('今天先处理这些阻断');
    expect(wrapper.text()).toContain('业务调度队列');
    expect(wrapper.text()).toContain('当前焦点工单');
    expect(wrapper.text()).toContain('称重交接阻断');
    expect(wrapper.text()).toContain('样例与开源参考');
    expect(wrapper.text()).toContain('ERPNext/Frappe');
    expect(wrapper.text()).toContain('Odoo Manufacturing');
    expect(wrapper.text()).toContain('InvenTree');
    expect(wrapper.text()).toContain('轻量审批任务');
    expect(wrapper.text()).toContain('证据链健康度');
    expect(wrapper.text()).toContain('软硬件结合管理');
    expect(wrapper.text()).toContain('工位运行状态');
    expect(wrapper.text()).toContain('模块完成度与真实 API 契约');
    expect(wrapper.text()).toContain('今日来料');
    expect(wrapper.text()).toContain('POST /api/business/operations/loss-approvals');
    expect(wrapper.text()).toContain('工资试算');
    expect(wrapper.text()).toContain('售后复核');
    expect(wrapper.find('table.m3-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('.operation-lane-grid article')).toHaveLength(4);
  });
});
