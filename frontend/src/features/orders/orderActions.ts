import type { ScanObjectType } from '../../services/scanContextApi';

/** 动作目标保留类型和精确单号，不能用列表关键字代替。 */
export interface OrderActionTarget {
  objectType: ScanObjectType | null;
  code: string;
}

export type OpenIntakeAction = (target?: OrderActionTarget | null) => void;

export function intakeDraftKey(target: OrderActionTarget | null): string {
  return target ? `intake:${target.objectType ?? 'AUTO'}:${target.code}` : 'intake:new';
}
