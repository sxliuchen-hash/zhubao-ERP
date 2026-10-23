import type { ScanObjectType } from './scanContextApi';

/** 订单中心只读行。金额不在这个契约里。null 表示未知，不能显示成 0。 */
export interface OrderWorkspaceRow {
  rowKey: string;
  objectType: ScanObjectType;
  code: string;
  motherOrderCode: string;
  workOrderCode: string | null;
  executionRoundCode: string | null;
  currentProcess: string | null;
  customerId: number | null;
  customerName: string | null;
  goodsName: string | null;
  materialCode: string | null;
  materialName: string | null;
  quantity: string | null;
  quantityUnit: string | null;
  weight: string | null;
  weightUnit: string | null;
  dueDate: string | null;
  businessStatus: string;
  facet: string;
  facetNote: string | null;
  processPercent: 0 | 30 | 60 | 90 | 100 | null;
  processProgressVersion: number | null;
  processFormallyDelivered: boolean | null;
  operatorName: string | null;
  updatedAt: string | null;
}

export interface OrderWorkspacePage {
  items: OrderWorkspaceRow[];
  page: number;
  size: number;
  totalRows: number;
  facetCounts: Record<string, number>;
  availableViews: Array<'ORDERS' | 'PRODUCTION' | 'QUALITY'>;
  generatedAt: string;
}

export interface OrderWorkspaceQuery {
  view?: 'ORDERS' | 'PRODUCTION' | 'QUALITY';
  query?: string;
  customerId?: string;
  process?: string;
  facet?: string;
  dueBefore?: string;
  page?: number;
  size?: number;
}
