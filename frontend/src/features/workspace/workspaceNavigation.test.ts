import { describe, expect, it } from 'vitest';
import {
  filterWorkspaceTabs,
  groupTabsBySection,
  isTabKey,
  mobileMoreTabs,
  mobileOrderTabs,
  sectionOfTab,
  workspaceTabs,
} from './workspaceNavigation';

describe('workspaceNavigation', () => {
  it('把现有 17 个页面各归入一个展示分区，不新增菜单编码', () => {
    expect(workspaceTabs).toHaveLength(17);
    expect(new Set(workspaceTabs.map((tab) => tab.key)).size).toBe(17);
    expect(workspaceTabs.map((tab) => tab.key)).not.toContain('orders');
    expect(workspaceTabs.map((tab) => tab.key)).not.toContain('tools');
    expect(isTabKey('progress')).toBe(true);
    expect(isTabKey('orders')).toBe(false);
  });

  it('桌面分组按分区排列，空分区不出现', () => {
    const groups = groupTabsBySection(workspaceTabs.filter((tab) => ['dashboard', 'production', 'mobile'].includes(tab.key)));
    expect(groups.map((group) => group.label)).toEqual(['工作台', '生产作业', '系统管理']);
    expect(sectionOfTab('inbound')).toBe('orders');
    expect(sectionOfTab('label')).toBe('tools');
  });

  it('手机订单入口只收来料、进度、生产和质检，库存与其余页面分开', () => {
    const visible = workspaceTabs.filter((tab) => tab.key !== 'users');
    expect(mobileOrderTabs(visible).map((tab) => tab.key)).toEqual(['inbound', 'progress', 'production', 'quality']);
    expect(mobileMoreTabs(visible).map((tab) => tab.key)).toEqual([
      'customers',
      'finance',
      'approvals',
      'label',
      'documents',
      'sample',
      'hardware',
      'operations',
      'configuration',
      'mobile',
    ]);
    expect(mobileMoreTabs(visible).map((tab) => tab.key)).not.toContain('inventory');
    expect(mobileOrderTabs(workspaceTabs.filter((tab) => tab.key === 'inventory'))).toEqual([]);
  });

  it('更多里的筛选只匹配名称和说明', () => {
    expect(filterWorkspaceTabs(workspaceTabs, '打印').map((tab) => tab.key)).toEqual(['label']);
    expect(filterWorkspaceTabs(workspaceTabs, '  ')).toHaveLength(17);
  });
});
