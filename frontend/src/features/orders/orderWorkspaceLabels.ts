/** 展示分类的文字。不能拿它替换正式业务状态。 */
import type { OrderWorkspaceRow } from '../../services/orderWorkspaceTypes';
const facetLabels: Record<string, string> = {
  ALL: '全部',
  UNSPLIT: '待拆分',
  WAITING_START: '待开工',
  IN_PROCESS: '在制',
  WAITING_QUALITY: '待质检',
  REWORK: '返工',
  READY_TO_SHIP: '可发货',
  SHIPPED: '已发货',
  UNKNOWN: '待核对',
};

export function orderFacetLabel(facet: string | null | undefined): string {
  if (!facet) return '待核对';
  return facetLabels[facet] ?? '待核对';
}

export function orderQuantityText(quantity: string | null | undefined, unit: string | null | undefined): string {
  if (!quantity) return '数量未填写';
  return unit ? `${quantity} ${unit}` : quantity;
}

/** 只有仍在生产且尚未交工的当前工序，才按百分比提示下一步。 */
export function orderProductionStatus(row: OrderWorkspaceRow): string {
  if (['WAITING_START', 'IN_PROCESS'].includes(row.facet) && row.processFormallyDelivered === false) {
    if (row.processPercent === 100) return '本工序完成，待交工';
    if (row.processPercent !== null && row.processPercent > 0) return '加工中';
  }
  return row.businessStatus || '待核对';
}
