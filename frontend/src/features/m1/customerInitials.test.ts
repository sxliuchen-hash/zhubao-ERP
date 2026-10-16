import { describe, expect, it } from 'vitest';
import type { LabelPayload } from '../../services/labelApi';
import { applyCustomerMark, customerInitials } from './customerInitials';

const payload: LabelPayload = {
  type: 'MOTHER',
  typeLabel: '母单',
  primaryCode: 'MD-1',
  qrContent: 'JG|mother|MD-1',
  fields: [
    { name: '客户', value: '恒信珠宝' },
    { name: '期限', value: '2026-10-20' },
  ],
  footerLeft: '',
  footerRight: '',
  printedAt: '',
  brandTitle: '珠宝来料加工系统',
  templateCode: 'MOTHER-DEFAULT',
  printerSetting: { widthMm: 40, heightMm: 30, dpi: 300, printerName: '', copies: 1 },
};

describe('客户暗记', () => {
  it('取汉字拼音首字母，常见多音字按词语读音', () => {
    expect(customerInitials('恒信珠宝')).toBe('HXZB');
    expect(customerInitials('重庆珠宝')).toBe('CQZB');
    expect(customerInitials('银行')).toBe('YH');
    expect(customerInitials('长春')).toBe('CC');
    expect(customerInitials('厦门')).toBe('XM');
    expect(customerInitials('Tom珠宝')).toBe('TZB');
    expect(customerInitials('  ')).toBe('');
  });

  it('选中后页脚只印暗记，并去掉客户中文名', () => {
    const marked = applyCustomerMark(payload, 'HXZB');
    expect(marked.footerRight).toBe('HXZB');
    expect(marked.fields.map((field) => field.name)).toEqual(['期限']);
    expect(marked.primaryCode).toBe('MD-1');
    expect(applyCustomerMark(payload, '')).toEqual(payload);
  });
});
