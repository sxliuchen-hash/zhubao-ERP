import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  correctRegisterImportRow,
  confirmRegisterImport,
  fetchRegisterImportBatch,
  fetchRegisterImportBatches,
  fetchRegisterImportPostingPlan,
  fetchRegisterImportWorkspace,
  postRegisterImportBatch,
  previewRegisterImport,
} from './registerImportApi';

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
            parserStatus: 'Excel preview parser ready; structured batch confirm ready; explicit business posting command ready',
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

  it('confirms a preview as a structured import batch', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
            templateCode: 'REG-OUTBOUND',
            templateName: '交货情况登记表',
            sourceFileName: '出货登记表.xlsx',
            targetCommand: 'ShipFinishedStockCommand',
            totalRows: 1,
            readyRows: 1,
            warningRows: 0,
            blockedRows: 0,
            executable: true,
            status: 'CONFIRMED',
            confirmedBy: '何琳',
            confirmedAt: '2026-07-07T19:10:00',
            confirmRemark: '结构化批次确认',
            nextAction: '已落结构化导入批次，待业务单据入账映射',
            rows: [{
              importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
              sourceLineNo: 3,
              sourceRegisterSequence: '1',
              status: 'READY',
              normalizedFields: {
                sourceRegisterLineNo: '3',
                logisticsNo: 'SF-OUT-20260706-022',
              },
              messages: [],
            }],
          },
        }),
      } as Response);

    const preview = {
      importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
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
        status: 'READY' as const,
        normalizedFields: {
          logisticsNo: 'SF-OUT-20260706-022',
        },
        messages: [],
      }],
    };

    const batch = await confirmRegisterImport({
      preview,
      operator: '何琳',
      confirmRemark: '结构化批次确认',
    });

    expect(batch.status).toBe('CONFIRMED');
    expect(batch.rows[0].normalizedFields.sourceRegisterLineNo).toBe('3');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/register-import/batches/confirm');
    expect(options?.method).toBe('POST');
    expect(options?.headers).toMatchObject({ 'Content-Type': 'application/json' });
    expect(JSON.parse(options?.body as string)).toMatchObject({
      operator: '何琳',
      confirmRemark: '结构化批次确认',
      preview: {
        importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
      },
    });
  });

  it('fetches recent register import batches, one batch detail, and posting plan', async () => {
    const batch = {
      importBatchNo: 'IMP-REG-INBOUND-20260707193000',
      templateCode: 'REG-INBOUND',
      templateName: '签收入库登记表',
      sourceFileName: '入库登记表.xlsx',
      targetCommand: 'ConfirmInboundCommand',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      status: 'CONFIRMED',
      confirmedBy: '何琳',
      confirmedAt: '2026-07-07T19:30:00',
      confirmRemark: '结构化批次确认',
      nextAction: '已落结构化导入批次，待业务单据入账映射',
      rows: [{
        importBatchNo: 'IMP-REG-INBOUND-20260707193000',
        sourceLineNo: 4,
        sourceRegisterSequence: '1',
        status: 'READY',
        normalizedFields: {
          courierNo: 'SF-IN-20260707-001',
        },
        messages: [],
      }],
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [batch],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: batch,
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            importBatchNo: 'IMP-REG-INBOUND-20260707193000',
            templateCode: 'REG-INBOUND',
            templateName: '签收入库登记表',
            targetCommand: 'ConfirmInboundCommand',
            status: 'BLOCKED',
            totalRows: 1,
            readyDrafts: 0,
            warningDrafts: 0,
            blockedDrafts: 1,
            nextAction: '先补齐业务主数据',
            drafts: [{
              importBatchNo: 'IMP-REG-INBOUND-20260707193000',
              sourceLineNo: 4,
              sourceRegisterSequence: '1',
              targetCommand: 'ConfirmInboundCommand',
              status: 'BLOCKED',
              mappedFields: {
                courierNo: 'SF-IN-20260707-001',
              },
              missingFields: [{
                field: 'materialCode',
                level: 'BLOCK',
                message: '缺少物料编码',
                fallback: '匹配物料档案',
              }],
              warningMessages: [],
            }],
          },
        }),
      } as Response);

    const batches = await fetchRegisterImportBatches(5);
    const detail = await fetchRegisterImportBatch('IMP-REG-INBOUND-20260707193000');
    const postingPlan = await fetchRegisterImportPostingPlan('IMP-REG-INBOUND-20260707193000');

    expect(batches).toHaveLength(1);
    expect(detail.rows[0].normalizedFields.courierNo).toBe('SF-IN-20260707-001');
    expect(postingPlan.status).toBe('BLOCKED');
    expect(postingPlan.drafts[0].missingFields[0].field).toBe('materialCode');
    expect(fetchMock.mock.calls[0][0]).toContain('/api/register-import/batches?limit=5');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/register-import/batches/IMP-REG-INBOUND-20260707193000');
    expect(fetchMock.mock.calls[2][0])
      .toContain('/api/register-import/batches/IMP-REG-INBOUND-20260707193000/posting-plan');
  });

  it('posts a confirmed register import batch through explicit posting command API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            importBatchNo: 'IMP-REG-INBOUND-20260707193000',
            templateCode: 'REG-INBOUND',
            templateName: '签收入库登记表',
            status: 'POSTED',
            totalDrafts: 1,
            postedDrafts: 1,
            skippedDrafts: 0,
            blockedDrafts: 0,
            warningDrafts: 0,
            operator: '样例库复核员',
            postedAt: '2026-07-07T20:30:00',
            nextAction: '已通过后端业务服务完成入账；库存变化已进入库存台账',
            items: [{
              sourceLineNo: 4,
              sourceRegisterSequence: '1',
              targetCommand: 'ConfirmInboundCommand',
              status: 'POSTED',
              businessCode: 'MO-REG-001',
              ledgerCode: 'LEDGER-REG-001',
              message: '已执行 ConfirmInboundCommand',
            }],
          },
        }),
      } as Response);

    const result = await postRegisterImportBatch('IMP-REG-INBOUND-20260707193000', {
      allowWarnings: true,
      operator: '样例库复核员',
      postingRemark: '显式入账',
    });

    expect(result.status).toBe('POSTED');
    expect(result.items[0].ledgerCode).toBe('LEDGER-REG-001');
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/register-import/batches/IMP-REG-INBOUND-20260707193000/posting-commands');
    expect(options?.method).toBe('POST');
    expect(options?.headers).toMatchObject({ 'Content-Type': 'application/json' });
    expect(JSON.parse(options?.body as string)).toMatchObject({
      allowWarnings: true,
      operator: '样例库复核员',
      postingRemark: '显式入账',
    });
  });

  it('posts register import row review corrections without executing posting commands', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
            templateCode: 'REG-OUTBOUND',
            templateName: '交货情况登记表',
            sourceFileName: '出货登记表.xlsx',
            targetCommand: 'ShipFinishedStockCommand',
            totalRows: 1,
            readyRows: 1,
            warningRows: 0,
            blockedRows: 0,
            executable: true,
            status: 'CONFIRMED',
            confirmedBy: '何琳',
            confirmedAt: '2026-07-07T19:10:00',
            confirmRemark: '结构化批次确认',
            nextAction: '已完成登记表复核补录：何琳；重新生成入账映射计划后再决定是否显式入账',
            rows: [{
              importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
              sourceLineNo: 3,
              sourceRegisterSequence: '1',
              status: 'READY',
              normalizedFields: {
                logisticsNo: 'SF-OUT-20260706-022',
                shipmentCode: 'OUT-REG-001',
                motherOrderCode: 'MO-REG-001',
                materialCode: 'MAT-18K-RING',
              },
              messages: [],
            }],
          },
        }),
      } as Response);

    const batch = await correctRegisterImportRow('IMP-REG-OUTBOUND-20260707190000', 3, {
      fields: {
        shipmentCode: 'OUT-REG-001',
        motherOrderCode: 'MO-REG-001',
        materialCode: 'MAT-18K-RING',
      },
      operator: '何琳',
      correctionRemark: '匹配主数据',
    });

    expect(batch.status).toBe('CONFIRMED');
    expect(batch.rows[0].normalizedFields.shipmentCode).toBe('OUT-REG-001');
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/register-import/batches/IMP-REG-OUTBOUND-20260707190000/rows/3/corrections');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toMatchObject({
      fields: {
        shipmentCode: 'OUT-REG-001',
        motherOrderCode: 'MO-REG-001',
        materialCode: 'MAT-18K-RING',
      },
      operator: '何琳',
      correctionRemark: '匹配主数据',
    });
  });
});
