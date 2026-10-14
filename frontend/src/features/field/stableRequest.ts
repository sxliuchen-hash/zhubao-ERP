/** 同一次业务提交在失败或响应丢失后复用的请求编号。 */
export interface RequestAttempt {
  fingerprint: string;
  requestId: string;
}

/**
 * 内容没变就沿用原编号，方便服务端把重试认成同一次提交。
 * 内容变了才生成新编号。编号字符和长度按服务端命令契约收紧。
 */
export function nextRequestAttempt(current: RequestAttempt | null, fingerprint: string, prefix: string): RequestAttempt {
  if (current && current.fingerprint === fingerprint) return current;
  const random = globalThis.crypto?.randomUUID?.() ?? String(Date.now());
  const requestId = `${prefix}-${random}`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64);
  return { fingerprint, requestId: requestId.length >= 8 ? requestId : `${prefix}-request`.replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 64) };
}
