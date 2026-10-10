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
    expect(wrapper.find('.nav-group-label').text()).toContain('作业');
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

    expect(wrapper.findAll('.nav-button .nav-icon')).toHaveLength(15);
    expect(wrapper.get('.sidebar-toggle').attributes('aria-expanded')).toBe('true');
    expect(wrapper.get('.app-shell').classes()).not.toContain('is-collapsed');

    await wrapper.get('.sidebar-toggle').trigger('click');

    expect(wrapper.get('.app-shell').classes()).toContain('is-collapsed');
    expect(wrapper.get('.sidebar-toggle').attributes('aria-expanded')).toBe('false');
    expect(wrapper.get('.sidebar-toggle').attributes('aria-label')).toBe('展开侧边栏');
    expect(localStorage.getItem('zhubao-sidebar-collapsed')).toBe('1');
    expect(wrapper.findAll('.nav-button .nav-icon')).toHaveLength(15);

    await wrapper.get('.sidebar-toggle').trigger('click');

    expect(wrapper.get('.app-shell').classes()).not.toContain('is-collapsed');
    expect(localStorage.getItem('zhubao-sidebar-collapsed')).toBe('0');
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
