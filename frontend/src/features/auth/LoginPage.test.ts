import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage.vue';
import { fetchAuthDemoUsers } from '../../services/authApi';

vi.mock('../../services/authApi', () => ({
  fetchAuthDemoUsers: vi.fn(),
  loginAuthSession: vi.fn(),
  changeOwnPassword: vi.fn(),
}));

describe('登录页演示账号提示', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('没有启用的演示账号时隐藏默认密码提示并保留手填密码', async () => {
    vi.mocked(fetchAuthDemoUsers).mockResolvedValue([]);
    const wrapper = mount(LoginPage, { props: { session: null } });
    await flushPromises();

    expect(wrapper.text()).not.toContain('密码是账号名加 123');
    expect(wrapper.find('datalist').exists()).toBe(false);
    const username = wrapper.get('input[autocomplete="username"]');
    const password = wrapper.get('input[autocomplete="current-password"]');
    expect(username.attributes('list')).toBeUndefined();
    await password.setValue('Trial-Password-2026');
    await username.setValue('trial-admin');
    expect(password.element).toHaveProperty('value', 'Trial-Password-2026');
  });

  it('只为服务端返回的演示账号填充演示密码', async () => {
    vi.mocked(fetchAuthDemoUsers).mockResolvedValue(['leader']);
    const wrapper = mount(LoginPage, { props: { session: null } });
    await flushPromises();

    expect(wrapper.text()).toContain('密码是账号名加 123');
    expect(wrapper.findAll('datalist option').map((option) => option.attributes('value'))).toEqual(['leader']);
    const username = wrapper.get('input[autocomplete="username"]');
    const password = wrapper.get('input[autocomplete="current-password"]');
    await username.setValue('leader');
    expect(password.element).toHaveProperty('value', 'leader123');
    await password.setValue('Trial-Password-2026');
    await username.setValue('trial-admin');
    expect(password.element).toHaveProperty('value', 'Trial-Password-2026');
  });

  it('演示账号查询失败时隐藏演示提示', async () => {
    vi.mocked(fetchAuthDemoUsers).mockRejectedValue(new Error('网络不可用'));
    const wrapper = mount(LoginPage, { props: { session: null } });
    await flushPromises();

    expect(wrapper.text()).not.toContain('密码是账号名加 123');
    expect(wrapper.find('datalist').exists()).toBe(false);
  });
});
