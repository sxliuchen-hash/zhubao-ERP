export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').trim();
const apiEnabled = import.meta.env.MODE === 'test' || Boolean(baseUrl) || import.meta.env.VITE_ENABLE_API === 'true';

function assertApiEnabled() {
  if (!apiEnabled) {
    throw new Error('API 未配置，当前使用本地评审样例');
  }
}

export async function apiGet<TResponse>(path: string): Promise<TResponse> {
  assertApiEnabled();
  const response = await fetch(`${baseUrl}${path}`);
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
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    body,
  });
  const payload = (await response.json()) as ApiResponse<TResponse>;
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '请求失败');
  }
  return payload.data;
}
