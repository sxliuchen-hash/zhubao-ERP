/** 展示分类的文字。不能拿它替换正式业务状态。 */
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
