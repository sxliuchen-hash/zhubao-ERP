import { apiGet, apiPost } from './apiClient';

export interface PriceLine {
  processName: string;
  unit: string;
  unitPrice: number;
}

export interface PriceAgreement {
  code: string;
  customerId: number;
  customerName: string;
  mode: string;
  effectiveFrom: string;
  effectiveTo: string;
  lines: PriceLine[];
}

export interface ReceivableLine {
  code: string;
  customerId: number;
  customerName: string;
  yearMonth: string;
  sourceType: string;
  motherOrderCode: string;
  workOrderCode: string;
  processName: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  amount: number;
  reversesCode: string;
  remark: string;
}

export interface ReceiptAllocation {
  receivableCode: string;
  amount: number;
}

export interface CustomerReceipt {
  code: string;
  amount: number;
  receivedOn: string;
  allocations: ReceiptAllocation[];
}

export interface CustomerStatement {
  customerId: number;
  customerName: string;
  stockQuantity: number;
  stockWeight: number;
  receivableAmount: number;
  receivedAmount: number;
  outstandingAmount: number;
  prepaymentAmount: number;
  lines: ReceivableLine[];
  receipts: CustomerReceipt[];
  stockNote: string;
}

export interface UnpricedItem {
  motherOrderCode: string;
  workOrderCode: string;
  processName: string;
  reason: string;
}

export interface BillingRunResult {
  createdCount: number;
  unpriced: UnpricedItem[];
}

export interface PayrollLine {
  operatorName: string;
  orderCode: string;
  processName: string;
  grossAmount: number;
  lossDeduction: number;
  netAmount: number;
  confirmed: boolean;
}

export interface ConsumableStock {
  code: string;
  name: string;
  unitName: string;
  onHandQuantity: number;
  onHandAmount: number;
}

export interface FixedAsset {
  code: string;
  name: string;
  category: string;
  originalValue: number;
  residualValue: number;
  startMonth: string;
  usefulMonths: number;
}

export interface PayableView {
  code: string;
  kind: string;
  yearMonth: string;
  payee: string;
  motherOrderCode: string;
  amount: number;
  paidAmount: number;
  unpaidAmount: number;
}

export interface OperatingStatement {
  yearMonth: string;
  customerId: number | null;
  revenue: number;
  wageCost: number;
  consumableCost: number;
  depreciationCost: number;
  outsourceCost: number;
  result: number;
  gaps: string[];
}

export function fetchStatement(customerId: string, yearMonth: string): Promise<CustomerStatement> {
  const month = yearMonth ? `&yearMonth=${encodeURIComponent(yearMonth)}` : '';
  return apiGet(`/api/finance/statements?customerId=${encodeURIComponent(customerId)}${month}`);
}

export function savePriceAgreement(body: {
  customerId: number;
  customerName: string;
  mode: string;
  effectiveFrom: string;
  effectiveTo: string;
  lines: PriceLine[];
}): Promise<PriceAgreement> {
  return apiPost('/api/finance/price-agreements', body);
}

export function generateBilling(yearMonth: string, customerId: string): Promise<BillingRunResult> {
  return apiPost('/api/finance/billing-runs', {
    yearMonth,
    customerId: customerId ? Number(customerId) : null,
  });
}

export function recordReceipt(body: {
  customerId: number;
  customerName: string;
  receivedOn: string;
  amount: number;
  allocations: ReceiptAllocation[];
  idempotencyKey?: string;
}): Promise<CustomerReceipt> {
  return apiPost('/api/finance/receipts', body);
}

export function saveWorkerRate(body: {
  processName: string;
  unit: string;
  unitPrice: number;
  effectiveFrom: string;
  effectiveTo: string;
}): Promise<unknown> {
  return apiPost('/api/finance/worker-rates', body);
}

export function generatePayroll(yearMonth: string): Promise<{ lines: PayrollLine[]; missingRates: UnpricedItem[] }> {
  return apiPost('/api/finance/payroll-runs', { yearMonth });
}

export function confirmPayroll(yearMonth: string): Promise<unknown> {
  return apiPost('/api/finance/payroll-runs/confirmations', { yearMonth });
}

export function fetchConsumables(): Promise<ConsumableStock[]> {
  return apiGet('/api/finance/consumable-items');
}

export function saveConsumableItem(body: { code: string; name: string; unitName: string }): Promise<unknown> {
  return apiPost('/api/finance/consumable-items', body);
}

