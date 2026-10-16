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

  it('模板编辑可调整字段顺序和字号，并把版式保存到后端', async () => {
    const requests: unknown[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      if (String(input).includes('/api/label/templates') && init?.method === 'POST') {
        requests.push(JSON.parse(String(init.body)));
      }
      return { ok: true, json: async () => ({ code: 0, message: 'ok', data: init?.method === 'POST'
        ? { templateName: '现场模板' } : [] }) } as Response;
    });
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('.manager-head .link-button').trigger('click');
    await wrapper.get('input[placeholder="如 MOTHER-BOX"]').setValue('MOTHER-SITE');
    await wrapper.get('input[placeholder="如 母单周转盒模板"]').setValue('现场模板');
    await wrapper.get('[aria-label="第1个字段名称"]').setValue('客户');
    await wrapper.get('.template-editor .fields-head button').trigger('click');
    await wrapper.get('[aria-label="第2个字段名称"]').setValue('验证码');
    await wrapper.get('[aria-label="第2个字段来源"]').setValue('verificationCode');
    await wrapper.get('[aria-label="上移第2个字段"]').trigger('click');
    await wrapper.get('[aria-label="标签版式"]').setValue('COMPACT');
    await wrapper.get('[aria-label="二维码宽度"]').setValue('15');
    await wrapper.get('[aria-label="单号字号"]').setValue('10');
    expect(wrapper.get('[aria-label="模板实时预览"]').html()).toContain('--bw-code-font:10pt;');
    await wrapper.findAll('button').find((button) => button.text() === '保存模板')!.trigger('click');
    await flushPromises();
    expect(requests).toHaveLength(1);
    expect(requests[0]).toMatchObject({
      templateCode: 'MOTHER-SITE',
      fields: [{ label: '验证码', source: 'verificationCode', sortOrder: 1 },
        { label: '客户', source: 'customer', sortOrder: 2 }],
      layout: { mode: 'COMPACT', qrWidthMm: 15, codeFontPt: 10, fieldFontPt: null },
    });
  });

  it('通用标签选择模板后按配置带出待填写字段', async () => {
    mockTemplates([{
      id: 9, templateCode: 'OUTBOUND-CUSTOM', templateName: '出货模板', labelType: 'OUTBOUND',
      enabled: true, fields: [
        { label: '客户', source: 'customer', sortOrder: 2 },
        { label: '出货批次', source: '', sortOrder: 1 },
      ], printerSetting: { widthMm: 40, heightMm: 30, dpi: 300, printerName: '', copies: 1 },
    }]);
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="标签类型"]').setValue('OUTBOUND');
    const generic = wrapper.findAll('.form-card')[1];
    await generic.get('select:not([aria-label])').setValue('OUTBOUND-CUSTOM');
    expect(generic.findAll('input[placeholder="名称，如 客户"]').map((input) => (input.element as HTMLInputElement).value))
      .toEqual(['出货批次', '客户']);
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

  it('打印批次在响应丢失后复用请求编号并显示各目标', async () => {
    const printIds: string[] = [];
    let prints = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      if (url.includes('/api/label/print-queues')) return { ok: true, json: async () => ({ code: 0, data: [
        { stationId: 'A', queueId: 'Q1', name: '主队列', labelType: 'WORK_ORDER', widthMm: 60, heightMm: 40, enabled: true },
        { stationId: 'A', queueId: 'Q2', name: '备用队列', labelType: 'WORK_ORDER', widthMm: 60, heightMm: 40, enabled: true },
      ] }) } as Response;
      if (url.includes('/api/label/print-batches') && method === 'POST') {
        prints += 1;
        const body = JSON.parse(String(init?.body));
        printIds.push(body.requestId);
        expect(body.targets).toHaveLength(2);
        if (prints === 1) throw new Error('响应丢失');
        return { ok: true, json: async () => ({ code: 0, message: 'ok', data: { id: 'PB-1', jobs: [
          { id: 'J1', targetStationId: 'A', targetQueueId: 'Q1', status: 'PENDING', failureReason: '', paperCheckResult: '' },
          { id: 'J2', targetStationId: 'A', targetQueueId: 'Q2', status: 'FAILED', failureReason: '队列不可用', paperCheckResult: '' },
        ] } }) } as Response;
      }
      const payload = url.includes('/api/label/work-orders/')
        ? {
          type: 'WORK_ORDER', typeLabel: '子工单', primaryCode: 'ZD-1', qrContent: 'JG|work_order|ZD-1|0|LI1',
          fields: [{ name: '客户', value: '恒信珠宝' }], footerLeft: '', footerRight: '', printedAt: '',
          brandTitle: '珠宝来料加工系统', templateCode: 'FLOW',
          printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 },
        }
        : [];
      return { ok: true, json: async () => ({ code: 0, message: 'ok', data: payload }) } as Response;
    });
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-1' }));
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('ZD-1');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();
    await wrapper.findAll('.print-targets input[type="checkbox"]')[0].setValue(true);
    await wrapper.findAll('.print-targets input[type="checkbox"]')[1].setValue(true);
    const submit = () => wrapper.findAll('button').find((button) => button.text() === '提交到打印工位')!;
    await submit().trigger('click');
    await flushPromises();
    await submit().trigger('click');
    await flushPromises();
    expect(printIds).toHaveLength(2);
    expect(printIds[1]).toBe(printIds[0]);
    expect(wrapper.text()).toContain('预计 2 张');
    expect(wrapper.text()).toContain('队列不可用');
    wrapper.unmount();
  });

  it('待领取目标改派时提交新工位队列并显示关联任务', async () => {
    const reroutes: Array<{ requestId: string; target: { stationId: string; queueId: string }; reason: string }> = [];
    const original = { id: 'J-1', targetStationId: 'A', targetQueueId: 'Q1', status: 'PENDING',
      failureReason: '', paperCheckResult: '', copies: 1, predecessorId: '', retryReason: '' };
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      let data: unknown = [];
      if (url.includes('/api/label/print-queues')) data = [
        { stationId: 'A', queueId: 'Q1', name: '原队列', labelType: 'WORK_ORDER', widthMm: 60, heightMm: 40, enabled: true },
        { stationId: 'B', queueId: 'Q2', name: '新队列', labelType: 'WORK_ORDER', widthMm: 60, heightMm: 40, enabled: true },
      ];
      else if (url.includes('/api/label/print-target-jobs/J-1/reroute')) {
        reroutes.push(JSON.parse(String(init?.body)));
        data = { id: 'PB-2', jobs: [{ ...original, id: 'J-2', targetStationId: 'B', targetQueueId: 'Q2',
          predecessorId: 'J-1', retryReason: '原工位停机' }] };
      } else if (url.includes('/api/label/print-batches/PB-1')) {
        data = { id: 'PB-1', jobs: [{ ...original, status: 'CANCELED' }] };
      } else if (url.includes('/api/label/print-batches') && method === 'POST') {
        data = { id: 'PB-1', jobs: [original] };
      } else if (url.includes('/api/label/work-orders/')) {
        data = { type: 'WORK_ORDER', typeLabel: '子工单', primaryCode: 'ZD-1',
          qrContent: 'JG|work_order|ZD-1|0|LI1', fields: [], footerLeft: '', footerRight: '',
          printedAt: '', brandTitle: '珠宝来料加工系统', templateCode: 'FLOW',
          printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 } };
      }
      return { ok: true, json: async () => ({ code: 0, message: 'ok', data }) } as Response;
    });
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-1' }));
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('ZD-1');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();
    await wrapper.find('.print-targets input[type="checkbox"]').setValue(true);
    await wrapper.findAll('button').find(button => button.text() === '提交到打印工位')!.trigger('click');
    await flushPromises();
    await wrapper.get('[aria-label="J-1改派目标"]').setValue('B|Q2');
    await wrapper.get('[aria-label="J-1改派原因"]').setValue('原工位停机');
    await wrapper.findAll('button').find(button => button.text() === '取消原目标并创建关联任务')!.trigger('click');
    await flushPromises();
    expect(reroutes).toHaveLength(1);
    expect(reroutes[0].target).toEqual({ stationId: 'B', queueId: 'Q2' });
    expect(wrapper.text()).toContain('来源任务 J-1');
    expect(wrapper.text()).toContain('已取消');
    wrapper.unmount();
  });

  it('同版刷新沿用打印请求，改单换版后创建新请求', async () => {
    let version = 0;
    let instance = 0;
    const requestIds: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.includes('/api/label/print-queues')) return { ok: true, json: async () => ({ code: 0, data: [
        { stationId: 'A', queueId: 'Q1', name: '主队列', labelType: 'WORK_ORDER', widthMm: 60, heightMm: 40, enabled: true },
      ] }) } as Response;
      if (url.includes('/api/label/print-batches') && init?.method === 'POST') {
        requestIds.push(JSON.parse(String(init.body)).requestId);
        return { ok: true, json: async () => ({ code: 0, message: 'ok', data: { id: `PB-${requestIds.length}`, jobs: [] } }) } as Response;
      }
      const payload = url.includes('/api/label/work-orders/') ? {
        type: 'WORK_ORDER', typeLabel: '子工单', primaryCode: 'ZD-1',
        qrContent: `JG|work_order|ZD-1|${version}|LI${++instance}`,
        fields: [], footerLeft: '', footerRight: '', printedAt: '',
        brandTitle: '珠宝来料加工系统', templateCode: 'FLOW',
        printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 },
      } : [];
      return { ok: true, json: async () => ({ code: 0, message: 'ok', data: payload }) } as Response;
    });
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-1' }));
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('ZD-1');
    const openAndSubmit = async () => {
      await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
      await flushPromises();
      await wrapper.find('.print-targets input[type="checkbox"]').setValue(true);
      await wrapper.findAll('button').find(button => button.text() === '提交到打印工位')!.trigger('click');
      await flushPromises();
    };
    await openAndSubmit();
    await openAndSubmit();
    version = 1;
    await openAndSubmit();
    expect(requestIds).toHaveLength(3);
    expect(requestIds[1]).toBe(requestIds[0]);
    expect(requestIds[2]).not.toBe(requestIds[0]);
    wrapper.unmount();
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

  it('已拆母单仍预览并打印母单标签，同时保留子单选择', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'MOTHER_ORDER',
      code: 'MD-SPLIT-1',
      requiresSelection: true,
      relatedTargets: [
        { objectType: 'WORK_ORDER', code: 'ZD-1', motherOrderCode: 'MD-SPLIT-1', requirement: '内壁刻字', quantity: 4, quantityUnit: '件' },
        { objectType: 'WORK_ORDER', code: 'ZD-2', motherOrderCode: 'MD-SPLIT-1', requirement: '内壁刻字', quantity: 6, quantityUnit: '件' },
      ],
    }));
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => ({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data: String(input).includes('/api/label/mother-orders/') ? {
        type: 'MOTHER', typeLabel: '母单', primaryCode: 'MD-SPLIT-1', qrContent: 'JG|mother|MD-SPLIT-1',
        fields: [{ name: '客户', value: '恒信珠宝' }], footerLeft: '', footerRight: '', printedAt: '',
        brandTitle: '珠宝来料加工系统', templateCode: '',
        printerSetting: { widthMm: 40, heightMm: 30, dpi: 300, printerName: '', copies: 1 },
      } : [] }),
    } as Response));
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('MD-SPLIT-1');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[aria-label="标签预览"]').text()).toContain('MD-SPLIT-1');
    expect(wrapper.text()).toContain('已拆成子单');
    expect(wrapper.find('[aria-label="选择ZD-1"]').exists()).toBe(true);
    expect(wrapper.find('[aria-label="选择ZD-2"]').exists()).toBe(true);
    await wrapper.get('.preview-toolbar .primary-button').trigger('click');
    expect(openLabelPrint).toHaveBeenCalledWith(expect.objectContaining({ primaryCode: 'MD-SPLIT-1', type: 'MOTHER' }));
  });

  it('单号不明确时不猜测母单标签', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      requiresSelection: true,
      relatedTargets: [
        { objectType: 'MOTHER_ORDER', code: 'MD-1', motherOrderCode: 'MD-1', requirement: null, quantity: 1, quantityUnit: '件' },
        { objectType: 'MOTHER_ORDER', code: 'MD-2', motherOrderCode: 'MD-2', requirement: null, quantity: 1, quantityUnit: '件' },
      ],
    }));
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data: [] }),
    } as Response);
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('SF1048329001');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('请选择母单或子单');
    expect(wrapper.find('[aria-label="标签预览"]').exists()).toBe(false);
    expect(fetchSpy.mock.calls.map((call) => String(call[0])).some((url) => url.includes('/api/label/mother-orders/'))).toBe(false);
  });

  it('选择客户暗记后页脚只印拼音首字母，并去掉客户中文名', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'MOTHER_ORDER', code: 'MD-1' }));
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      const data = url.includes('/api/business/master-data/customers')
        ? [
          { customerId: 8, customerCode: 'HX', name: '恒信珠宝', aliases: '', contact: '', phone: '', remark: '', enabled: true },
          { customerId: 9, customerCode: 'HX2', name: '华信珠宝', aliases: '', contact: '', phone: '', remark: '', enabled: true },
        ]
        : url.includes('/api/label/mother-orders/')
          ? {
            type: 'MOTHER', typeLabel: '母单', primaryCode: 'MD-1', qrContent: 'JG|mother|MD-1',
            fields: [{ name: '客户', value: '恒信珠宝' }, { name: '期限', value: '2026-10-20' }],
            footerLeft: '', footerRight: '', printedAt: '', brandTitle: '珠宝来料加工系统', templateCode: 'MOTHER-DEFAULT',
            printerSetting: { widthMm: 40, heightMm: 30, dpi: 300, printerName: '', copies: 1 },
          }
          : [];
      return { ok: true, json: async () => ({ code: 0, message: 'ok', data }) } as Response;
    });
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('MD-1');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('.label-print-area').text()).toContain('恒信珠宝');
    await wrapper.get('[aria-label="页脚客户暗记"]').setValue('8');
    await flushPromises();
    const printed = wrapper.get('.label-print-area').text();
    expect(printed).toContain('HXZB');
    expect(printed).not.toContain('恒信珠宝');
    expect(printed).toContain('2026-10-20');
    expect(wrapper.text()).toContain('还有其他客户是同一组首字母');
    await wrapper.get('.preview-toolbar .primary-button').trigger('click');
    expect(openLabelPrint).toHaveBeenCalledWith(expect.objectContaining({
      footerRight: 'HXZB',
      fields: [{ name: '期限', value: '2026-10-20' }],
    }));
  });
});
