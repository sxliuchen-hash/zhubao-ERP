import { apiGet, apiPost } from './apiClient';

export interface SplitLineRequest {
  quantity: number;
  weight: number;
  processes: string[];
}

export interface ChildWorkOrderView {
  code: string;
  motherOrderCode: string;
  verificationCode: string;
  customerName: string;
  processingRequirement: string;
  dueDate: string;
  goodsName: string;
  quantity: number;
  weight: number;
  processes: string[];
  qrContent: string;
}

export interface MotherOrderSplitView {
  motherOrderCode: string;
  decision: string;
  decisionLabel: string;
  verificationCode: string;
  customerName: string;
  processingRequirement: string;
  dueDate: string;
  goodsName: string;
  quantityUnit: string;
  quantity: number;
  weight: number;
  remainingQuantity: number;
  remainingWeight: number;
  qrContent: string;
  children: ChildWorkOrderView[];
}

export function fetchMotherSplit(code: string): Promise<MotherOrderSplitView> {
  return apiGet<MotherOrderSplitView>(`/api/business/operations/mother-orders/${encodeURIComponent(code)}/split`);
}

export function keepMotherOrder(code: string): Promise<MotherOrderSplitView> {
  return apiPost<Record<string, never>, MotherOrderSplitView>(
    `/api/business/operations/mother-orders/${encodeURIComponent(code)}/keep`,
    {},
  );
}

export function splitMotherOrder(code: string, lines: SplitLineRequest[]): Promise<MotherOrderSplitView> {
  return apiPost<{ lines: SplitLineRequest[] }, MotherOrderSplitView>(
    `/api/business/operations/mother-orders/${encodeURIComponent(code)}/splits`,
    { lines },
  );
}
