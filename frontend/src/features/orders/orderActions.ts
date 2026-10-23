import type { ScanBalanceCandidate, ScanObjectType } from '../../services/scanContextApi';
import type { InventoryBalance } from '../../data/m1PrototypeData';

/** 动作目标保留类型和精确单号，不能用列表关键字代替。 */
export interface OrderActionTarget {
  objectType: ScanObjectType | null;
  code: string;
}

export type OpenIntakeAction = (target?: OrderActionTarget | null) => void;
export type ProductionMode = 'progress' | 'station';
export type OpenProductionAction = (target?: OrderActionTarget | null, mode?: ProductionMode) => void;
export type OpenQualityAction = (target?: OrderActionTarget | null) => void;
export interface ShippingActionTarget extends OrderActionTarget { balance?: ScanBalanceCandidate }
export type OpenShippingAction = (target?: ShippingActionTarget | null) => void;
export type OpenAftercareAction = (row: InventoryBalance) => void;
export interface OrderChangeTarget extends OrderActionTarget { motherOrderCode: string }
export type OpenOrderChangeAction = (target: OrderChangeTarget) => void;

export function intakeDraftKey(target: OrderActionTarget | null): string {
  return target ? `intake:${target.objectType ?? 'AUTO'}:${target.code}` : 'intake:new';
}
