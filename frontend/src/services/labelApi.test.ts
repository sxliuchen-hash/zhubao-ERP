import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchMotherLabel, generateLabel, labelTypeOptions } from './labelApi';

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
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/label/generate');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toMatchObject({
      type: 'OUTBOUND',
      primaryCode: 'CK20260709001',
    });
  });

  it('按母单号便捷生成母单标签', async () => {
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
        },
      }),
    } as Response);

    const payload = await fetchMotherLabel('MO20260709001');

    expect(payload.type).toBe('MOTHER');
    expect(payload.qrContent).toBe('JG|mother|MO20260709001');
    expect(fetchMock.mock.calls[0][0]).toContain('/api/label/mother-orders/MO20260709001');
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
