import { describe, expect, it } from 'vitest';
import { parseScanCode } from './scanCode';

describe('parseScanCode', () => {
  it('parses a label qr as a work order', () => {
    expect(parseScanCode('JG|work_order|CC-20260706-001-JL-ZD001', 'pwa_report')).toEqual({
      objectType: 'WORK_ORDER',
      code: 'CC-20260706-001-JL-ZD001',
      nextAction: 'report',
    });
  });

  it('parses a mother label and keeps the legacy type token', () => {
    expect(parseScanCode('JG|mother|MD-20261008-001').objectType).toBe('MOTHER_ORDER');
    expect(parseScanCode('JG|WO|CC-OLD-001', 'pwa_report').nextAction).toBe('report');
  });

  it('reads a courier number from plain text or a waybill url', () => {
    expect(parseScanCode('sf 1048329001')).toEqual({
      objectType: 'EXPRESS_NO',
      code: 'SF1048329001',
      nextAction: 'intake',
    });
    expect(parseScanCode('https://example.com/sf?mailNo=YT1234567890').code).toBe('YT1234567890');
  });

  it('rejects unknown content', () => {
    expect(() => parseScanCode('bad')).toThrow('未识别的二维码内容');
    expect(() => parseScanCode('HELLOWORLD')).toThrow('未识别的二维码内容');
  });
});
