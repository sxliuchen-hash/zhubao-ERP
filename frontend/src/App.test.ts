import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import App from './App.vue';

describe('App', () => {
  it('starts on the M1 business workbench and exposes core modules', () => {
    const wrapper = mount(App);

    expect(wrapper.text()).toContain('Operations Console');
    expect(wrapper.text()).toContain('M3.25 / 现场作业台重构');
    expect(wrapper.text()).toContain('首页工作台');
    expect(wrapper.text()).toContain('来料母单');
    expect(wrapper.text()).toContain('生产工单');
    expect(wrapper.text()).toContain('质检返工');
    expect(wrapper.text()).toContain('库存追溯');
    expect(wrapper.text()).toContain('配置基线');
    expect(wrapper.text()).toContain('文档附件');
    expect(wrapper.text()).toContain('样例库');
    expect(wrapper.text()).toContain('软硬件联动');
    expect(wrapper.text()).toContain('PWA 作业');
  });
});
