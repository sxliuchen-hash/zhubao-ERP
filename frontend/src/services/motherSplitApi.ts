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

export function keepMotherOrder(code: string, requestId: string): Promise<MotherOrderSplitView> {
  return apiPost<{ requestId: string }, MotherOrderSplitView>(
    `/api/business/operations/mother-orders/${encodeURIComponent(code)}/keep`,
    { requestId },
  );
}

export function splitMotherOrder(
  code: string,
  lines: SplitLineRequest[],
  requestId: string,
): Promise<MotherOrderSplitView> {
  return apiPost<{ requestId: string; lines: SplitLineRequest[] }, MotherOrderSplitView>(
    `/api/business/operations/mother-orders/${encodeURIComponent(code)}/splits`,
    { requestId, lines },
  );
}
