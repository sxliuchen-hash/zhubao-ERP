import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ApprovalWorkspace from './ApprovalWorkspace.vue';

describe('ApprovalWorkspace', () => {
  it('renders lightweight approval tasks and decision boundaries', () => {
    const wrapper = mount(ApprovalWorkspace);

    expect(wrapper.text()).toContain('轻量审批工作台');
    expect(wrapper.text()).toContain('审批阻断队列');
    expect(wrapper.text()).toContain('审批任务池');
    expect(wrapper.find('table.approval-task-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('LOSS-20260706-009');
    expect(wrapper.text()).toContain('AUTH-ACC-APP-20260708-001');
    expect(wrapper.text()).toContain('ACCOUNT_MANAGEMENT');
    expect(wrapper.text()).toContain('生产账号变更审批摘要');
    expect(wrapper.text()).toContain('仅工资试算，不发放');
    expect(wrapper.text()).toContain('轻量审批不等于完整工作流');
    expect(wrapper.text()).toContain('审批通过');
    expect(wrapper.text()).toContain('驳回补证');
  });
});
