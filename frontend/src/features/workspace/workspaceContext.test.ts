import { describe, expect, it } from 'vitest';
import { createWorkspaceState } from './workspaceContext';

describe('workspaceContext', () => {
  it('记住选中单据和返回位置，代次只增不减', () => {
    const workspace = createWorkspaceState();
    workspace.rememberReturn({
      section: 'orders',
      query: '锦和',
      customerId: '12',
      process: '抛光',
      facet: 'IN_PROCESS',
      dueBefore: '2026-10-23',
      page: 2,
      scrollTop: 40,
    });
    workspace.rememberSelection({
      objectType: 'WORK_ORDER',
      code: 'ZD-1',
      motherOrderCode: 'MD-1',
      workOrderCode: 'ZD-1',
      executionRoundCode: null,
    });
    workspace.holdPendingAction({ draftKey: 'operator:progress:WORK_ORDER:ZD-1', requestId: 'req-1' });

    expect(workspace.selection.value?.selectionRevision).toBe(1);
    expect(workspace.returnLocation.value?.customerId).toBe('12');
    expect(workspace.pendingAction.value?.requestId).toBe('req-1');

    workspace.rememberSelection({
      objectType: 'MOTHER_ORDER',
      code: 'MD-2',
      motherOrderCode: 'MD-2',
      workOrderCode: null,
      executionRoundCode: null,
    });
    expect(workspace.selection.value).toMatchObject({ code: 'MD-2', selectionRevision: 2 });
  });

  it('清空后不留下一账号的单据、位置和未完成请求', () => {
    const workspace = createWorkspaceState();
    workspace.rememberSelection({
      objectType: 'MOTHER_ORDER',
      code: 'MD-1',
      motherOrderCode: 'MD-1',
      workOrderCode: null,
      executionRoundCode: 'RW-1',
    });
    workspace.rememberReturn({
      section: 'desk',
      query: '',
      customerId: '',
      process: '',
      facet: 'ALL',
      dueBefore: '',
      page: 1,
      scrollTop: 0,
    });
    workspace.holdPendingAction({ draftKey: 'a', requestId: 'req-9' });
    workspace.approvalComments.value['AP-A'] = '上一账号意见';
    const previous = workspace.selection.value?.selectionRevision ?? 0;

    workspace.clear();

    expect(workspace.selection.value).toBeNull();
    expect(workspace.returnLocation.value).toBeNull();
    expect(workspace.pendingAction.value).toBeNull();
    expect(workspace.approvalDecisions.value).toEqual({});
    expect(workspace.approvalComments.value).toEqual({});
    workspace.rememberSelection({
      objectType: 'MOTHER_ORDER',
      code: 'MD-3',
      motherOrderCode: 'MD-3',
      workOrderCode: null,
      executionRoundCode: null,
    });
    expect(workspace.selection.value?.selectionRevision).toBeGreaterThan(previous);
  });
});
