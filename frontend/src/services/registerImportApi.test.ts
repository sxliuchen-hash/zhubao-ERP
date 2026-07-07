import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchRegisterImportWorkspace } from './registerImportApi';

describe('registerImportApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches register import templates and summary together', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [{
            code: 'REG-OUTBOUND',
            name: '交货情况登记表',
            sourceFile: '出货登记表.xlsx',
            businessFlow: '成品库存 -> 打包出货 -> 已发货库存',
            targetCommand: 'ShipFinishedStockCommand',
            targetLedgerType: 'outbound_shipment',
            parserStatus: 'COMMAND_READY',
            nextAction: '补导入批次表',
            fields: [{
              sourceColumn: '快递',
              targetField: 'paperExpressText + expressCompany + logisticsNo',
              targetObject: 'ShipFinishedStockCommand',
              required: true,
              normalizeRule: '原文保留后拆分',
              validationRule: '物流单号不能为空',
            }],
            validationRules: [],
          }],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            templates: 4,
            fields: 34,
            requiredFields: 25,
            commandReadyTemplates: 4,
            parserStatus: 'Excel parser pending; command mapping ready',
          },
        }),
      } as Response);

    const model = await fetchRegisterImportWorkspace();

    expect(model.templates[0].code).toBe('REG-OUTBOUND');
    expect(model.templates[0].fields[0].targetField).toContain('paperExpressText');
    expect(model.summary.commandReadyTemplates).toBe(4);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
