export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

/** 保留接口状态码，便于页面区分无权限和其它失败。 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: number;

  constructor(message: string, status: number, code: number) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
  }
}

const authTokenKey = 'zhubao-auth-token';
const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').trim();

async function request(url: string, init?: RequestInit): Promise<Response> {
  try {
    return init ? await fetch(url, init) : await fetch(url);
  } catch {
    throw new Error('后端接口不可用');
  }
}

async function readPayload<T>(response: Response): Promise<T> {
  let payload: ApiResponse<T>;
  try {
    payload = (await response.json()) as ApiResponse<T>;
  } catch {
    throw new Error('后端接口不可用');
  }
  if (response.status === 401) {
    clearAuthToken();
    if (typeof globalThis.dispatchEvent === 'function' && typeof globalThis.CustomEvent === 'function') {
      globalThis.dispatchEvent(new CustomEvent('zhubao-auth-session-changed', { detail: { session: null } }));
    }
  }
  if (!response.ok || payload.code !== 0) {
    throw new ApiRequestError(payload.message || '请求失败', response.status, payload.code ?? 0);
  }
  return payload.data;
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
  const headers = authHeaders();
  const url = `${baseUrl}${path}`;
  const response = Object.keys(headers).length ? await request(url, { headers }) : await request(url);
  return readPayload<TResponse>(response);
}

export async function apiPost<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
  const response = await request(`${baseUrl}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
    },
    body: JSON.stringify(body),
  });
  return readPayload<TResponse>(response);
}

export async function apiPostForm<TResponse>(path: string, body: FormData): Promise<TResponse> {
  const headers = authHeaders();
  const response = await request(`${baseUrl}${path}`, {
    method: 'POST',
    ...(Object.keys(headers).length ? { headers } : {}),
    body,
  });
  return readPayload<TResponse>(response);
}
