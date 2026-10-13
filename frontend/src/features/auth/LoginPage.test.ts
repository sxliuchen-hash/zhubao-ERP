import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage.vue';
import { fetchAuthDemoUsers, loginAuthSession } from '../../services/authApi';

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

describe('登录表单校验', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchAuthDemoUsers).mockResolvedValue([]);
  });

  it.each([
    ['', 'Trial-Password-2026', '请输入账号'],
    ['   ', 'Trial-Password-2026', '请输入账号'],
    ['admin', '', '请输入密码'],
    ['admin', '   ', '请输入密码'],
  ])('拦截空白账号或密码（%s）', async (username, password, message) => {
    const wrapper = mount(LoginPage, { props: { session: null } });
    await flushPromises();
    await wrapper.get('input[autocomplete="username"]').setValue(username);
    await wrapper.get('input[autocomplete="current-password"]').setValue(password);
    await wrapper.get('form').trigger('submit');

    expect(wrapper.get('[role="alert"]').text()).toBe(message);
    expect(loginAuthSession).not.toHaveBeenCalled();
    expect(wrapper.get('button').attributes('disabled')).toBeUndefined();
  });

  it('修正输入后可重试，账号去除首尾空格且密码原样提交', async () => {
    vi.mocked(loginAuthSession).mockRejectedValue(new Error('账号或密码错误'));
    const wrapper = mount(LoginPage, { props: { session: null } });
    await flushPromises();
    await wrapper.get('form').trigger('submit');
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入账号');

    await wrapper.get('input[autocomplete="username"]').setValue(' admin ');
    await wrapper.get('input[autocomplete="current-password"]').setValue(' Trial-Password-2026 ');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(loginAuthSession).toHaveBeenCalledWith({ username: 'admin', password: ' Trial-Password-2026 ' });
    expect(wrapper.get('[role="alert"]').text()).toBe('账号或密码错误');
    expect(wrapper.get('input[autocomplete="current-password"]').element).toHaveProperty('value', ' Trial-Password-2026 ');
    expect(wrapper.get('button').attributes('disabled')).toBeUndefined();
  });
});
