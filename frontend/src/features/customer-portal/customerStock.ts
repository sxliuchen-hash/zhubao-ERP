export type StockBucket = 'CONSIGNMENT' | 'IN_FACTORY' | 'OUTSOURCING' | 'QUALITY' | 'SHIPPED';
export type WeightRead = 'KNOWN' | 'UNKNOWN' | 'INCOMPLETE';

export interface CustomerStockLine {
  materialCode: string;
  unit: string;
  bucket: StockBucket;
  quantity: string | number;
  weightRead: WeightRead;
  knownWeight: string | number | null;
  reconciliationPending: boolean;
}

export interface CustomerStockSource {
  kind: string;
  state: string;
  note: string;
  withheld: boolean;
}

export interface CustomerOwnedStock {
  customerId: number;
  lines: CustomerStockLine[];
  sources: CustomerStockSource[];
}

export interface CustomerEvidenceItem {
  grantId: string;
  attachmentCode: string;
  fileName: string;
  mediaType: string;
  kind: string;
  downloadTicket: string;
}

export interface CustomerEvidenceDetail {
  customerId: number;
  visibility: string;
  items: CustomerEvidenceItem[];
}

const bucketText: Record<StockBucket, string> = {
  CONSIGNMENT: '寄存',
  IN_FACTORY: '本厂',
  OUTSOURCING: '外协',
  QUALITY: '质量',
  SHIPPED: '已发',
};

const bucketNote: Record<StockBucket, string> = {
  CONSIGNMENT: '已收且尚未进入加工的客户料',
  IN_FACTORY: '本厂保管。外协中的数量已划出，不重复计入',
  OUTSOURCING: '外协是其中项，不重复计入本厂，也不能当厂内可发',
  QUALITY: '待质检、返工或暂缓，不并入可交成品',
  SHIPPED: '历史出厂记录，不算当前在厂',
};

export function bucketLabel(bucket: StockBucket): string {
  return bucketText[bucket];
}

export function bucketMeaning(bucket: StockBucket): string {
  return bucketNote[bucket];
}

export function weightText(line: CustomerStockLine): string {
  if (line.weightRead === 'UNKNOWN' || line.knownWeight == null) return '未称';
  if (line.weightRead === 'INCOMPLETE') return `已知 ${line.knownWeight}，重量不完整`;
  return String(line.knownWeight);
}

export function filterLines(lines: CustomerStockLine[], material: string): CustomerStockLine[] {
  const text = material.trim();
  if (!text) return lines;
  return lines.filter((line) => line.materialCode.includes(text) || line.unit.includes(text));
}
