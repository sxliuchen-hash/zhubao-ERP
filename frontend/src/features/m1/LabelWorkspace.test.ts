import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LabelWorkspace from './LabelWorkspace.vue';
import { openLabelPrint } from './labelPrint';

vi.mock('./labelPrint', async () => {
  const actual = await vi.importActual<typeof import('./labelPrint')>('./labelPrint');
  return {
    ...actual,
    openLabelPrint: vi.fn(),
  };
});

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
    expect(wrapper.text()).toContain('黑白打印');
    expect(wrapper.text()).toContain('通用标签生成');
    expect(wrapper.text()).toContain('按母单号便捷生成');
    expect(wrapper.text()).toContain('标签模板与打印机设置');
    expect(wrapper.text()).toContain('CC-20260709-001-JL');
    expect(wrapper.text()).toContain('JG|mother|CC-20260709-001-JL');
    expect(wrapper.find('.bw-label svg').exists()).toBe(true);
    expect(wrapper.html()).not.toContain('#f4b740');

    await wrapper.get('.preview-toolbar .primary-button').trigger('click');
    expect(openLabelPrint).toHaveBeenCalledWith(expect.objectContaining({
      primaryCode: 'CC-20260709-001-JL',
    }));
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

  it('按单号或二维码从单据打开现场贴纸', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      const payload = url.includes('/api/label/work-orders/')
        ? {
          type: 'WORK_ORDER',
          typeLabel: '子工单',
          primaryCode: 'ZD-1',
          qrContent: 'JG|work_order|ZD-1',
          fields: [
            { name: '验证码', value: 'B8N4' },
            { name: '客户', value: '恒信珠宝' },
            { name: '加工要求', value: '内壁刻字' },
            { name: '期限', value: '2026-10-20' },
            { name: '单号', value: 'ZD-1' },
          ],
          footerLeft: '',
          footerRight: '',
          printedAt: '2026-10-08 12:00',
          brandTitle: '珠宝来料加工系统',
          templateCode: 'WORK_ORDER-DEFAULT',
          printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 },
        }
        : [];
      return {
        ok: true,
        json: async () => ({ code: 0, message: 'ok', data: payload }),
      } as Response;
    });
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('JG|work_order|ZD-1');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('已打开子单 ZD-1');
    expect(wrapper.text()).toContain('B8N4');
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('内壁刻字');
    expect(wrapper.text()).toContain('2026-10-20');
    expect(wrapper.text()).toContain('JG|work_order|ZD-1');
  });
});
