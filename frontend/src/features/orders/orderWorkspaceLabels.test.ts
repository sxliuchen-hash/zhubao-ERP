import { describe, expect, it } from 'vitest';
import { orderFacetLabel, orderQuantityText } from './orderWorkspaceLabels';

describe('orderWorkspaceLabels', () => {
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
