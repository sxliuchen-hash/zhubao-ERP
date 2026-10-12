import type { ScanIntent, ScanObjectType } from '../../services/scanContextApi';

/** 从进度或顶部追溯进入现场时使用的规范目标。关键字过滤不能代替它。 */
export interface TraceTarget {
  objectType: ScanObjectType | null;
  code: string;
  intent: ScanIntent;
  token: number;
}

export function progressTraceTarget(
  row: { motherOrderCode: string; workOrderCode: string; openTab: 'inbound' | 'production' | 'quality' },
  token: number,
): TraceTarget {
  const mother = row.motherOrderCode.trim();
  const work = row.workOrderCode.trim();
  const keptMother = work === '' || work === mother;
  const intent: ScanIntent = row.openTab === 'quality' ? 'QUALITY' : row.openTab === 'inbound' ? 'INTAKE' : 'PRODUCTION';
  return {
    objectType: keptMother ? 'MOTHER_ORDER' : 'WORK_ORDER',
    code: keptMother ? mother : work,
    intent,
    token,
  };
}
