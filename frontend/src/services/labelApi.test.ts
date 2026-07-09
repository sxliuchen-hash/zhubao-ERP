import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  fetchLabelTemplates,
  fetchMotherLabel,
  generateLabel,
  labelTypeOptions,
  saveLabelTemplate,
} from './labelApi';

const printerSetting = { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 };

describe('labelApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('通用标签生成通过 JSON 接口提交并返回载荷', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: {
          type: 'OUTBOUND',
          typeLabel: '出货',
          primaryCode: 'CK20260709001',
          qrContent: 'JG|outbound|CK20260709001',
          fields: [{ name: '客户', value: '恒信珠宝' }],
          footerLeft: '签收人：张三',
          footerRight: '',
          printedAt: '2026-07-09 10:00',
          brandTitle: '珠宝来料加工系统',
          templateCode: 'OUTBOUND-DEFAULT',
          printerSetting,
        },
      }),
    } as Response);

    const payload = await generateLabel({
      type: 'OUTBOUND',
      primaryCode: 'CK20260709001',
      fields: [{ name: '客户', value: '恒信珠宝' }],
      footerLeft: '签收人：张三',
    });

    expect(payload.qrContent).toBe('JG|outbound|CK20260709001');
    expect(payload.typeLabel).toBe('出货');
    expect(payload.printerSetting.dpi).toBe(203);
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/label/generate');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toMatchObject({
      type: 'OUTBOUND',
      primaryCode: 'CK20260709001',
    });
  });

  it('按母单号便捷生成母单标签并可带模板编码', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: {
          type: 'MOTHER',
          typeLabel: '母单',
          primaryCode: 'MO20260709001',
          qrContent: 'JG|mother|MO20260709001',
          fields: [{ name: '客户', value: '客户#8' }],
          footerLeft: '快递：顺丰 SF123',
          footerRight: '收货人：王收货',
          printedAt: '2026-07-09 10:00',
          brandTitle: '珠宝来料加工系统',
          templateCode: 'MOTHER-BOX',
          printerSetting,
        },
      }),
    } as Response);

    const payload = await fetchMotherLabel('MO20260709001', 'MOTHER-BOX');

    expect(payload.type).toBe('MOTHER');
    expect(payload.templateCode).toBe('MOTHER-BOX');
    expect(fetchMock.mock.calls[0][0]).toContain('/api/label/mother-orders/MO20260709001?templateCode=MOTHER-BOX');
  });

  it('查询与保存标签模板', async () => {
    const template = {
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
      printerSetting,
      remark: '',
      createdBy: 'system',
      createdAt: '2026-07-09T10:00:00',
    };
    const ok = (data: unknown) => ({ ok: true, json: async () => ({ code: 0, message: 'ok', data }) } as Response);
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(ok([template]))
      .mockResolvedValueOnce(ok({ ...template, id: 7005, templateCode: 'MOTHER-BOX' }));

    const list = await fetchLabelTemplates();
    const saved = await saveLabelTemplate({
      templateCode: 'MOTHER-BOX',
      templateName: '母单周转盒模板',
      labelType: 'MOTHER',
      widthMm: 80,
      heightMm: 60,
      copies: 2,
    });

    expect(list[0].templateCode).toBe('MOTHER-DEFAULT');
    expect(saved.id).toBe(7005);
    expect(fetchMock.mock.calls[0][0]).toContain('/api/label/templates');
    expect(fetchMock.mock.calls[1][1]?.method).toBe('POST');
  });

  it('提供四种标签类型下拉选项', () => {
    expect(labelTypeOptions.map((option) => option.value)).toEqual([
      'MOTHER',
      'WORK_ORDER',
      'OUTBOUND',
      'REWORK',
    ]);
  });
});