export function purchaseConsumable(body: {
  itemCode: string;
  quantity: number;
  amount: number;
  supplier: string;
  yearMonth: string;
}): Promise<unknown> {
  return apiPost('/api/finance/consumable-purchases', body);
}

export function issueConsumable(body: {
  itemCode: string;
  quantity: number;
  customerId: number | null;
  yearMonth: string;
}): Promise<unknown> {
  return apiPost('/api/finance/consumable-issues', body);
}

export function saveFixedAsset(body: {
  name: string;
  category: string;
  originalValue: number;
  residualValue: number;
  startMonth: string;
  usefulMonths: number;
}): Promise<FixedAsset> {
  return apiPost('/api/finance/fixed-assets', body);
}

export function runDepreciation(yearMonth: string): Promise<unknown[]> {
  return apiPost('/api/finance/depreciation-runs', { yearMonth });
}

export function saveOutsource(body: {
  supplier: string;
  amount: number;
  yearMonth: string;
  customerId: number | null;
  motherOrderCode: string;
}): Promise<unknown> {
  return apiPost('/api/finance/outsource-bills', body);
}

export function fetchPayables(yearMonth: string): Promise<PayableView[]> {
  const month = yearMonth ? `?yearMonth=${encodeURIComponent(yearMonth)}` : '';
  return apiGet(`/api/finance/payables${month}`);
}

export function payPayable(body: {
  payableCode: string;
  amount: number;
  paidOn: string;
  idempotencyKey?: string;
}): Promise<unknown> {
  return apiPost('/api/finance/payable-payments', body);
}

export function fetchOperatingStatement(yearMonth: string, customerId: string): Promise<OperatingStatement> {
  const customer = customerId ? `&customerId=${encodeURIComponent(customerId)}` : '';
  return apiGet(`/api/finance/operating-statement?yearMonth=${encodeURIComponent(yearMonth)}${customer}`);
}

export interface FinanceDashboardScope {
  kind: string;
  customerIds: number[];
  note: string;
}

export interface FinanceCapability {
  part: string;
  available: boolean;
  reason: string;
}

export interface FinanceTrendPoint {
  yearMonth: string;
  throughQueryTime: boolean;
  operating: OperatingStatement | null;
}

export interface CurrentBalances {
  asOf: string;
  outstandingAmount: number | null;
  unpaidAmount: number | null;
  limitation: string;
}

export interface ReceivableRankRow {
  customerId: number;
  customerName: string;
  outstandingAmount: number;
}

export interface ReceivableRanking {
  total: number;
  returned: number;
  truncated: boolean;
  rows: ReceivableRankRow[];
}

export interface FinanceDashboard {
  yearMonth: string;
  generatedAt: string;
  scope: FinanceDashboardScope | null;
  operating: OperatingStatement | null;
  trend: FinanceTrendPoint[] | null;
  currentBalances: CurrentBalances | null;
  receivableRanking: ReceivableRanking | null;
  capabilities: FinanceCapability[];
}

export type FinanceDetailKind = 'REVENUE' | 'COST' | 'RECEIVABLE' | 'PAYABLE';

export interface FinanceDetailRow {
  sourceType: string;
  sourceCode: string;
  customerId: number | null;
  customerName: string;
  yearMonth: string;
  amount: number;
  note: string;
}

export interface FinanceDetailPage {
  kind: string;
  asOf: string;
  yearMonth: string;
  page: number;
  size: number;
  total: number;
  totalAmount: number | null;
  rows: FinanceDetailRow[];
}

export function fetchFinanceDashboard(yearMonth: string, customerId: string): Promise<FinanceDashboard> {
  const customer = customerId ? `&customerId=${encodeURIComponent(customerId)}` : '';
  return apiGet(`/api/finance/dashboard?yearMonth=${encodeURIComponent(yearMonth)}${customer}`);
}

export function fetchFinanceDetails(query: {
  kind: FinanceDetailKind;
  yearMonth: string;
  customerId: string;
  page: number;
  size: number;
}): Promise<FinanceDetailPage> {
  const params = new URLSearchParams();
  params.set('kind', query.kind);
  if (query.yearMonth) params.set('yearMonth', query.yearMonth);
  if (query.customerId) params.set('customerId', query.customerId);
  params.set('page', String(query.page));
  params.set('size', String(query.size));
  return apiGet(`/api/finance/dashboard/details?${params.toString()}`);
}
