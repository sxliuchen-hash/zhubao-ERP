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

export interface FixedAssetDispositionView {
  dispositionId: string;
  assetCode: string;
  originalValue: number;
  kind: string;
  kindLabel: string;
  occurredOn: string;
  basis: string;
  depreciationImpact: string;
  depreciationImpactLabel: string;
  netValue: number | null;
  receiptCode: string;
  proceedsAmount: number | null;
  received: boolean;
}

export function fetchAssetDispositions(assetCode: string): Promise<FixedAssetDispositionView[]> {
  return apiGet(`/api/finance/fixed-asset-dispositions?assetCode=${encodeURIComponent(assetCode)}`);
}

export function recordAssetDisposition(body: {
  requestId: string;
  assetCode: string;
  kind: string;
  occurredOn: string;
  basis: string;
  receiptCode: string;
  proceeds: number | null;
}): Promise<FixedAssetDispositionView> {
  return apiPost('/api/finance/fixed-asset-dispositions', body);
}

export interface FixedAssetStocktake {
  stocktakeId: string;
  assetCode: string;
  countedAt: string;
  bookLocation: string | null;
  bookCustodian: string | null;
  bookStatus: string | null;
  bookVersion: number;
  seen: string | null;
  countLocation: string | null;
  countCustodian: string | null;
  exceptionText: string;
  status: string;
  version: number;
}

export function openAssetStocktake(body: {
  requestId: string;
  assetCode: string;
  countedAt: string;
}): Promise<FixedAssetStocktake> {
  return apiPost('/api/finance/fixed-asset-stocktakes', body);
}

export function countAssetStocktake(body: {
  requestId: string;
  stocktakeId: string;
  expectedVersion: number;
  seen: string;
  location: string;
  custodian: string;
  exceptionText: string;
}): Promise<FixedAssetStocktake> {
  return apiPost('/api/finance/fixed-asset-stocktakes/counts', body);
}

export function confirmAssetStocktake(body: {
  requestId: string;
  stocktakeId: string;
  expectedVersion: number;
}): Promise<FixedAssetStocktake> {
  return apiPost('/api/finance/fixed-asset-stocktakes/confirmations', body);
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
