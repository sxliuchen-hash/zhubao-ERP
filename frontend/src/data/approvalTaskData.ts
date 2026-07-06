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

export const approvalTaskFallback: ApprovalTask[] = [
  {
    approvalCode: 'LOSS-20260706-009',
    scene: 'LOSS_APPROVAL',
    businessCode: 'ZD-20260706-001',
    title: '抛光工序超标损耗审批',
    status: 'WAITING_APPROVAL',
    applicant: '操作工 李华',
    approver: '车间组长 马杰',
    ownerRole: 'WORKSHOP_LEADER',
    severity: 'red',
    sourceStatus: 'WAITING_APPROVAL',
    sourceSummary:
      'inputWeight=121.15; outputWeight=116.55; lossWeight=4.60; allowedLossWeight=2.42; excessLossWeight=2.18; trialWage=284.60; formalPayroll=false',
    evidenceCode: 'EV-QC-LOSS-20260706-009',
    formalPayroll: false,
    createdAt: '2026-07-06T15:42:00+08:00',
    decidedAt: null,
    decisionOperator: null,
    decisionComment: null,
    locked: false,
  },
  {
    approvalCode: 'LOSS-20260706-010',
    scene: 'LOSS_APPROVAL',
    businessCode: 'ZD-20260706-006',
    title: '焊接工序待补称重审批',
    status: 'WAITING_APPROVAL',
    applicant: '操作工 王强',
    approver: '车间组长 马杰',
    ownerRole: 'WORKSHOP_LEADER',
    severity: 'amber',
    sourceStatus: 'WAITING_EVIDENCE',
    sourceSummary:
      'inputWeight=684.40; outputWeight=683.20; lossWeight=pending; allowedLossWeight=5.47; excessLossWeight=pending; trialWage=pending; formalPayroll=false',
    evidenceCode: 'EV-HO-20260706-018',
    formalPayroll: false,
    createdAt: '2026-07-06T16:10:00+08:00',
    decidedAt: null,
    decisionOperator: null,
    decisionComment: null,
    locked: false,
  },
  {
    approvalCode: 'LOSS-20260706-006',
    scene: 'LOSS_APPROVAL',
    businessCode: 'ZD-20260706-002',
    title: '镶石工序正常损耗自动通过',
    status: 'AUTO_APPROVED',
    applicant: '操作工 陈敏',
    approver: 'SYSTEM',
    ownerRole: 'WORKSHOP_LEADER',
    severity: 'green',
    sourceStatus: 'AUTO_APPROVED',
    sourceSummary:
      'inputWeight=119.92; outputWeight=118.98; lossWeight=0.94; allowedLossWeight=2.40; excessLossWeight=0; trialWage=312.00; formalPayroll=false',
    evidenceCode: 'EV-HO-20260706-014',
    formalPayroll: false,
    createdAt: '2026-07-06T14:28:00+08:00',
    decidedAt: '2026-07-06T14:28:00+08:00',
    decisionOperator: 'SYSTEM',
    decisionComment: 'Within configured loss threshold.',
    locked: true,
  },
];

export function approvalStatusTone(status: ApprovalStatus): Tone {
  if (status === 'APPROVED' || status === 'AUTO_APPROVED') return 'green';
  if (status === 'REJECTED') return 'red';
  return 'amber';
}
