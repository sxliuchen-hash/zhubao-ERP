import type { ScanContextResponse, ScanProgressRecord } from '../../services/scanContextApi';

/** 过程说明要落在真实母单或子单上。出货单和返工单回到来源工单，不记成出货。 */
export interface ProgressTarget {
  targetType: 'MOTHER_ORDER' | 'WORK_ORDER';
  targetCode: string;
  motherOrderCode: string;
}

export const progressStageLabels: Record<string, string> = {
  PRODUCTION: '生产过程',
  QUALITY: '质检过程',
  SHIPPING: '发货准备',
};

export function progressStageLabel(stage: string | null | undefined): string {
  const code = stage?.trim() ?? '';
  return progressStageLabels[code] || '未标明阶段';
}

export function progressTargetOf(
  context?: { motherOrderCode?: string | null; workOrderCode?: string | null } | null,
): ProgressTarget | null {
  const mother = context?.motherOrderCode?.trim() ?? '';
  const work = context?.workOrderCode?.trim() ?? '';
  if (work && work !== mother) {
    return { targetType: 'WORK_ORDER', targetCode: work, motherOrderCode: mother };
  }
  if (mother) {
    return { targetType: 'MOTHER_ORDER', targetCode: mother, motherOrderCode: mother };
  }
  return null;
}

export function progressTargetFromScan(context: ScanContextResponse | null | undefined): ProgressTarget | null {
  if (!context || context.requiresSelection) return null;
  const target = context.target;
  const mother = target.motherOrderCode?.trim() ?? '';
  if (target.objectType === 'WORK_ORDER' && target.code) {
    return { targetType: 'WORK_ORDER', targetCode: target.code, motherOrderCode: mother };
  }
  if (target.objectType === 'MOTHER_ORDER' && target.code) {
    return { targetType: 'MOTHER_ORDER', targetCode: target.code, motherOrderCode: mother || target.code };
  }
  const source = target.workOrderCode?.trim() ?? '';
  if ((target.objectType === 'REWORK' || target.objectType === 'OUTBOUND_ORDER') && source) {
    if (mother && source === mother) {
      return { targetType: 'MOTHER_ORDER', targetCode: mother, motherOrderCode: mother };
    }
    return { targetType: 'WORK_ORDER', targetCode: source, motherOrderCode: mother };
  }
  return null;
}

/** 把刚保存的说明补进当前卡片。相同编号不重复追加。 */
export function appendProgressRecord(context: ScanContextResponse | null | undefined, record: ScanProgressRecord): void {
  if (!context || !record.code) return;
  const current = context.progress.progressRecords;
  if (current.some((item) => item.code === record.code)) return;
  context.progress.progressRecords = [...current, record];
}
