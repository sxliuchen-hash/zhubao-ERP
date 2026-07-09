import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LabelWorkspace from './LabelWorkspace.vue';

describe('LabelWorkspace', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  function mockTemplates(templates: unknown[] = []) {
    return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data: templates }),
    } as Response);
  }

  it('渲染标签打印工作台与本地样例预览', async () => {
    mockTemplates();
    const wrapper = mount(LabelWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('标签打印');
    expect(wrapper.text()).toContain('通用标签生成');
    expect(wrapper.text()).toContain('按母单号便捷生成');
    expect(wrapper.text()).toContain('标签模板与打印机设置');
    // 本地样例预览
    expect(wrapper.text()).toContain('CC-20260709-001-JL');
    expect(wrapper.text()).toContain('JG|mother|CC-20260709-001-JL');
    // 二维码以内联 SVG 渲染
    expect(wrapper.find('.qr-image svg').exists()).toBe(true);
  });

  it('提供四种标签类型下拉选项', async () => {
    mockTemplates();
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    const typeOptions = wrapper.find('.label-forms select').findAll('option');

    expect(typeOptions.map((option) => option.text())).toEqual(['母单', '子工单', '出货', '返工']);
  });

  it('加载后端模板并可打开模板编辑器', async () => {
    mockTemplates([
      {
        id: 7001,
        templateCode: 'MOTHER-DEFAULT',
        templateName: '母单默认模板',
        labelType: 'MOTHER',
        brandTitle: '珠宝来料加工系统',
        fields: [{ label: '客户', source: 'customer', sortOrder: 1 }],
        footerLeftSource: 'express',
        footerRightSource: 'receiver',
        isDefault: true,
        enabled: true,
        printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 },
        remark: '',
        createdBy: 'system',
        createdAt: '2026-07-09T10:00:00',
      },
    ]);
    const wrapper = mount(LabelWorkspace);
    await flushPromises();

    expect(wrapper.find('.template-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('母单默认模板');

    await wrapper.find('.manager-head .link-button').trigger('click');
    expect(wrapper.find('.template-editor').exists()).toBe(true);
    expect(wrapper.text()).toContain('打印机设置');
  });
});
