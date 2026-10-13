import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import LabelWorkspace from './LabelWorkspace.vue';
import { openLabelPrint } from './labelPrint';
import { queryScanContext } from '../../services/scanContextApi';
import { buildScanContext } from '../scan/scanContextFixture';

vi.mock('./labelPrint', async () => {
  const actual = await vi.importActual<typeof import('./labelPrint')>('./labelPrint');
  return {
    ...actual,
    openLabelPrint: vi.fn(),
  };
});

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn() };
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

  it('没有真实标签时显示空态并禁止打印样例', async () => {
    mockTemplates();
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('请先按单号打开贴纸');
    expect(wrapper.text()).not.toContain('CC-20260709-001-JL');
    expect(wrapper.find('.bw-label svg').exists()).toBe(false);
    expect(wrapper.find('.preview-toolbar .primary-button').exists()).toBe(false);
    expect(openLabelPrint).not.toHaveBeenCalled();
  });

  it('模板接口失败明确显示原因，不回退本地标签', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('连接失败'));
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('后端接口不可用');
    expect(wrapper.find('.bw-label svg').exists()).toBe(false);
  });

  it('提供四种标签类型下拉选项', async () => {
    mockTemplates();
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    const typeOptions = wrapper.get('[aria-label="标签类型"]').findAll('option');

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
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'WORK_ORDER',
      code: 'ZD-1',
    }));
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('JG|work_order|ZD-1');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('已打开子单 ZD-1');
    expect(wrapper.text()).toContain('B8N4');
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('内壁刻字');
    expect(wrapper.text()).toContain('2026-10-20');
    expect(wrapper.text()).toContain('JG|work_order|ZD-1');
    await wrapper.get('.preview-toolbar .primary-button').trigger('click');
    expect(openLabelPrint).toHaveBeenCalledWith(expect.objectContaining({ primaryCode: 'ZD-1' }));
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    expect(wrapper.find('.preview-toolbar .primary-button').exists()).toBe(false);

  });

  it('收货跳转自动带入母单并显示可打印预览，不隐式选择不拆', async () => {
    const target = ref({ objectType: 'MOTHER_ORDER', code: 'MD-NEW-1', intent: 'LOOKUP', token: 1 });
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'MOTHER_ORDER', code: 'MD-NEW-1' }));
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => ({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data: String(input).includes('/api/label/mother-orders/') ? {
        type: 'MOTHER', typeLabel: '母单', primaryCode: 'MD-NEW-1', qrContent: 'JG|mother|MD-NEW-1',
        fields: [{ name: '客户', value: '恒信珠宝' }], footerLeft: '', footerRight: '', printedAt: '',
        brandTitle: '珠宝来料加工系统', templateCode: '',
        printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 },
      } : [] }),
    } as Response));
    const wrapper = mount(LabelWorkspace, { global: { provide: { 'zhubao-label-target': target } } });
    await flushPromises();
    expect(queryScanContext).toHaveBeenCalledWith({ content: 'MD-NEW-1', objectType: 'MOTHER_ORDER', intent: 'LOOKUP' });
    expect(wrapper.get('[aria-label="现场贴纸单号"]').element).toHaveProperty('value', 'MD-NEW-1');
    expect(wrapper.get('[aria-label="标签预览"]').text()).toContain('MD-NEW-1');
    expect(wrapper.text()).toContain('收货母单标签可直接打印');
    expect(fetchSpy.mock.calls.every(([url]) => !String(url).includes('/keep'))).toBe(true);
    await wrapper.get('.preview-toolbar .primary-button').trigger('click');
    expect(openLabelPrint).toHaveBeenCalledWith(expect.objectContaining({ primaryCode: 'MD-NEW-1' }));
  });
});
