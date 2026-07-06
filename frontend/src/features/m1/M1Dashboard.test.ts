import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import M1Dashboard from './M1Dashboard.vue';

describe('M1Dashboard', () => {
  it('renders business dashboard content instead of a technical placeholder', () => {
    const wrapper = mount(M1Dashboard);

    expect(wrapper.text()).toContain('今日来料');
    expect(wrapper.text()).toContain('现场待办');
    expect(wrapper.text()).toContain('生产运营主控台');
    expect(wrapper.text()).toContain('追溯查询');
    expect(wrapper.find('table.erp-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('证据链健康');
    expect(wrapper.text()).toContain('在制工单看板');
    expect(wrapper.text()).toContain('超标损耗审批');
  });
});
