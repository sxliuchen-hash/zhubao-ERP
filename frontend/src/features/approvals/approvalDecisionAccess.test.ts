import { describe, expect, it } from 'vitest';
import type { ApprovalTask } from '../../data/approvalTaskData';
import type { AuthSession } from '../../services/authApi';
import { approvalDecisionReason } from './approvalDecisionAccess';

const task = { status: 'WAITING_APPROVAL', scene: 'LOSS_APPROVAL', applicant: '操作工', approver: '组长甲', ownerRole: 'WORKSHOP_LEADER' } as ApprovalTask;
function actor(role: string, operator = '组长甲', username = 'leader') { return { role, operator, username } as AuthSession; }
describe('任务级审批入口判断', () => {
  it('申请人、其他组长和未知场景不能办理，已处理任务不能重复决定', () => {
    expect(approvalDecisionReason(task, actor('ADMIN', '操作工'))).toContain('申请人不能');
    expect(approvalDecisionReason({ ...task, applicant: 'LEADER' }, actor('ADMIN'))).toContain('申请人不能');
    expect(approvalDecisionReason(task, actor('WORKSHOP_LEADER', '另一组长'))).toContain('未分配');
    expect(approvalDecisionReason({ ...task, scene: 'UNKNOWN' }, actor('ADMIN'))).toContain('不能审批');
    expect(approvalDecisionReason({ ...task, status: 'APPROVED' }, actor('ADMIN'))).toContain('已处理');
  });
  it('非管理员按场景与指定身份办理，历史前置申请只认真实角色编码', () => {
    expect(approvalDecisionReason(task, actor('WORKSHOP_LEADER'))).toBe('');
    expect(approvalDecisionReason({ ...task, scene: 'ACCOUNT_MANAGEMENT' }, actor('WORKSHOP_LEADER'))).toContain('不能审批');
    expect(approvalDecisionReason({ ...task, scene: 'ACCOUNT_MANAGEMENT' }, actor('GENERAL_MANAGER'))).toBe('');
    expect(approvalDecisionReason({ ...task, scene: 'ACCOUNT_PRE_APPROVAL', ownerRole: 'FINANCE_STATISTICIAN', approver: 'GENERAL_MANAGER|ADMIN' }, actor('FINANCE_STATISTICIAN'))).toBe('');
    expect(approvalDecisionReason({ ...task, scene: 'ACCOUNT_PRE_APPROVAL', ownerRole: '中文责任人', approver: '组长甲' }, actor('ADMIN'))).toContain('不能审批');
  });
});
