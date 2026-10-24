import { describe, expect, it } from 'vitest';
import { orderFacetLabel, orderProductionStatus, orderQuantityText } from './orderWorkspaceLabels';
import type { OrderWorkspaceRow } from '../../services/orderWorkspaceTypes';

describe('orderWorkspaceLabels', () => {
  it('百分比仅解释在制工序，不能覆盖质检、返工、发货或已交工事实', () => {
    const row = { facet: 'IN_PROCESS', businessStatus: '在制', processPercent: 100, processFormallyDelivered: false } as OrderWorkspaceRow;
    expect(orderProductionStatus(row)).toBe('本工序完成，待交工');
    expect(orderProductionStatus({ ...row, processPercent: 60 })).toBe('加工中');
    expect(orderProductionStatus({ ...row, facet: 'WAITING_QUALITY', businessStatus: '待质检', processFormallyDelivered: true })).toBe('待质检');
    expect(orderProductionStatus({ ...row, facet: 'REWORK', businessStatus: '返工' })).toBe('返工');
    expect(orderProductionStatus({ ...row, facet: 'SHIPPED', businessStatus: '已发货' })).toBe('已发货');
  });
  it('分类文字不改写正式状态，未知分类显示待核对', () => {
    expect(orderFacetLabel('IN_PROCESS')).toBe('在制');
    expect(orderFacetLabel('UNKNOWN')).toBe('待核对');
    expect(orderFacetLabel('已质检')).toBe('待核对');
    expect(orderFacetLabel(null)).toBe('待核对');
  });

  it('数量保留原文字，不把缺失显示成 0', () => {
    expect(orderQuantityText('10.5', '克')).toBe('10.5 克');
    expect(orderQuantityText('120', '颗')).toBe('120 颗');
    expect(orderQuantityText(null, '件')).toBe('数量未填写');
  });
});
