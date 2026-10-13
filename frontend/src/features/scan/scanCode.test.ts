import { describe, expect, it } from 'vitest';
import { extractCourierNumber } from './scanCode';

describe('extractCourierNumber', () => {
  it('reads a courier number from plain text or a waybill url', () => {
    expect(extractCourierNumber('sf 1048329001')).toBe('SF1048329001');
    expect(extractCourierNumber('https://example.com/sf?mailNo=YT1234567890')).toBe('YT1234567890');
  });

  it('does not treat a system label or plain words as a waybill', () => {
    expect(extractCourierNumber('JG|work_order|CC-20260706-001-JL-ZD001')).toBe('');
    expect(extractCourierNumber('bad')).toBe('');
    expect(extractCourierNumber('HELLOWORLD')).toBe('');
  });
});
