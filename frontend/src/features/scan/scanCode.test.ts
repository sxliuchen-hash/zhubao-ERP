import { describe, expect, it } from 'vitest';
import { parseScanCode } from './scanCode';

describe('parseScanCode', () => {
  it('parses work order scan content', () => {
    const parsed = parseScanCode('JG|WO|CC-20260706-001-JL-ZD001', 'pwa_report');

    expect(parsed).toEqual({
      objectType: 'WORK_ORDER',
      code: 'CC-20260706-001-JL-ZD001',
      nextAction: 'report',
    });
  });

  it('rejects unknown content', () => {
    expect(() => parseScanCode('bad')).toThrow('未识别的二维码内容');
  });
});
