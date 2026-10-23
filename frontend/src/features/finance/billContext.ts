/** 账单上下文只携带稳定客户编号，不用客户名称代替授权范围。 */
export interface BillContext {
  customerId: number;
  yearMonth: string;
  receivableCode?: string;
}
export type BillMode = 'statement' | 'receipt';
export type OpenBillAction = (context: BillContext, mode?: BillMode) => void;
export function validBillContext(context: BillContext) {
  return Number.isSafeInteger(context.customerId) && context.customerId > 0 && (context.yearMonth === '' || /^\d{4}-(0[1-9]|1[0-2])$/.test(context.yearMonth));
}
