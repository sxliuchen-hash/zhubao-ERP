import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { authSessionChangedEvent, fetchAuthSession, type AuthSession } from './services/authApi';

vi.mock('./services/authApi', () => ({
  authSessionChangedEvent: 'zhubao-auth-session-changed',
  fetchAuthSession: vi.fn(),
  fetchAuthDemoUsers: vi.fn().mockResolvedValue(['admin']),
  loginAuthSession: vi.fn(),
  logoutAuthSession: vi.fn().mockResolvedValue(undefined),
  changeOwnPassword: vi.fn(),
}));

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(fetchAuthSession).mockResolvedValue(null);
  });

  it('shows the login page before a session exists', async () => {
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.find('.login-card').text()).toContain('登录');
    expect(wrapper.find('.ops-nav').exists()).toBe(false);
  });

  it('starts on the M1 business workbench and exposes core modules', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(allMenuCodes));
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('来料加工');
    expect(wrapper.findAll('.nav-group-label').map((label) => label.text())).toEqual([
      '工作台',
      '来料与订单',
      '生产与质量',
      '库存与交付',
      '财务经营',
      '协同与系统',
    ]);
    expect(wrapper.text()).toContain('今日待办');
    expect(wrapper.text()).toContain('当前处理单');
    expect(wrapper.find('.ops-scope-ribbon').exists()).toBe(false);
    expect(wrapper.find('.ops-nav').text()).toContain('首页');
    expect(wrapper.find('.ops-nav').text()).toContain('来料');
    expect(wrapper.find('.ops-nav').text()).toContain('生产');
    expect(wrapper.find('.ops-nav').text()).toContain('质检');
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
    expect(wrapper.get('.mobile-tabbar').text()).toContain('工作台');
    expect(wrapper.get('.mobile-tabbar').text()).toContain('现场作业');
    expect(wrapper.get('.mobile-tabbar').text()).toContain('审批');
    expect(wrapper.get('.mobile-tabbar').text()).toContain('更多');
    expect(wrapper.get('.mobile-tabbar').text()).not.toContain('消息');
    expect(wrapper.get('.mobile-tabbar').text()).not.toContain('扫码');
  });

  it('filters sidebar navigation from the authenticated menu contract', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(['dashboard', 'production', 'mobile']));

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.findAll('.nav-button').map((button) => button.attributes('data-tab'))).toEqual([
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

    expect(wrapper.findAll('.nav-button').map((button) => button.attributes('data-tab'))).toEqual([
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

    expect(wrapper.findAll('.ops-nav .nav-button .nav-icon')).toHaveLength(15);
    expect(wrapper.get('.sidebar-toggle').attributes('aria-expanded')).toBe('true');
    expect(wrapper.get('.app-shell').classes()).not.toContain('is-collapsed');

    await wrapper.get('.sidebar-toggle').trigger('click');

    expect(wrapper.get('.app-shell').classes()).toContain('is-collapsed');
    expect(wrapper.get('.sidebar-toggle').attributes('aria-expanded')).toBe('false');
    expect(wrapper.get('.sidebar-toggle').attributes('aria-label')).toBe('展开侧边栏');
    expect(localStorage.getItem('zhubao-sidebar-collapsed')).toBe('1');
    expect(wrapper.findAll('.ops-nav .nav-button .nav-icon')).toHaveLength(15);

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
    expect(wrapper.findAll('.nav-group-label').map((label) => label.text())).not.toContain('财务经营');
    await wrapper.get('.mobile-tabbar button[aria-haspopup="dialog"]').trigger('click');
    expect(wrapper.get('.mobile-more-sheet').text()).not.toContain('财务');
    expect(wrapper.get('.mobile-more-sheet').text()).toContain('来料');
  });

  it('opens an authorized module from the mobile more sheet without a second page state', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValueOnce(sessionWithMenus(allMenuCodes));
    const wrapper = mount(App);
    await flushPromises();

    await wrapper.get('[aria-label="追溯查询"]').setValue('MD-1');
    await wrapper.get('.mobile-tabbar button[aria-haspopup="dialog"]').trigger('click');
    await wrapper.get('.mobile-more-item[data-mobile-tab="finance"]').trigger('click');

    expect(wrapper.get('h1').text()).toBe('财务');
    expect(wrapper.get('.page-location').text()).toBe('财务经营');
    expect(wrapper.find('.mobile-more-sheet').exists()).toBe(false);
    expect(wrapper.get('[aria-label="追溯查询"]').element).toHaveProperty('value', 'MD-1');
    expect(wrapper.findAll('.finance-workspace')).toHaveLength(1);
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
});

const allMenuCodes = [
  'dashboard',
  'inbound',
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
