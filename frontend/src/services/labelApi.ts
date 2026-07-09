import { apiGet, apiPost } from './apiClient';

// 标签类型：母单 / 子工单 / 出货 / 返工
export type LabelType = 'MOTHER' | 'WORK_ORDER' | 'OUTBOUND' | 'REWORK';

// 标签上的一个信息字段（键值对）
export interface LabelField {
  name: string;
  value: string;
}

// 可打印标签载荷，前端据此渲染标签并生成二维码
export interface LabelPayload {
  type: LabelType;
  typeLabel: string;
  primaryCode: string;
  qrContent: string;
  fields: LabelField[];
  footerLeft: string;
  footerRight: string;
  printedAt: string;
}

// 通用标签生成请求
export interface GenerateLabelRequest {
  type: LabelType;
  primaryCode: string;
  fields?: LabelField[];
  footerLeft?: string;
  footerRight?: string;
}

// 通用标签生成（需要 LABEL_PRINT 权限）
export async function generateLabel(request: GenerateLabelRequest): Promise<LabelPayload> {
  return apiPost<GenerateLabelRequest, LabelPayload>('/api/label/generate', request);
}

// 按母单号便捷生成母单标签
export async function fetchMotherLabel(code: string): Promise<LabelPayload> {
  return apiGet<LabelPayload>(`/api/label/mother-orders/${encodeURIComponent(code)}`);
}

// 标签类型下拉选项
export const labelTypeOptions: Array<{ value: LabelType; label: string }> = [
  { value: 'MOTHER', label: '母单' },
  { value: 'WORK_ORDER', label: '子工单' },
  { value: 'OUTBOUND', label: '出货' },
  { value: 'REWORK', label: '返工' },
];
