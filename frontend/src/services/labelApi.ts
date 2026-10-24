import { apiGet, apiPost } from './apiClient';

// 标签类型：母单 / 子工单 / 出货 / 返工
export type LabelType = 'MOTHER' | 'WORK_ORDER' | 'OUTBOUND' | 'REWORK' | 'STOCK_BATCH';

// 标签上的一个信息字段（键值对）
export interface LabelField {
  name: string;
  value: string;
}

// 打印机设置：尺寸、DPI、打印机名（浏览器端仅提示）、份数
export interface LabelPrinterSetting {
  widthMm: number;
  heightMm: number;
  dpi: number;
  printerName: string;
  copies: number;
}

// 标签版式只接受预设布局和受限的毫米、磅值，避免把任意样式注入打印页。
export interface LabelLayout {
  mode: 'AUTO' | 'COMPACT' | 'STANDARD';
  qrWidthMm: number | null;
  codeFontPt: number | null;
  fieldFontPt: number | null;
  emphasisFontPt: number | null;
  titleFontPt: number | null;
  footerFontPt: number | null;
}

// 可打印标签载荷，前端据此渲染标签并生成二维码
export interface StorageLabelSource { sourceCode: string; sourceLabel: string; materialCode: string; recorded: boolean; marks: string[]; instruction: string }
export interface LabelPayload {
  storageSources?: StorageLabelSource[];
  storageEdition?: number;
  snapshotHash?: string;
  storageHeightMm?: number;
  type: LabelType;
  typeLabel: string;
  primaryCode: string;
  qrContent: string;
  fields: LabelField[];
  footerLeft: string;
  footerRight: string;
  printedAt: string;
  brandTitle: string;
  templateCode: string;
  printerSetting: LabelPrinterSetting;
  layout?: LabelLayout;
}

// 通用标签生成请求
export interface GenerateLabelRequest {
  type: LabelType;
  primaryCode: string;
  fields?: LabelField[];
  footerLeft?: string;
  footerRight?: string;
  templateCode?: string;
}

// 标签模板字段定义（用户自定义标签展示哪些字段、来源与顺序）
export interface LabelTemplateField {
  label: string;
  source: string;
  sortOrder: number;
}

// 标签模板
export interface LabelTemplate {
  id: number;
  templateCode: string;
  templateName: string;
  labelType: LabelType;
  brandTitle: string;
  fields: LabelTemplateField[];
  footerLeftSource: string;
  footerRightSource: string;
  isDefault: boolean;
  enabled: boolean;
  printerSetting: LabelPrinterSetting;
  layout?: LabelLayout;
  remark: string;
  createdBy: string;
  createdAt: string;
}

// 新增/更新标签模板请求
export interface SaveLabelTemplateRequest {
  id?: number;
  templateCode: string;
  templateName: string;
  labelType: LabelType;
  brandTitle?: string;
  fields?: LabelTemplateField[];
  footerLeftSource?: string;
  footerRightSource?: string;
  isDefault?: boolean;
  enabled?: boolean;
  widthMm?: number;
  heightMm?: number;
  dpi?: number;
  printerName?: string;
  copies?: number;
  remark?: string;
  layout?: LabelLayout;
  operator?: string;
}

// 通用标签生成（需要 LABEL_PRINT 权限）
export async function generateLabel(request: GenerateLabelRequest): Promise<LabelPayload> {
  return apiPost<GenerateLabelRequest, LabelPayload>('/api/label/generate', request);
}

// 按母单号便捷生成母单标签，可指定模板编码
export async function fetchMotherLabel(code: string, templateCode?: string): Promise<LabelPayload> {
  const query = templateCode ? `?templateCode=${encodeURIComponent(templateCode)}` : '';
  return apiGet<LabelPayload>(`/api/label/mother-orders/${encodeURIComponent(code)}${query}`);
}

export async function fetchWorkOrderLabel(code: string, templateCode?: string): Promise<LabelPayload> {
  const query = templateCode ? `?templateCode=${encodeURIComponent(templateCode)}` : '';
  return apiGet<LabelPayload>(`/api/label/work-orders/${encodeURIComponent(code)}${query}`);
}

export interface ReprintLabelRequest {
  expectedSnapshotHash?: string;
  type: LabelType;
  primaryCode: string;
  reason: string;
}

export async function reprintLabel(request: ReprintLabelRequest): Promise<LabelPayload> {
  return apiPost<ReprintLabelRequest, LabelPayload>('/api/label/reprints', request);
}

// 查询全部标签模板
export async function fetchLabelTemplates(): Promise<LabelTemplate[]> {
  return apiGet<LabelTemplate[]>('/api/label/templates');
}

// 新增或更新标签模板（需要 LABEL_PRINT 权限）
export async function saveLabelTemplate(request: SaveLabelTemplateRequest): Promise<LabelTemplate> {
  return apiPost<SaveLabelTemplateRequest, LabelTemplate>('/api/label/templates', request);
}

// 标签类型下拉选项
export const labelTypeOptions: Array<{ value: LabelType; label: string }> = [
  { value: 'STOCK_BATCH', label: '寄存批次' },
  { value: 'MOTHER', label: '母单' },
  { value: 'WORK_ORDER', label: '子工单' },
  { value: 'OUTBOUND', label: '出货' },
  { value: 'REWORK', label: '返工' },
];

// 母单标签字段来源下拉选项（模板编辑用）
export const motherFieldSourceOptions: Array<{ value: string; label: string }> = [
  { value: 'verificationCode', label: '验证码' },
  { value: 'motherOrderCode', label: '所属母单号' },
  { value: 'customer', label: '客户' },
  { value: 'customerInitials', label: '客户首字母暗记' },
  { value: 'processingRequirement', label: '加工要求' },
  { value: 'dueDate', label: '期限' },
  { value: 'code', label: '单号' },
  { value: 'goodsName', label: '物料名称' },
  { value: 'materialCode', label: '物料编码' },
  { value: 'quantity', label: '数量' },
  { value: 'weight', label: '重量' },
  { value: 'inboundDate', label: '收货日' },
  { value: 'warehouseCode', label: '仓库' },
  { value: 'receiver', label: '收货人' },
  { value: 'express', label: '快递' },
];

export const fetchStockBatchLabel = (code: string) => apiGet<LabelPayload>(`/api/label/stock-batches/${encodeURIComponent(code)}`);

export const adoptStorageEdition = (code: string, body: { requestId: string; expectedEdition: number; reason: string; templateCode: string }) =>
  apiPost<typeof body, number>(`/api/label/${encodeURIComponent(code)}/storage-editions`, body);

export interface LabelStorageEdition { businessCode: string; edition: number; sources: StorageLabelSource[]; reason: string; operator: string; createdAt: string }
export const fetchStorageEditions = (code: string) => apiGet<LabelStorageEdition[]>(`/api/label/${encodeURIComponent(code)}/storage-editions`);
