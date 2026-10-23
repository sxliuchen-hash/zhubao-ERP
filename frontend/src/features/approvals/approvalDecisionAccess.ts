import type { AuthSession } from '../../services/authApi';
import type { ApprovalTask } from '../../data/approvalTaskData';

/** 与服务端 ApprovalAccess 对齐的展示判断；提交仍由服务端核对任务身份和状态。 */
export function approvalDecisionReason(task: ApprovalTask, session: AuthSession | null): string {
  if (!session?.operator?.trim() || !session.role) return '请登录后核对审批身份';
  if (task.status !== 'WAITING_APPROVAL') return '该审批已处理，不能重复决定';
  const samePerson = (snapshot: string) => Boolean(snapshot?.trim() && (snapshot.trim() === session.operator.trim()
    || (session.username?.trim() && snapshot.trim().toLowerCase() === session.username.trim().toLowerCase())));
  if (samePerson(task.applicant)) return '申请人不能审批自己提交的任务';
  const scene = task.scene.trim();
  let roles: string[];
  if (scene === 'LOSS_APPROVAL') roles = ['WORKSHOP_LEADER', 'GENERAL_MANAGER', 'ADMIN'];
  else if (scene === 'ACCOUNT_MANAGEMENT') roles = ['GENERAL_MANAGER', 'ADMIN'];
  else if (scene === 'ACCOUNT_PRE_APPROVAL') roles = [task.ownerRole, ...task.approver.split(/[|,，;；\s]+/)].map(role => role.trim().replaceAll('-', '_').toUpperCase());
  else return '当前身份不能审批该场景';
  if (!roles.includes(session.role)) return '当前身份不能审批该场景';
  if (scene === 'LOSS_APPROVAL' && session.role === 'WORKSHOP_LEADER' && !samePerson(task.approver)) return '该损耗审批未分配给当前审批人';
  return '';
}
