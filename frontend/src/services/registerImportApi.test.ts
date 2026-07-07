import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchRegisterImportWorkspace, previewRegisterImport } from './registerImportApi';

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
            parserStatus: 'Excel preview parser ready; confirm import pending',
          },
        }),
      } as Response);

    const model = await fetchRegisterImportWorkspace();

    expect(model.templates[0].code).toBe('REG-OUTBOUND');
    expect(model.templates[0].fields[0].targetField).toContain('paperExpressText');
    expect(model.summary.commandReadyTemplates).toBe(4);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('uploads register import preview form data', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            importBatchNo: 'IMP-REG-OUTBOUND-20260707180000',
            templateCode: 'REG-OUTBOUND',
            templateName: '交货情况登记表',
            sourceFileName: '出货登记表.xlsx',
            totalRows: 1,
            readyRows: 1,
            warningRows: 0,
            blockedRows: 0,
            executable: true,
            nextAction: '可进入人工复核或确认导入',
            rows: [{
              sourceLineNo: 3,
              sourceRegisterSequence: '1',
              status: 'READY',
              normalizedFields: {
                paperExpressText: '顺丰 SF-OUT-20260706-022',
                logisticsNo: 'SF-OUT-20260706-022',
              },
              messages: [],
            }],
          },
        }),
      } as Response);

    const file = new Blob(['xlsx-binary'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    const preview = await previewRegisterImport({
      templateCode: 'REG-OUTBOUND',
      businessDate: '2026-07-07',
      file,
      fileName: '出货登记表.xlsx',
    });

    expect(preview.rows[0].normalizedFields.logisticsNo).toBe('SF-OUT-20260706-022');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, options] = fetchMock.mock.calls[0];
    expect(options?.method).toBe('POST');
    expect(options?.body).toBeInstanceOf(FormData);
    const form = options?.body as FormData;
    expect(form.get('templateCode')).toBe('REG-OUTBOUND');
    expect(form.get('businessDate')).toBe('2026-07-07');
    expect(form.get('file')).toBeInstanceOf(File);
    expect((form.get('file') as File).name).toBe('出货登记表.xlsx');
  });
});
