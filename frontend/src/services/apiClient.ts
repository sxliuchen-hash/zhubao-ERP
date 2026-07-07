export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

const authTokenKey = 'zhubao-auth-token';
const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').trim();
const apiEnabled = import.meta.env.MODE === 'test' || Boolean(baseUrl) || import.meta.env.VITE_ENABLE_API === 'true';

function assertApiEnabled() {
  if (!apiEnabled) {
    throw new Error('API 未配置，当前使用本地评审样例');
  }
}

function storage(): Storage | null {
  return typeof globalThis.localStorage === 'undefined' ? null : globalThis.localStorage;
}

export function getAuthToken(): string {
  return storage()?.getItem(authTokenKey) ?? '';
}

export function setAuthToken(token: string): void {
  if (token) {
    storage()?.setItem(authTokenKey, token);
  }
}

export function clearAuthToken(): void {
  storage()?.removeItem(authTokenKey);
}

function authHeaders(): Record<string, string> {
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function apiGet<TResponse>(path: string): Promise<TResponse> {
  assertApiEnabled();
  const headers = authHeaders();
  const url = `${baseUrl}${path}`;
  const response = Object.keys(headers).length ? await fetch(url, { headers }) : await fetch(url);
  const payload = (await response.json()) as ApiResponse<TResponse>;
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '请求失败');
  }
  return payload.data;
}

export async function apiPost<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
  assertApiEnabled();
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
    },
    body: JSON.stringify(body),
  });
  const payload = (await response.json()) as ApiResponse<TResponse>;
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '请求失败');
  }
  return payload.data;
}

export async function apiPostForm<TResponse>(path: string, body: FormData): Promise<TResponse> {
  assertApiEnabled();
  const headers = authHeaders();
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    ...(Object.keys(headers).length ? { headers } : {}),
    body,
  });
  const payload = (await response.json()) as ApiResponse<TResponse>;
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '请求失败');
  }
  return payload.data;
}
