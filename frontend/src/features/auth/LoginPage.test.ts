import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage.vue';
import { loginAuthSession } from '../../services/authApi';

vi.mock('../../services/authApi', () => ({
  loginAuthSession: vi.fn(),
  changeOwnPassword: vi.fn(),
}));

describe('登录页账号输入', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('不显示默认账号或密码规律，也不自动填入密码', async () => {
    const wrapper = mount(LoginPage, { props: { session: null } });

    expect(wrapper.text()).not.toContain('账号名加 123');
    expect(wrapper.text()).not.toContain('演示环境');
    expect(wrapper.find('datalist').exists()).toBe(false);
    const username = wrapper.get('input[autocomplete="username"]');
    const password = wrapper.get('input[autocomplete="current-password"]');
    expect(username.attributes('list')).toBeUndefined();
    await username.setValue('leader');
    await username.trigger('change');
    expect(password.element).toHaveProperty('value', '');
    await password.setValue('Trial-Password-2026');
    await username.setValue('trial-admin');
    await username.trigger('change');
    expect(password.element).toHaveProperty('value', 'Trial-Password-2026');
  });
});

describe('登录表单校验', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
