import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import M1Dashboard from './M1Dashboard.vue';

describe('M1Dashboard', () => {
  it('renders a project-aware ERP/MES dashboard instead of a thin placeholder', () => {
    const wrapper = mount(M1Dashboard);

    expect(wrapper.text()).toContain('来料加工生产运营台');
    expect(wrapper.text()).toContain('评审原型，距离交付还有差距');
    expect(wrapper.text()).toContain('我目前实际做到了什么');
    expect(wrapper.text()).toContain('已提交验收 fixture');
    expect(wrapper.text()).toContain('还不具备交付条件');
    expect(wrapper.text()).toContain('业务调度台');
    expect(wrapper.text()).toContain('已接业务命令');
    expect(wrapper.text()).toContain('轻量审批任务');
    expect(wrapper.text()).toContain('证据链健康度');
    expect(wrapper.text()).toContain('现场阻断队列');
    expect(wrapper.text()).toContain('一期业务闭环状态图');
    expect(wrapper.text()).toContain('模块完成度台账');
    expect(wrapper.text()).toContain('在制工单看板');
    expect(wrapper.text()).toContain('今日来料');
    expect(wrapper.text()).toContain('超标损耗审批');
    expect(wrapper.text()).toContain('POST /api/business/operations/loss-approvals');
    expect(wrapper.text()).toContain('工资试算');
    expect(wrapper.text()).toContain('售后复核');
    expect(wrapper.find('table.erp-table').exists()).toBe(true);
  });
});
