import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import WorkspaceLoadState from './components/WorkspaceLoadState.vue';
import OrderActionHost from './features/orders/OrderActionHost.vue';
import { armMobileScan, bindMobileScanTarget, unbindMobileScanTarget } from './features/scan/mobileScanBridge';
import { authSessionChangedEvent, fetchAuthSession, type AuthSession } from './services/authApi';
import { fetchOrderWorkspace } from './services/orderWorkspaceApi';
import { queryScanContext } from './services/scanContextApi';
import { buildScanContext } from './features/scan/scanContextFixture';

vi.mock('./services/orderWorkspaceApi', () => ({ fetchOrderWorkspace: vi.fn() }));
vi.mock('./services/approvalApi', () => ({ fetchPendingApprovalTasks: vi.fn().mockResolvedValue([]), fetchApprovalTasks: vi.fn().mockResolvedValue([]), submitApprovalDecision: vi.fn() }));
vi.mock('./services/masterDataApi', async () => ({ ...await vi.importActual<typeof import('./services/masterDataApi')>('./services/masterDataApi'), fetchCustomers: vi.fn().mockResolvedValue([]), fetchMaterials: vi.fn().mockResolvedValue([]), fetchShortcuts: vi.fn().mockResolvedValue([]), fetchTemplates: vi.fn().mockResolvedValue([]) }));
vi.mock('./services/scanContextApi', async () => ({
  ...await vi.importActual<typeof import('./services/scanContextApi')>('./services/scanContextApi'), queryScanContext: vi.fn(),
}));

vi.mock('./services/authApi', () => ({
  authSessionChangedEvent: 'zhubao-auth-session-changed',
  fetchAuthSession: vi.fn(),
  fetchAuthDemoUsers: vi.fn().mockResolvedValue(['admin']),
  loginAuthSession: vi.fn(),
  logoutAuthSession: vi.fn().mockResolvedValue(undefined),
  changeOwnPassword: vi.fn(),
}));
vi.mock('./features/m1/HardwareIntegrationWorkspace.vue', () => { throw new Error('页面文件下载失败'); });

