import { describe, expect, it } from 'vitest';
import { operationAmountError, visibleAmounts } from './operationAmounts';

describe('数量重量确认', () => {
  it('可见默认值来自正式上下文，缺一或留空不能通过，改数量后须重新确认重量', () => {
    expect(visibleAmounts({ quantity: 12, weight: 25.75 } as never)).toEqual({ quantity: '12', weight: '25.75' });
    expect(visibleAmounts(null)).toEqual({ quantity: '', weight: '' });
    const defaults = { quantity: '12', weight: '25.75' };
    expect(operationAmountError({ quantity: '12', weight: '25.75' }, defaults, true)).toBe('');
    expect(operationAmountError({ quantity: '2', weight: '' }, defaults, false)).toContain('必须同时填写');
    expect(operationAmountError({ quantity: '', weight: '6.2' }, defaults, false)).toContain('必须同时填写');
    expect(operationAmountError({ quantity: '', weight: '' }, defaults, true)).toContain('留空不会自动沿用整批重量');
    expect(operationAmountError({ quantity: '2', weight: '25.75' }, defaults, false)).toContain('重新确认本次重量');
    expect(operationAmountError({ quantity: '2', weight: '6.20' }, defaults, true)).toBe('');
  });
});
