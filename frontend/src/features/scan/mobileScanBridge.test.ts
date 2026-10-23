import { describe, expect, it } from 'vitest';
import { armMobileScan, bindMobileScanTarget, cancelMobileScan, openMobileScanTarget, unbindMobileScanTarget } from './mobileScanBridge';

describe('手机底栏扫码', () => {
  it('当前页已有扫码框时直接打开，离开页面后不再打开', () => {
    const calls: string[] = [];
    const open = () => { calls.push('open'); };
    bindMobileScanTarget(open);
    expect(openMobileScanTarget()).toBe(true);
    unbindMobileScanTarget(open);
    expect(openMobileScanTarget()).toBe(false);
    expect(calls).toEqual(['open']);
    cancelMobileScan();
  });

  it('页面还没挂上时先记住，扫码框出现后打开一次', () => {
    const calls: string[] = [];
    const open = () => { calls.push('open'); };
    armMobileScan();
    expect(openMobileScanTarget()).toBe(false);
    bindMobileScanTarget(open);
    expect(calls).toEqual(['open']);
    unbindMobileScanTarget(open);
    cancelMobileScan();
  });
});
