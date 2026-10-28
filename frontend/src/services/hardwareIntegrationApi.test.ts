import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchHardwareWorkspace, mapPocRecord } from './hardwareIntegrationApi';

describe('hardwareIntegrationApi', () => {
  afterEach(() => vi.restoreAllMocks());
  it('现场阻断不能显示为通过', () => {
    expect(mapPocRecord({ code: 'POC-TEST', deviceCode: 'TEST', scene: '称重', testWindow: '', result: '阻断', evidence: '', blocker: '待复测', owner: '', nextAction: '' }).tone).toBe('red');
  });
  it('只读取现场记录、原始证据和真实汇总', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    for (const data of [[], [], { pocRecords: 0, pocBlocked: 0 }]) {
      fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ code: 0, data }) } as Response);
    }
    expect(await fetchHardwareWorkspace()).toEqual({ pocRecords: [], pocEvidence: [], summary: { pocRecords: 0, pocBlocked: 0 } });
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual(['/api/hardware/poc-records', '/api/hardware/poc-evidence', '/api/hardware/summary']);
  });
});
