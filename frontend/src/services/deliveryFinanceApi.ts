import { apiGet, apiPost } from './apiClient';
import type { DeliveryDetail } from './deliveryApi';
export interface DeliveryBill { billId: string; version: number; totalAmount: number }
export interface BillContext { draft: DeliveryDetail['draft']; lines: DeliveryDetail['lines']; sources: DeliveryDetail['sources']; bill: DeliveryBill | null; billLines: { itemName: string; targetCode: string; quantityUnit: string; method: string; inputQuantity: number | null; unitPrice: number | null; baseQuantity: number | null; exemptQuantity: number | null; directAmount: number | null; direction: string | null; basis: string }[] }
export interface EmployeeContext { closure: { closure: string; employeeQuantity: number | null; outsourcingQuantity: number; lossQuantity: number | null; finishedQuantity: number | null; quantityUnit: string; workEvidenceComplete: boolean; work: { payeeAccount: string; stepId: string; processName: string; quantity: number }[] }; payroll: { code: string; netAmount: number }[] }
export const fetchDeliveryBill = (id: string) => apiGet<BillContext>(`/api/finance/delivery-bills/${encodeURIComponent(id)}`);
export const saveDeliveryBill = (id: string, action: 'confirmations' | 'adjustments', body: Record<string, unknown>) => apiPost<Record<string, unknown>, DeliveryBill>(`/api/finance/delivery-bills/${encodeURIComponent(id)}/${action}`, body);
export const fetchEmployeeTarget = (target: string) => apiGet<EmployeeContext>(`/api/finance/employee-targets/${encodeURIComponent(target)}`);
export const settleEmployeeTarget = (target: string, body: Record<string, unknown>) => apiPost<Record<string, unknown>, { code: string }[]>(`/api/finance/employee-targets/${encodeURIComponent(target)}/settlements`, body);
