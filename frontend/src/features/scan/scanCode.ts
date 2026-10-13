const queryWaybill = /(?:waybillNo|mailNo|billNo|expressNo|logisticCode|waybill)=([A-Za-z0-9-]{8,32})/i;

/** 从面单文本或链接中取出快递单号。系统标签由服务端扫码上下文识别。 */
export function extractCourierNumber(raw: string): string {
  const trimmed = raw.trim();
  const lower = trimmed.toLowerCase();
  if (lower.startsWith('http://') || lower.startsWith('https://')) {
    const matched = queryWaybill.exec(trimmed);
    if (matched && isWaybill(normalizeCourier(matched[1]))) {
      return normalizeCourier(matched[1]);
    }
    const tail = trimmed.slice(trimmed.lastIndexOf('/') + 1).split('?')[0];
    const normalizedTail = normalizeCourier(tail);
    return isWaybill(normalizedTail) ? normalizedTail : '';
  }
  const normalized = normalizeCourier(trimmed);
  return isWaybill(normalized) ? normalized : '';
}

function normalizeCourier(raw: string): string {
  return raw.trim().replace(/\s+/g, '').toUpperCase();
}

function isWaybill(normalized: string): boolean {
  if (normalized.length < 8 || normalized.length > 32) {
    return false;
  }
  if (!/^[A-Z0-9-]+$/.test(normalized) || normalized.startsWith('-')) {
    return false;
  }
  return /\d/.test(normalized);
}
