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

export function fetchFixedAssetCards(): Promise<FixedAssetCardView[]> {
  return apiGet('/api/finance/fixed-asset-cards');
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
