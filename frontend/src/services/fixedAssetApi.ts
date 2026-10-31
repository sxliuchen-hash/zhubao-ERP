import { apiGet, apiPost } from './apiClient';

export interface FixedAssetField {
  presence: string;
  value: string;
  label: string;
}

export interface FixedAssetCardView {
  code: string;
  name: string;
  category: string;
  originalValue: number;
  residualValue: number;
  startMonth: string;
  usefulMonths: number;
  model: FixedAssetField;
  serialNumber: FixedAssetField;
  location: FixedAssetField;
  custodian: FixedAssetField;
  useStatus: FixedAssetField;
  version: number;
}

export interface FixedAssetAcquisitionView {
  acquisitionId: string;
  assetCode: string;
  source: string;
  amountState: string;
  originalValue: number | null;
  supplierLabel: string;
  acquiredOnLabel: string;
  handoverLocationLabel: string;
  handoverReceiverLabel: string;
  payableCode: string;
  coveredPayableCode: string;
  paymentCode: string;
  paidAmount: number | null;
  paid: boolean;
}

export interface FixedAssetRepairView {
  repairId: string;
  assetCode: string;
  originalValue: number;
  repairedOn: string;
  handoverLabel: string;
  evidenceLabel: string;
  state: string;
  stateLabel: string;
  payableCode: string;
  costAmount: number | null;
  paidAmount: number | null;
  paid: boolean;
}

export function fetchAssetRepairs(assetCode: string): Promise<FixedAssetRepairView[]> {
  return apiGet(`/api/finance/fixed-asset-repairs?assetCode=${encodeURIComponent(assetCode)}`);
}

export function recordAssetRepair(body: {
  requestId: string;
  assetCode: string;
  repairedOn: string;
  handoverName: string;
  evidenceCode: string;
}): Promise<FixedAssetRepairView> {
  return apiPost('/api/finance/fixed-asset-repairs', body);
}

export function confirmAssetRepair(body: {
  requestId: string;
  repairId: string;
  amount: number | null;
  evidenceCode: string;
}): Promise<FixedAssetRepairView> {
  return apiPost('/api/finance/fixed-asset-repairs/cost-confirmations', body);
}

export function payAssetRepair(body: {
  requestId: string;
  repairId: string;
  amount: number | null;
  paidOn: string;
  voucher: string;
}): Promise<FixedAssetRepairView> {
  return apiPost('/api/finance/fixed-asset-repairs/payments', body);
}

export interface FixedAssetDepreciationRun {
  yearMonth: string;
  algorithm: string;
  entries: Array<{ code: string; assetCode: string; yearMonth: string; amount: number }>;
}

export function continueAssetDepreciation(body: {
  requestId: string;
  yearMonth: string;
  stopEffectiveMonth: string | null;
}): Promise<FixedAssetDepreciationRun> {
  return apiPost('/api/finance/depreciation-continuations', body);
}

export function fetchFixedAssetCards(): Promise<FixedAssetCardView[]> {
  return apiGet('/api/finance/fixed-asset-cards');
}

export interface FixedAssetCustodyView {
  transferId: string;
  assetCode: string;
  originalValue: number;
  fromLocationLabel: string;
  toLocationLabel: string;
  fromCustodianLabel: string;
  toCustodianLabel: string;
  fromStatusLabel: string;
  toStatusLabel: string;
  version: number;
}

export function fetchAssetCustody(assetCode: string): Promise<FixedAssetCustodyView[]> {
  return apiGet(`/api/finance/fixed-asset-custody-transfers?assetCode=${encodeURIComponent(assetCode)}`);
}

export function transferAssetCustody(body: {
  requestId: string;
  assetCode: string;
  expectedVersion: number;
  location: string;
  custodian: string;
  useStatus: string;
}): Promise<FixedAssetCustodyView> {
  return apiPost('/api/finance/fixed-asset-custody-transfers', body);
}

export function receiveFixedAsset(body: {
  requestId: string;
  source: string;
  assetCode: string;
  name: string;
  category: string;
  originalValue: number | null;
  residualValue: number | null;
  startMonth: string;
  usefulMonths: number | null;
  supplierName: string;
  acquiredOn: string;
  handoverLocation: string;
  handoverReceiver: string;
  coveredPayableCode: string;
  paymentAmount: number | null;
  paymentDate: string;
  paymentVoucher: string;
}): Promise<FixedAssetAcquisitionView> {
  return apiPost('/api/finance/fixed-asset-acquisitions', body);
}
