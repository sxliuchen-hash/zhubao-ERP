import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { authSessionChangedEvent, fetchAuthSession, type AuthSession } from './services/authApi';

vi.mock('./services/authApi', () => ({
  authSessionChangedEvent: 'zhubao-auth-session-changed',
  fetchAuthSession: vi.fn(),
}));

describe('App', () => {
  beforeEach(() => {
    vi.mocked(fetchAuthSession).mockResolvedValue(null);
  });

  it('starts on the M1 business workbench and exposes core modules', async () => {
    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('M3.97 ERP/MES OPERATIONS DESK');
    expect(wrapper.text()).toContain('M3.97 / 生产账号审批摘要');
    expect(wrapper.text()).toContain('复核补录');
    expect(wrapper.text()).toContain('登录审计');
    expect(wrapper.text()).toContain('授权导航过滤');
    expect(wrapper.text()).toContain('账号审批摘要');
    expect(wrapper.text()).toContain('账号密码哈希');
    expect(wrapper.text()).toContain('失败登录锁定');
    expect(wrapper.text()).toContain('账号启停解锁');
    expect(wrapper.text()).toContain('密码策略校验');
    expect(wrapper.text()).toContain('自助改密续期');
    expect(wrapper.text()).toContain('审计摘要链');
    expect(wrapper.text()).toContain('硬件 POC 仓储');
    expect(wrapper.text()).toContain('POC 证据关联');
    expect(wrapper.text()).toContain('数据范围过滤');
    expect(wrapper.find('.ops-command-metrics').exists()).toBe(true);
    expect(wrapper.findAll('.ops-command-metrics article')).toHaveLength(3);
    expect(wrapper.text()).toContain('首页工作台');
    expect(wrapper.text()).toContain('来料母单');
    expect(wrapper.text()).toContain('生产工单');
    expect(wrapper.text()).toContain('质检返工');
    expect(wrapper.text()).toContain('库存追溯');
    expect(wrapper.text()).toContain('审批工作台');
    expect(wrapper.text()).toContain('配置基线');
    expect(wrapper.text()).toContain('文档附件');
    expect(wrapper.text()).toContain('样例库');
    expect(wrapper.text()).toContain('软硬件联动');
    expect(wrapper.text()).toContain('运维中心');
    expect(wrapper.text()).toContain('PWA 作业');
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
    expect(wrapper.find('.ops-nav').text()).toContain('生产工单');
    expect(wrapper.find('.ops-nav').text()).not.toContain('来料母单');
    expect(wrapper.find('.ops-nav').text()).not.toContain('配置基线');

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

    expect(wrapper.findAll('.nav-button')).toHaveLength(12);
  });
});

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
