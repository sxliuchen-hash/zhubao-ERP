/**
 * 2026-10-14 从 frontend/src/features/scan/scanCode.ts 移出。
 * 页面查单已改走服务端扫码上下文，不再调用 parseScanCode。
 * 现行文件仍保留 extractCourierNumber，恢复时不要整文件覆盖。
 */

export type ScanObjectType = 'MOTHER_ORDER' | 'WORK_ORDER' | 'OUTBOUND_ORDER' | 'REWORK' | 'EXPRESS_NO';

export interface ParsedScanCode {
  objectType: ScanObjectType;
  code: string;
  nextAction: string;
}

const typeMap: Record<string, ScanObjectType> = {
  mother: 'MOTHER_ORDER',
  MO: 'MOTHER_ORDER',
  work_order: 'WORK_ORDER',
  WO: 'WORK_ORDER',
  outbound: 'OUTBOUND_ORDER',
  OUT: 'OUTBOUND_ORDER',
  rework: 'REWORK',
  EXP: 'EXPRESS_NO',
};

const queryWaybill = /(?:waybillNo|mailNo|billNo|expressNo|logisticCode|waybill)=([A-Za-z0-9-]{8,32})/i;

/** 只保留与旧 /api/scan/resolve 一致的格式解析。页面查单不再调用这里。 */
export function parseScanCode(content: string, scene = 'lookup'): ParsedScanCode {
  const normalized = content.trim();
  if (normalized.startsWith('JG|')) {
    return parseSystemCode(normalized, scene);
  }
  const waybill = extractCourierNumber(normalized);
  if (!waybill) {
    throw new Error('未识别的二维码内容');
  }
  return {
    objectType: 'EXPRESS_NO',
    code: waybill,
    nextAction: 'intake',
  };
}

function parseSystemCode(content: string, scene: string): ParsedScanCode {
  const parts = content.split('|');
  if (parts.length !== 3 || parts[0] !== 'JG') {
    throw new Error('未识别的二维码内容');
  }
  const objectType = typeMap[parts[1]];
  if (!objectType || !parts[2]) {
    throw new Error('未识别的二维码类型');
  }
  const code = objectType === 'EXPRESS_NO' ? normalizeCourier(parts[2]) : parts[2].trim();
  if (!code) {
    throw new Error('未识别的二维码类型');
  }
  return {
    objectType,
    code,
    nextAction: nextAction(objectType, scene),
  };
}

function nextAction(objectType: ScanObjectType, scene: string): string {
  if (objectType === 'WORK_ORDER' && scene === 'pwa_report') {
    return 'report';
  }
  if (objectType === 'MOTHER_ORDER') {
    return 'trace';
  }
  if (objectType === 'OUTBOUND_ORDER') {
    return 'outbound';
  }
  if (objectType === 'EXPRESS_NO') {
    return 'intake';
  }
  return 'lookup';
}

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
