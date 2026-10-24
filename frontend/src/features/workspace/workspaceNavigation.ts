/**
 * 展示分区只决定导航怎么归组。
 * 菜单编码仍是原来的 TabKey，不能凭分区名放宽服务端授权。
 */

export type WorkspaceSection =
  | 'desk'
  | 'orders'
  | 'production'
  | 'quality'
  | 'inventory'
  | 'customers'
  | 'finance'
  | 'approvals'
  | 'tools'
  | 'system';

export type TabKey =
  | 'dashboard'
  | 'inbound'
  | 'customers'
  | 'production'
  | 'progress'
  | 'quality'
  | 'inventory'
  | 'finance'
  | 'approvals'
  | 'configuration'
  | 'users'
  | 'documents'
  | 'sample'
  | 'label'
  | 'hardware'
  | 'operations'
  | 'mobile';

export interface WorkspaceTab {
  key: TabKey;
  label: string;
  meta: string;
  code: string;
  section: WorkspaceSection;
}

export const workspaceSections: Array<{ id: WorkspaceSection; label: string }> = [
  { id: 'desk', label: '工作台' },
  { id: 'orders', label: '订单' },
  { id: 'production', label: '生产作业' },
  { id: 'quality', label: '质量作业' },
  { id: 'inventory', label: '库存' },
  { id: 'customers', label: '客户' },
  { id: 'finance', label: '财务' },
  { id: 'approvals', label: '审批' },
  { id: 'tools', label: '业务工具' },
  { id: 'system', label: '系统管理' },
];

/** 手机底栏「订单」收进的原页面。库存单独占一格，不进这里。 */
export const mobileOrderTabKeys = ['inbound', 'progress', 'production', 'quality'] as const satisfies readonly TabKey[];

export const workspaceTabs: WorkspaceTab[] = [
  { key: 'dashboard', label: '首页', meta: '今日待办', code: 'OP', section: 'desk' },
  { key: 'inbound', label: '来料', meta: '收货入库', code: 'IN', section: 'orders' },
  { key: 'progress', label: '进度', meta: '在制筛选', code: 'PG', section: 'orders' },
  { key: 'production', label: '生产', meta: '派工报工', code: 'MO', section: 'production' },
  { key: 'quality', label: '质检', meta: '返工放行', code: 'QC', section: 'quality' },
  { key: 'inventory', label: '库存', meta: '出库退货', code: 'ST', section: 'inventory' },
  { key: 'customers', label: '客户档案', meta: '账单与进度', code: 'CU', section: 'customers' },
  { key: 'finance', label: '财务', meta: '客户账成本', code: 'FN', section: 'finance' },
  { key: 'approvals', label: '审批', meta: '待我处理', code: 'AP', section: 'approvals' },
  { key: 'label', label: '标签', meta: '打印补打', code: 'LB', section: 'tools' },
  { key: 'documents', label: '附件', meta: '照片视频', code: 'EV', section: 'tools' },
  { key: 'sample', label: '登记导入', meta: 'Excel 入账', code: 'SA', section: 'tools' },
  { key: 'users', label: '用户管理', meta: '账号与权限', code: 'US', section: 'system' },
  { key: 'hardware', label: '设备', meta: '读数接入', code: 'HW', section: 'system' },
  { key: 'operations', label: '运维', meta: '硬件存储', code: 'OC', section: 'system' },
  { key: 'configuration', label: '配置', meta: '个人与存储', code: 'CF', section: 'system' },
  { key: 'mobile', label: '手机作业', meta: '收货报工', code: 'PW', section: 'system' },
];

const tabByKey = new Map(workspaceTabs.map((tab) => [tab.key, tab]));

export function isTabKey(value: string): value is TabKey {
  return tabByKey.has(value as TabKey);
}

export function sectionOfTab(key: TabKey): WorkspaceSection {
  return tabByKey.get(key)?.section ?? 'desk';
}

export function sectionLabel(section: WorkspaceSection): string {
  return workspaceSections.find((item) => item.id === section)?.label ?? '';
}

export function groupTabsBySection<T extends { key: TabKey }>(tabs: T[]) {
  return workspaceSections
    .map((section) => ({
      id: section.id,
      label: section.label,
      tabs: tabs.filter((tab) => sectionOfTab(tab.key) === section.id),
    }))
    .filter((group) => group.tabs.length > 0);
}

export function filterWorkspaceTabs<T extends { label: string; meta: string }>(tabs: T[], query: string): T[] {
  const text = query.trim();
  if (!text) return tabs;
  return tabs.filter((tab) => tab.label.includes(text) || tab.meta.includes(text));
}

/** 按固定顺序取出当前账号真正获准的订单类页面。 */
export function mobileOrderTabs<T extends { key: TabKey }>(visible: T[]): T[] {
  return mobileOrderTabKeys.flatMap((key) => {
    const tab = visible.find((item) => item.key === key);
    return tab ? [tab] : [];
  });
}

/** 底栏已经占住的页面不再放进「更多」。 */
export function mobileMoreTabs<T extends { key: TabKey }>(visible: T[]): T[] {
  const dock = new Set<TabKey>(['dashboard', 'inventory', ...mobileOrderTabKeys]);
  return visible.filter((tab) => !dock.has(tab.key));
}

/** 桌面把来料和进度收进订单中心；岗位入口仍携带真实获准菜单编码。 */
export function desktopNavigationGroups(visible: WorkspaceTab[]) {
  const productionCode = visible.some(tab => tab.key === 'production') ? 'production' : 'progress';
  const entries = visible.flatMap(tab => {
    if (tab.key === 'inbound' || (tab.key === 'progress' && productionCode !== 'progress')) return [];
    if (tab.key === productionCode) return [{ ...tab, label: '生产作业', section: 'production' as const }];
    if (tab.key === 'quality') return [{ ...tab, label: '质量作业', section: 'quality' as const }];
    const desktopLabel: Partial<Record<TabKey, string>> = {
      dashboard: '工作台',
      inventory: '库存与交付',
      finance: '财务经营',
      approvals: '审批中心',
    };
    return [{ ...tab, label: desktopLabel[tab.key] ?? tab.label }];
  });
  return workspaceSections.map(section => ({ ...section, tabs: entries.filter(tab => tab.section === section.id) })).filter(group => group.tabs.length);
}
