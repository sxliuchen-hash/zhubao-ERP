import { apiGet, apiPost } from './apiClient';

export interface MobileIntakeLookup {
  found: boolean;
  motherOrderCode: string;
  courierNo: string;
  customerName: string;
  processingRequirement: string;
  dueDate: string;
  goodsName: string;
}

export interface MobileIntakeRequest {
  courierNo: string;
  customerName: string;
  processingRequirement: string;
  dueDate: string;
  goodsName: string;
  quantity: number;
  weight: string;
  expressCompany?: string;
}

export interface MobileIntakeResult {
  alreadyExists: boolean;
  motherOrderCode: string;
  courierNo: string;
  customerName: string;
  processingRequirement: string;
  dueDate: string;
  goodsName: string;
  quantity: number;
  weight: number;
  ledgerCode: string;
  qrContent: string;
}

export function lookupMobileIntake(courierNo: string): Promise<MobileIntakeLookup> {
  return apiGet<MobileIntakeLookup>(
    `/api/business/operations/mobile-intakes?courierNo=${encodeURIComponent(courierNo)}`,
  );
}

export function submitMobileIntake(body: MobileIntakeRequest): Promise<MobileIntakeResult> {
  return apiPost<MobileIntakeRequest, MobileIntakeResult>('/api/business/operations/mobile-intakes', body);
}

export function fetchMotherOrder(code: string): Promise<MobileIntakeResult> {
  return apiGet<MobileIntakeResult>(`/api/business/operations/mother-orders/${encodeURIComponent(code)}`);
}
