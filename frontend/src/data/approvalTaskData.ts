import type { Tone } from './m1PrototypeData';

export type ApprovalStatus = 'WAITING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'AUTO_APPROVED';

export interface ApprovalTask {
  approvalCode: string;
  scene: string;
  businessCode: string;
  title: string;
  status: ApprovalStatus;
  applicant: string;
  approver: string;
  ownerRole: string;
  severity: Tone;
  sourceStatus: string;
  sourceSummary: string;
  evidenceCode: string;
  formalPayroll: boolean;
  createdAt: string;
  decidedAt?: string | null;
  decisionOperator?: string | null;
  decisionComment?: string | null;
  locked: boolean;
}

export interface ApprovalDecisionCommand {
  decision: 'APPROVE' | 'REJECT';
  operator: string;
  comment: string;
  evidenceCode?: string;
}

/** 审批请求留在当前账号会话里，切页面不能丢掉未知结果。 */
export interface ApprovalDecisionSession {
  task: ApprovalTask;
  command: ApprovalDecisionCommand;
  outcome: 'submitting' | 'unknown' | 'succeeded' | 'confirmed' | 'rejected';
  result: ApprovalTask | null;
  error: string;
}

export function approvalStatusTone(status: ApprovalStatus): Tone {
  if (status === 'APPROVED' || status === 'AUTO_APPROVED') return 'green';
  if (status === 'REJECTED') return 'red';
  return 'amber';
}
