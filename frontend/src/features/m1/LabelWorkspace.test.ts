import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import LabelWorkspace from './LabelWorkspace.vue';

describe('LabelWorkspace', () => {
  it('渲染标签打印工作台与本地样例预览', () => {
    const wrapper = mount(LabelWorkspace);

    expect(wrapper.text()).toContain('标签打印');
    expect(wrapper.text()).toContain('通用标签生成');
    expect(wrapper.text()).toContain('按母单号便捷生成');
    // 本地样例预览
    expect(wrapper.text()).toContain('CC-20260709-001-JL');
    expect(wrapper.text()).toContain('JG|mother|CC-20260709-001-JL');
    // 二维码以内联 SVG 渲染
    expect(wrapper.find('.qr-image svg').exists()).toBe(true);
  });

  it('提供四种标签类型下拉选项', () => {
    const wrapper = mount(LabelWorkspace);
    const options = wrapper.findAll('select option');

    expect(options.map((option) => option.text())).toEqual(['母单', '子工单', '出货', '返工']);
  });
});
