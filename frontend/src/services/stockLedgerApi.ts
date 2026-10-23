import { apiGet } from './apiClient';

export interface StockLedgerDimension {
  ownerType: string;
  ownerCustomerId: number | null;
  customerId: number | null;
  motherOrderCode: string;
  workOrderCode: string;
  materialCode: string;
  warehouseCode: string;
  stockStatus: string;
  batchCode?: string;
}
export interface StockLedgerRecord {
  code: string;
  bizTime: string;
  documentType: string;
  documentCode: string;
  from: StockLedgerDimension | null;
  to: StockLedgerDimension | null;
  quantity: number | string;
  weight: number | string;
  remark: string;
  sourceLedgerCode?: string | null;
}

/** 接口返回当前会话可见的全部台账，范围继续由服务端过滤。 */
export function fetchStockLedgers(): Promise<StockLedgerRecord[]> { return apiGet('/api/inventory/ledgers'); }
