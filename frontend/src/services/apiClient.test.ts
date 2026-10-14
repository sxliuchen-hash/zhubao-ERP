import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiGet, getAuthToken, setAuthToken } from './apiClient';

function jsonResponse(status: number, body: unknown): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    json: async () => body,
  } as Response;
}

describe('apiClient 身份失效', () => {
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('会话失效的 403 清掉令牌并通知回到登录页', async () => {
    setAuthToken('audit-expired-session');
    const events: Array<CustomEvent<{ session: unknown }>> = [];
    globalThis.addEventListener('zhubao-auth-session-changed', ((event: Event) => {
      events.push(event as CustomEvent<{ session: unknown }>);
    }) as EventListener);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(403, {
      code: 40301,
      message: '登录已失效，请重新登录',
      data: null,
    })));

    await expect(apiGet('/api/business/operations/progress')).rejects.toThrow('登录已失效，请重新登录');
    expect(getAuthToken()).toBe('');
    expect(events).toHaveLength(1);
    expect(events[0].detail.session).toBeNull();
  });

  it('普通业务无权限保留登录', async () => {
    setAuthToken('still-logged-in');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(403, {
      code: 40301,
      message: '无权执行 损耗审批试算',
      data: null,
    })));

    await expect(apiGet('/api/finance/statements')).rejects.toThrow('无权执行');
    expect(getAuthToken()).toBe('still-logged-in');
  });

  it('非 JSON 错误响应不清除令牌', async () => {
    setAuthToken('still-logged-in');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      status: 403,
      ok: false,
      json: async () => {
        throw new SyntaxError('not json');
      },
    }));

    await expect(apiGet('/api/business/operations/progress')).rejects.toThrow('后端接口不可用');
    expect(getAuthToken()).toBe('still-logged-in');
  });

  it('HTTP 401 仍然清除令牌', async () => {
    setAuthToken('expired-401');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(401, {
      code: 401,
      message: '未登录',
      data: null,
    })));

    await expect(apiGet('/api/auth/session')).rejects.toThrow('未登录');
    expect(getAuthToken()).toBe('');
  });
});
