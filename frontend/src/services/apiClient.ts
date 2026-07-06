export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export async function apiGet<TResponse>(path: string): Promise<TResponse> {
  const response = await fetch(`${baseUrl}${path}`);
  const payload = (await response.json()) as ApiResponse<TResponse>;
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '请求失败');
  }
  return payload.data;
}

export async function apiPost<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
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
