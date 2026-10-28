import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  fetchMachineOutputMachineMatchSuggestions,
  fetchMachineOutputSummary,
  fetchMachineOutputsByImportBatch,
} from './machineOutputApi';

describe('machineOutputApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('按批次读取设备记录、汇总及匹配建议', async () => {
    const record = {
      code: 'MOR-REG-001',
      workDate: '2026-07-08',
      customerId: 1001,
      customerNameSnapshot: '恒信珠宝',
      productCategory: '戒托',
      machineNoSnapshot: 'ENG-03',
      workOrderCode: 'WO-REG-ENG-001',
      workReportCode: 'WO-REG-ENG-001',
      outputQuantity: 12,
      quantityUnit: '件',
      operatorSignatureName: '周凯',
      sourceRegisterType: '雕刻机统计单（日）',
      sourceLineNo: 6,
      machineLineNo: '3',
      sourceSequenceNo: '1',
      importBatchNo: 'IMP-REG-ENGRAVING-001',
      ledgerCode: 'STK-REG-001',
      status: 'SUBMITTED',
      remark: '登记表导入',
      createdBy: '车间组长 李华',
      createdAt: '2026-07-08T01:20:00',
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 0, message: 'ok', data: [record] }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            totalRecords: 1,
            totalQuantity: 12,
            quantityUnit: '件',
            byMachine: [{ machineNoSnapshot: 'ENG-03', quantity: 12, records: 1 }],
            byWorkDate: [{ workDate: '2026-07-08', quantity: 12, records: 1 }],
            unmatchedMachineRecords: 1,
            signatureMissingRecords: 1,
          },
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [{
            machineNoSnapshot: 'ENG-03',
            productCategory: 'X',
            candidateMachineIds: [9001],
            matchedRecords: 1,
            unmatchedRecords: 1,
            totalRecords: 2,
            latestWorkDate: '2026-07-08',
            sourceRegisterType: 'REG-ENGRAVING',
            sourceMachineLineNo: '3',
            readiness: 'READY_TO_BIND',
            suggestedAction: 'Bind unmatched records to candidate machine id 9001.',
          }],
        }),
      } as Response);

    const byBatch = await fetchMachineOutputsByImportBatch('IMP-REG-ENGRAVING-001');
    const summary = await fetchMachineOutputSummary(20);
    const suggestions = await fetchMachineOutputMachineMatchSuggestions(20);

    expect(byBatch[0].machineNoSnapshot).toBe('ENG-03');
    expect(summary.byMachine[0].quantity).toBe(12);
    expect(suggestions[0].readiness).toBe('READY_TO_BIND');
    expect(suggestions[0].candidateMachineIds).toEqual([9001]);
    expect(fetchMock.mock.calls[0][0]).toContain('/api/machine-outputs/import-batches/IMP-REG-ENGRAVING-001');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/machine-outputs/summary?limit=20');
    expect(fetchMock.mock.calls[2][0]).toContain('/api/machine-outputs/machine-match-suggestions?limit=20');
  });

  it('绑定未绑定设备产量记录到机器档案', async () => {
    const { bindMachineOutput } = await import('./machineOutputApi');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: { machineNoSnapshot: 'ENG-03', machineId: 9001, boundRecords: 2, operator: '车间组长 李华' },
      }),
    } as Response);

    const result = await bindMachineOutput({ machineNoSnapshot: 'ENG-03', machineId: 9001, operator: '车间组长 李华' });

    expect(result.boundRecords).toBe(2);
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/machine-outputs/machine-bindings');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toMatchObject({ machineNoSnapshot: 'ENG-03', machineId: 9001 });
  });
});