describe('App', () => {
  beforeEach(() => {
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    localStorage.clear();
    vi.mocked(fetchAuthSession).mockResolvedValue(null);
    vi.mocked(fetchOrderWorkspace).mockReset().mockResolvedValue({ items: [], page: 1, size: 20, totalRows: 0, facetCounts: {}, availableViews: ['ORDERS', 'PRODUCTION', 'QUALITY'], generatedAt: '' });
    vi.mocked(queryScanContext).mockReset().mockImplementation(async input => buildScanContext({ objectType: input.objectType ?? 'MOTHER_ORDER', code: input.content }));
  });

  it('shows the login page before a session exists', async () => {
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('.login-card').text()).toContain('登录');
    expect(wrapper.find('.ops-nav').exists()).toBe(false);
  });

  it('keeps the saved login when the server is temporarily unreachable', async () => {
    localStorage.setItem('zhubao-auth-token', 'saved-token');
    vi.mocked(fetchAuthSession).mockRejectedValueOnce(new Error('后端接口不可用'));

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('暂时连不上服务器');
    expect(wrapper.text()).toContain('登录信息还在这台设备上');
    expect(localStorage.getItem('zhubao-auth-token')).toBe('saved-token');
    expect(wrapper.find('.ops-nav').exists()).toBe(false);
  });

  it('starts on the M1 business workbench and exposes core modules', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(allMenuCodes));
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('来料加工');
    expect(wrapper.findAll('.nav-group-label').map((label) => label.text())).toEqual([
      '业务工具',
      '系统管理',
    ]);
    expect(wrapper.text()).toContain('订单事项');
    expect(wrapper.text()).toContain('今天的工作');
    expect(wrapper.find('[aria-label="业务单据和当前处理单"]').exists()).toBe(false);
    expect(wrapper.find('.ops-scope-ribbon').exists()).toBe(false);
    expect(wrapper.find('.ops-nav').text()).toContain('首页');
    expect(wrapper.find('.ops-nav').text()).toContain('订单中心');
    expect(wrapper.find('[data-tab="inbound"]').exists()).toBe(false);
    expect(wrapper.find('[data-tab="progress"]').exists()).toBe(false);
    expect(wrapper.find('.ops-nav').text()).toContain('客户档案');
    expect(wrapper.find('.ops-nav').text()).toContain('生产');
    expect(wrapper.find('.ops-nav').text()).toContain('质量作业');
    expect(wrapper.find('.ops-nav').text()).toContain('库存');
    expect(wrapper.find('.ops-nav').text()).toContain('审批');
    expect(wrapper.find('.ops-nav').text()).toContain('标签');
    expect(wrapper.find('.ops-nav').text()).toContain('登记导入');
    expect(wrapper.find('.ops-nav').text()).toContain('附件');
    expect(wrapper.find('.ops-nav').text()).toContain('设备');
    expect(wrapper.find('.ops-nav').text()).toContain('配置');
    expect(wrapper.find('.ops-nav').text()).toContain('运维');
    expect(wrapper.find('.ops-nav').text()).toContain('手机作业');
    expect(wrapper.get('.page-location').text()).toBe('工作台');
    expect(wrapper.get('.mobile-tabbar').text()).toContain('待办');
    expect(wrapper.get('.mobile-tabbar').text()).toContain('订单');
    expect(wrapper.get('.mobile-tabbar').text()).toContain('库存');
    expect(wrapper.get('.mobile-tabbar').text()).toContain('更多');
    expect(wrapper.get('.mobile-tabbar').text()).not.toContain('现场作业');
    expect(wrapper.get('.mobile-tabbar').text()).not.toContain('审批');
    expect(wrapper.get('.mobile-tabbar').text()).not.toContain('消息');
    expect(wrapper.get('[aria-label="扫码"]').classes()).toContain('mobile-scan-key');
    expect(wrapper.get('[aria-label="版本升级通知"]').text()).toContain('单据维护会列出未回收母单');
    await wrapper.get('[aria-label="版本升级通知"] button').trigger('click');
    expect(wrapper.find('[aria-label="版本升级通知"]').exists()).toBe(false);
    expect(localStorage.getItem('zhubao-upgrade-notice-20261020')).toBe('1');
  });
  it('岗位导航读取同一 Q1，并保留原业务工具入口而不循环回到岗位列表', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(allMenuCodes));
    const wrapper = mount(App); await flushPromises();
    await wrapper.get('[data-tab="production"]').trigger('click'); await flushPromises();
    await vi.waitFor(() => expect(wrapper.find('[aria-label="订单查询列表"]').exists()).toBe(true), { timeout: 5000 });
    expect(wrapper.get('h1').text()).toBe('生产作业');
    expect(fetchOrderWorkspace).toHaveBeenCalledWith(expect.objectContaining({ view: 'PRODUCTION', size: 20 }));
    expect(wrapper.get('[data-tab="production"]').classes()).toContain('active');
    await wrapper.get('[data-tab="quality"]').trigger('click'); await flushPromises();
    expect(wrapper.get('h1').text()).toBe('质量作业');
    expect(fetchOrderWorkspace).toHaveBeenCalledWith(expect.objectContaining({ view: 'QUALITY', size: 20 }));
    await wrapper.findAll('.order-legacy-actions button').find(button => button.text() === '质检记录、损耗与工资试算')!.trigger('click'); await flushPromises();
    expect(wrapper.get('h1').text()).toBe('质检');
    expect(wrapper.find('[aria-label="订单查询列表"]').exists()).toBe(false); wrapper.unmount();
  });
  it('只有进度菜单仍可进入生产岗位查询，空菜单不会展开为全量管理菜单', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'progress']));
    const progress = mount(App); await flushPromises(); await progress.get('[data-tab="progress"]').trigger('click'); await flushPromises();
    await vi.waitFor(() => expect(progress.find('[aria-label="订单查询列表"]').exists()).toBe(true), { timeout: 5000 });
    expect(fetchOrderWorkspace).toHaveBeenCalledWith(expect.objectContaining({ view: 'PRODUCTION', size: 20 })); progress.unmount();
    vi.mocked(fetchAuthSession).mockResolvedValueOnce({ ...sessionWithMenus([]), role: 'ADMIN', effectivePermissions: [] });
    const empty = mount(App); await flushPromises();
    expect(empty.find('[data-workspace-section="orders"]').exists()).toBe(false);
    expect(empty.find('[data-tab="finance"]').exists()).toBe(false); expect(empty.find('[data-tab="users"]').exists()).toBe(false); empty.unmount();
  });

  it('opens field work from the center scan key when the current page has no scanner', async () => {
    const mediaDevices = Object.getOwnPropertyDescriptor(navigator, 'mediaDevices');
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'mobile', 'label']));
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.get('.mobile-tab[data-mobile-tab="dashboard"]').classes()).toContain('active');
    await wrapper.get('[aria-label="扫码"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('手机作业');
    expect(wrapper.get('.mobile-tab[data-mobile-section="more"]').classes()).toContain('active');
    wrapper.unmount();
    if (mediaDevices) Object.defineProperty(navigator, 'mediaDevices', mediaDevices);
  });

  it('opens the permitted field workspace first on a phone', async () => {
    const originalWidth = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 375 });
    try {
      vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'production', 'mobile']));
      const wrapper = mount(App);
      await flushPromises();

      expect(wrapper.get('.mobile-tab[data-mobile-tab="dashboard"]').classes()).toContain('active');
      expect(wrapper.get('.mobile-brand strong').text()).toBe('首页');
      expect(wrapper.get('.mobile-tabbar').text()).toContain('订单');
      wrapper.unmount();
    } finally {
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth });
    }
  });

  it('filters sidebar navigation from the authenticated menu contract', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'production', 'mobile']));

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.findAll('.nav-button[data-tab]').map((button) => button.attributes('data-tab'))).toEqual([
      'dashboard',
      'production',
      'mobile',
    ]);
    expect(wrapper.find('.ops-nav').text()).toContain('生产');
    expect(wrapper.find('.ops-nav').text()).not.toContain('来料');
    expect(wrapper.find('.ops-nav').text()).not.toContain('配置');

    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, {
      detail: { session: sessionWithMenus(['dashboard', 'quality']) },
    }));
    await flushPromises();

    expect(wrapper.findAll('.nav-button[data-tab]').map((button) => button.attributes('data-tab'))).toEqual([
      'dashboard',
      'quality',
    ]);

    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, {
      detail: { session: null },
    }));
    await flushPromises();

    expect(wrapper.find('.login-card').text()).toContain('登录');
    expect(wrapper.findAll('.nav-button')).toHaveLength(0);
  });

  it('shows an icon on every module and can collapse the sidebar', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(allMenuCodes));
    const wrapper = mount(App);
    await flushPromises();
    const operatorMenus = allMenuCodes.filter(key => !['inbound', 'progress'].includes(key));

    expect(wrapper.findAll('.ops-nav .nav-button .nav-icon')).toHaveLength(operatorMenus.length + 1);
    expect(wrapper.get('.sidebar-toggle').attributes('aria-expanded')).toBe('true');
    expect(wrapper.get('.app-shell').classes()).not.toContain('is-collapsed');

    await wrapper.get('.sidebar-toggle').trigger('click');

    expect(wrapper.get('.app-shell').classes()).toContain('is-collapsed');
    expect(wrapper.get('.sidebar-toggle').attributes('aria-expanded')).toBe('false');
    expect(wrapper.get('.sidebar-toggle').attributes('aria-label')).toBe('展开侧边栏');
    expect(localStorage.getItem('zhubao-sidebar-collapsed')).toBe('1');
    expect(wrapper.findAll('.ops-nav .nav-button .nav-icon')).toHaveLength(operatorMenus.length + 1);

    await wrapper.get('.sidebar-toggle').trigger('click');

    expect(wrapper.get('.app-shell').classes()).not.toContain('is-collapsed');
    expect(localStorage.getItem('zhubao-sidebar-collapsed')).toBe('0');
  });

  it('hides finance from navigation when the session has no finance menu', async () => {
    const codes = allMenuCodes.filter((code) => code !== 'finance');
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(codes));
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('.ops-nav').text()).not.toContain('财务');
    expect(wrapper.findAll('.nav-group-label').map((label) => label.text())).not.toContain('财务');
    await wrapper.get('.mobile-tabbar button[aria-haspopup="dialog"]').trigger('click');
    expect(wrapper.get('[aria-label="更多"]').text()).not.toContain('财务');
    expect(wrapper.get('[aria-label="更多"]').text()).not.toContain('来料');
    await wrapper.get('.mobile-tab[data-mobile-section="orders"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('[aria-label="订单查询列表"]').exists()).toBe(true), { timeout: 5000 });
    expect(wrapper.get('[aria-label="订单查询列表"]').text()).toContain('来料');
  });

  it('opens an authorized module from the mobile more sheet without a second page state', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(allMenuCodes));
    const wrapper = mount(App);
    await flushPromises();

    await wrapper.get('[aria-label="追溯查询"]').setValue('MD-1');
    await wrapper.get('.mobile-tabbar button[aria-haspopup="dialog"]').trigger('click');
    await wrapper.get('.mobile-more-item[data-mobile-tab="finance"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.findAll('.finance-workspace')).toHaveLength(1), { timeout: 5000 });

    expect(wrapper.findAll('h1')).toHaveLength(1);
    expect(wrapper.get('h1').text()).toBe('经营总览');
    expect(wrapper.find('.page-location').exists()).toBe(false);
    expect(wrapper.find('.mobile-more-sheet').exists()).toBe(false);
    expect(wrapper.get('[aria-label="追溯查询"]').element).toHaveProperty('value', 'MD-1');
    expect(wrapper.findAll('.finance-workspace')).toHaveLength(1);
  });

  it('页面按需加载后可正常切回首页', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'finance']));
    const wrapper = mount(App);
    await flushPromises();
    await wrapper.get('.nav-button[data-tab="finance"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('.finance-workspace').exists()).toBe(true));
    await wrapper.get('.nav-button[data-tab="dashboard"]').trigger('click');
    expect(wrapper.find('.finance-workspace').exists()).toBe(false);
    expect(wrapper.text()).toContain('今天的工作');
  });

  it('页面文件加载失败时保留导航并提供中文重试入口', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'hardware']));
    const wrapper = mount(App, { global: { config: { errorHandler: () => {} } } });
    await flushPromises();
    await wrapper.get('.nav-button[data-tab="hardware"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.text()).toContain('页面加载失败，请检查网络后重试。'));
    expect(wrapper.get('[role="alert"]').text()).toContain('重新加载页面');
    await wrapper.get('.nav-button[data-tab="dashboard"]').trigger('click');
    expect(wrapper.text()).toContain('今天的工作');
  });

  it('加载状态用中文反馈并可重试失败页面', async () => {
    const retry = vi.fn();
    const wrapper = mount(WorkspaceLoadState, { global: { provide: { 'zhubao-retry-workspace': retry } } });
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载页面');
    await wrapper.setProps({ error: new Error('网络异常') });
    await wrapper.get('button').trigger('click');
    expect(retry).toHaveBeenCalledOnce();
  });

  it('shows the password change form outside the shell', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce({
      ...sessionWithMenus(['dashboard']),
      passwordChangeRequired: true,
    });
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('[aria-label="首次修改密码"]').exists()).toBe(true);
    expect(wrapper.find('.mobile-tabbar').exists()).toBe(false);
    expect(wrapper.find('.ops-nav').exists()).toBe(false);
  });

  it('身份失效事件清除会话并回到登录页', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(allMenuCodes));
    const wrapper = mount(App);
    await flushPromises();
    expect(wrapper.find('.login-card').exists()).toBe(false);

    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, { detail: { session: null } }));
    await flushPromises();

    expect(wrapper.find('.login-card').exists()).toBe(true);
    expect(wrapper.find('.ops-nav').exists()).toBe(false);
    wrapper.unmount();
  });

  it('审批栏目跟随服务端菜单，不再只限管理员', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce({ ...sessionWithMenus(['dashboard', 'approvals', 'mobile', 'label']), role: 'ADMIN' });
    const wrapper = mount(App);
    await flushPromises();
    expect(wrapper.find('[data-tab="approvals"]').exists()).toBe(true);
    await wrapper.get('[aria-haspopup="dialog"]').trigger('click');
    expect(wrapper.find('[data-mobile-tab="approvals"]').exists()).toBe(true);

    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, {
      detail: { session: { ...sessionWithMenus(['dashboard', 'approvals', 'mobile', 'label']), role: 'WORKSHOP_LEADER' } },
    }));
    await flushPromises();
    expect(wrapper.find('[data-tab="approvals"]').exists()).toBe(true);
    await wrapper.get('[aria-haspopup="dialog"]').trigger('click');
    expect(wrapper.find('[data-mobile-tab="approvals"]').exists()).toBe(true);

    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, {
      detail: { session: { ...sessionWithMenus(['dashboard', 'mobile', 'label']), role: 'WORKSHOP_LEADER' } },
    }));
    await flushPromises();
    expect(wrapper.find('[data-tab="approvals"]').exists()).toBe(false);
    await wrapper.get('[aria-haspopup="dialog"]').trigger('click');
    expect(wrapper.get('.mobile-more-sheet').text()).toContain('标签');
    expect(wrapper.get('.mobile-more-sheet').text()).not.toContain('审批');
    wrapper.unmount();
  });

  it('没有手机作业菜单但有订单读取菜单时，底栏扫码进入只读订单中心', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'inbound']));
    const wrapper = mount(App);
    await flushPromises();

    await wrapper.get('[aria-label="扫码"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('订单中心');
    expect(wrapper.find('.field-job').exists()).toBe(false);
    expect(wrapper.find('.mobile-tab[data-mobile-section="inventory"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('退出或会话失效后取消挂起扫码', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'mobile']));
    const wrapper = mount(App);
    await flushPromises();
    armMobileScan();

    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, { detail: { session: null } }));
    await flushPromises();

    const open = vi.fn();
    bindMobileScanTarget(open);
    expect(open).not.toHaveBeenCalled();
    unbindMobileScanTarget(open);
    wrapper.unmount();
  });

  it('用户管理导航仅向拥有菜单的管理员开放', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce({ ...sessionWithMenus(['dashboard', 'users']), role: 'ADMIN' });
    const wrapper = mount(App);
    await flushPromises();
    expect(wrapper.find('[data-tab="users"]').exists()).toBe(true);
    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, {
      detail: { session: { ...sessionWithMenus(['dashboard', 'users']), role: 'GENERAL_MANAGER' } },
    }));
    await flushPromises();
    expect(wrapper.find('[data-tab="users"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('顶栏查单只打开订单详情，不同时打开追溯回看；权限变化清掉旧单', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'production']));
    const wrapper = mount(App);
    await flushPromises();
    await wrapper.get('[data-mobile-section="orders"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('.order-workspace').exists()).toBe(true), { timeout: 5000 });
    await wrapper.get('[aria-label="追溯查询"]').setValue('MO-ONLY');
    await wrapper.get('[aria-label="打开追溯单据"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('[aria-label="订单详情"] h2').text()).toContain('MO-ONLY'));
    expect(queryScanContext).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[aria-label="追溯回看"]').exists()).toBe(false);
    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, { detail: { session: sessionWithMenus(['dashboard']) } }));
    await flushPromises();
    expect(wrapper.find('[aria-label="订单详情"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="追溯回看"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('MO-ONLY');
    expect(wrapper.find('[data-workspace-section="orders"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('底栏优先使用当前表单扫码，有订单菜单也不跳走', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'production']));
    const wrapper = mount(App);
    await flushPromises();
    const scan = vi.fn();
    bindMobileScanTarget(scan);
    const beforeScan = vi.mocked(fetchOrderWorkspace).mock.calls.length;
    await wrapper.get('[aria-label="扫码"]').trigger('click');
    expect(scan).toHaveBeenCalledOnce();
    expect(wrapper.get('h1').text()).toBe('首页');
    expect(fetchOrderWorkspace).toHaveBeenCalledTimes(beforeScan);
    unbindMobileScanTarget(scan);
    wrapper.unmount();
  });

  it('只有财务菜单时不因新展示分区获得订单入口', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'finance']));
    const wrapper = mount(App);
    await flushPromises();
    expect(wrapper.find('[data-workspace-section="orders"]').exists()).toBe(false);
    expect(wrapper.find('[data-mobile-section="orders"]').exists()).toBe(false);
    expect(fetchOrderWorkspace).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('从全局单据详情重新扫码时清掉原 LOOKUP，挂起扫码不重新打开旧单', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'production']));
    const wrapper = mount(App);
    await flushPromises();
    await wrapper.get('[data-mobile-section="orders"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('.order-workspace').exists()).toBe(true), { timeout: 5000 });
    await wrapper.get('[aria-label="追溯查询"]').setValue('MO-SCAN');
    await wrapper.get('[aria-label="打开追溯单据"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('[aria-label="订单详情"]').exists()).toBe(true), { timeout: 5000 });
    await wrapper.get('[aria-label="扫码"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('[aria-label="订单扫码单号"]').exists()).toBe(true), { timeout: 5000 });
    expect(queryScanContext).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[aria-label="订单详情"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('其他页面的顶栏查单保留原组件和草稿，只在当前页复用只读详情', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'production']));
    const wrapper = mount(App, { global: { stubs: { M1Dashboard: { template: '<input aria-label="原页面草稿" />' } } } });
    await flushPromises();
    const original = wrapper.get('[aria-label="原页面草稿"]').element;
    await wrapper.get('[aria-label="原页面草稿"]').setValue('尚未提交');
    await wrapper.get('[aria-label="追溯查询"]').setValue('MO-IN-PLACE');
    await wrapper.get('[aria-label="打开追溯单据"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[aria-label="原页面草稿"]').element).toBe(original);
    expect(original).toHaveProperty('value', '尚未提交');
    expect(wrapper.findAll('.order-detail-content')).toHaveLength(1);
    expect(wrapper.find('.order-workspace').exists()).toBe(false);
    expect(fetchOrderWorkspace).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('有效收货权限打开原页之上的动作容器，收起恢复底页交互，权限变化清掉本账号动作', async () => {
    const receiver = { ...sessionWithMenus(['dashboard', 'inbound']), role: 'RECEIVER', effectivePermissions: ['BUSINESS_INBOUND_CONFIRM'] };
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(receiver);
    const wrapper = mount(App);
    await flushPromises();
    expect(wrapper.getComponent(OrderActionHost).props('canIntake')).toBe(true);
    await wrapper.findAll('button').find(button => button.text() === '新建收货／母单')!.trigger('click');
    expect(wrapper.get('.app-shell').attributes('inert')).toBeDefined();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();
    expect(wrapper.get('.app-shell').attributes('inert')).toBeUndefined();
    window.dispatchEvent(new CustomEvent(authSessionChangedEvent, { detail: { session: { ...receiver, effectivePermissions: [] } } }));
    await flushPromises();
    expect(wrapper.getComponent(OrderActionHost).props('canIntake')).toBe(false);
    expect(wrapper.findAll('button').some(button => button.text() === '新建收货／母单')).toBe(false);
    wrapper.unmount();
  });
});

const allMenuCodes = [
  'dashboard',
  'inbound',
  'customers',
  'production',
  'progress',
  'quality',
  'inventory',
  'finance',
  'approvals',
  'label',
  'sample',
  'documents',
  'hardware',
  'configuration',
  'operations',
  'mobile',
];

function sessionWithMenus(menuCodes: string[]): AuthSession {
  return {
    token: 'token-demo',
    username: 'operator',
    operator: '操作工 王强',
    role: 'OPERATOR',
    roleLabel: '操作工',
    dataScope: 'SELF',
    dataScopeLabel: '本人任务',
    expiresAt: '2026-07-08 20:00:00',
    demoAccount: true,
    passwordChangedAt: '2026-07-08 09:00:00',
    passwordExpiresAt: '2026-10-06 09:00:00',
    passwordExpired: false,
    passwordChangeRequired: false,
    customerScope: [],
    menuCodes,
    menuTree: menuCodes.map((code) => ({
      code,
      label: code,
      module: code,
      route: `/${code}`,
      buttonCodes: [],
      children: [],
    })),
  };
}
