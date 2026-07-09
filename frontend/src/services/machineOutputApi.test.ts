import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  fetchMachineOutputMachineMatchSuggestions,
  fetchMachineOutputSummary,
  fetchMachineOutputs,
  fetchMachineOutputsByImportBatch,
  submitMachineOutput,
} from './machineOutputApi';

describe('machineOutputApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches recent machine output records and records by import batch', async () => {
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

    const recent = await fetchMachineOutputs(5);
    const byBatch = await fetchMachineOutputsByImportBatch('IMP-REG-ENGRAVING-001');
    const summary = await fetchMachineOutputSummary(20);
    const suggestions = await fetchMachineOutputMachineMatchSuggestions(20);

    expect(recent[0].code).toBe('MOR-REG-001');
    expect(byBatch[0].machineNoSnapshot).toBe('ENG-03');
    expect(summary.byMachine[0].quantity).toBe(12);
    expect(suggestions[0].readiness).toBe('READY_TO_BIND');
    expect(suggestions[0].candidateMachineIds).toEqual([9001]);
    expect(fetchMock.mock.calls[0][0]).toContain('/api/machine-outputs?limit=5');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/machine-outputs/import-batches/IMP-REG-ENGRAVING-001');
    expect(fetchMock.mock.calls[2][0]).toContain('/api/machine-outputs/summary?limit=20');
    expect(fetchMock.mock.calls[3][0]).toContain('/api/machine-outputs/machine-match-suggestions?limit=20');
  });

  it('submits a machine output record through JSON API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            code: 'MOR-REG-002',
            workDate: '2026-07-08',
            customerNameSnapshot: '恒信珠宝',
            productCategory: '戒托',
            machineNoSnapshot: 'ENG-03',
            workOrderCode: 'WO-REG-ENG-002',
            workReportCode: 'WO-REG-ENG-002',
            outputQuantity: 18,
            quantityUnit: '件',
            operatorSignatureName: '周凯',
            sourceRegisterType: '雕刻机统计单（日）',
            machineLineNo: '3',
            sourceSequenceNo: '1',
            importBatchNo: 'IMP-REG-ENGRAVING-002',
            ledgerCode: 'STK-REG-002',
            status: 'SUBMITTED',
            remark: '手工补录',
            createdBy: '车间组长 李华',
            createdAt: '2026-07-08T01:22:00',
          },
        }),
      } as Response);

    const result = await submitMachineOutput({
      code: 'MOR-REG-002',
      workDate: '2026-07-08',
      productCategory: '戒托',
      machineNoSnapshot: 'ENG-03',
      outputQuantity: 18,
      operatorSignatureName: '周凯',
      operator: '车间组长 李华',
    });

    expect(result.status).toBe('SUBMITTED');
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/machine-outputs');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toMatchObject({
      code: 'MOR-REG-002',
      machineNoSnapshot: 'ENG-03',
      operatorSignatureName: '周凯',
    });
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

  it('查询与保存机器档案主数据', async () => {
    const { fetchMachineProfiles, saveMachineProfile } = await import('./machineOutputApi');
    const profile = {
      id: 9003,
      machineNo: 'ENG-03',
      machineName: '雕刻机 3 号',
      machineType: '雕刻',
      location: '雕刻车间',
      enabled: true,
      remark: '',
      createdBy: '李华',
      createdAt: '2026-07-09T10:00:00',
    };
    const ok = (data: unknown) => ({ ok: true, json: async () => ({ code: 0, message: 'ok', data }) } as Response);
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(ok([profile]))
      .mockResolvedValueOnce(ok(profile));

    const list = await fetchMachineProfiles();
    const saved = await saveMachineProfile({ machineNo: 'ENG-03', machineName: '雕刻机 3 号' });

    expect(list[0].machineNo).toBe('ENG-03');
    expect(saved.id).toBe(9003);
    expect(fetchMock.mock.calls[0][0]).toContain('/api/machine-profiles');
    expect(fetchMock.mock.calls[1][1]?.method).toBe('POST');
  });
});
