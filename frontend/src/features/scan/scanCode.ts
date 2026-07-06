export type ScanObjectType = 'MOTHER_ORDER' | 'WORK_ORDER' | 'OUTBOUND_ORDER' | 'EXPRESS_NO';

export interface ParsedScanCode {
  objectType: ScanObjectType;
  code: string;
  nextAction: string;
}

const typeMap: Record<string, ScanObjectType> = {
  MO: 'MOTHER_ORDER',
  WO: 'WORK_ORDER',
  OUT: 'OUTBOUND_ORDER',
  EXP: 'EXPRESS_NO',
};

export function parseScanCode(content: string, scene = 'lookup'): ParsedScanCode {
  const parts = content.trim().split('|');
  if (parts.length !== 3 || parts[0] !== 'JG') {
    throw new Error('未识别的二维码内容');
  }

  const objectType = typeMap[parts[1]];
  if (!objectType || !parts[2]) {
    throw new Error('未识别的二维码类型');
  }

  return {
    objectType,
    code: parts[2],
    nextAction: objectType === 'WORK_ORDER' && scene === 'pwa_report' ? 'report' : 'lookup',
  };
}
