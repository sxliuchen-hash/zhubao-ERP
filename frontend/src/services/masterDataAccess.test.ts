import { describe, expect, it } from 'vitest';
import { canManageMasterData } from './masterDataAccess';
describe('基础资料快捷新增权限', () => {
  it('以当前有效权限为准，岗位回退只用于旧会话', () => {
    expect(canManageMasterData({ role: 'RECEIVER', effectivePermissions: [] })).toBe(false);
    expect(canManageMasterData({ role: 'SALES', effectivePermissions: ['MASTER_DATA_MANAGE'] })).toBe(true);
    expect(canManageMasterData({ role: 'BUSINESS_MANAGER', coverDuties: ['MASTER'] })).toBe(true);
  });
});
