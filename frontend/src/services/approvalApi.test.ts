import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchApprovalTasks, fetchPendingApprovalTasks, submitApprovalDecision } from './approvalApi';

describe('approvalApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('calls approval task endpoints and unwraps api responses', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: [
          {
            approvalCode: 'LOSS-001',
            scene: 'LOSS_APPROVAL',
            businessCode: 'ZD-001',
            title: '损耗审批',
            status: 'WAITING_APPROVAL',
            applicant: '李华',
            approver: '马杰',
            ownerRole: 'WORKSHOP_LEADER',
            severity: 'red',
            sourceStatus: 'WAITING_APPROVAL',
            sourceSummary: 'trialWage=100; formalPayroll=false',
            evidenceCode: 'EV-001',
            formalPayroll: false,
            createdAt: '2026-07-06T15:42:00+08:00',
            locked: false,
          },
        ],
      }),
    } as Response);

    const tasks = await fetchApprovalTasks();
    const pending = await fetchPendingApprovalTasks();

    expect(tasks[0].approvalCode).toBe('LOSS-001');
    expect(pending[0].status).toBe('WAITING_APPROVAL');
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/approvals/tasks');
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/approvals/tasks/pending');
  });

  it('submits an approval decision command', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: {
          approvalCode: 'LOSS-001',
          scene: 'LOSS_APPROVAL',
          businessCode: 'ZD-001',
          title: '损耗审批',
          status: 'APPROVED',
          applicant: '李华',
          approver: '马杰',
          ownerRole: 'WORKSHOP_LEADER',
          severity: 'green',
          sourceStatus: 'WAITING_APPROVAL',
          sourceSummary: 'trialWage=100; formalPayroll=false',
          evidenceCode: 'EV-001',
          formalPayroll: false,
          createdAt: '2026-07-06T15:42:00+08:00',
          decidedAt: '2026-07-06T16:00:00+08:00',
          decisionOperator: '马杰',
          decisionComment: '通过',
          locked: true,
        },
      }),
    } as Response);

    const result = await submitApprovalDecision('LOSS-001', {
      decision: 'APPROVE',
      operator: '马杰',
      comment: '通过',
      evidenceCode: 'EV-001',
    });

    expect(result.status).toBe('APPROVED');
    expect(fetchMock).toHaveBeenCalledWith('/api/approvals/tasks/LOSS-001/decisions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        decision: 'APPROVE',
        operator: '马杰',
        comment: '通过',
        evidenceCode: 'EV-001',
      }),
    });
  });
});
