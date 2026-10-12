import { ApiRequestError } from '../../services/apiClient';

/** 失败时只更换提示。调用方必须留下已经填写的内容。 */
export function keptInputFailure(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError && error.status === 401) {
    return '登录已过期。已填内容还在，请重新登录后再提交。';
  }
  if (error instanceof Error && error.message.trim()) return error.message;
  return fallback;
}

/** 按换行拆开已有要求，只用于页面核对，不产生新的业务规则。 */
export function requirementLines(text: string): string[] {
  return text.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
}
