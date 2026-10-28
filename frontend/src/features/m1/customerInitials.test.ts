import { describe, expect, it } from 'vitest';
import { customerInitials } from './customerInitials';

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
});
