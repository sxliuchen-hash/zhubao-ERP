import { describe, expect, it } from 'vitest';
import {
  getHardwareManualFallbackCount,
  getOpenExceptionCount,
  hardwareDevices,
  inboundOrders,
  inventoryBalances,
  sampleScenarios,
  workQueues,
} from './m1PrototypeData';

describe('m1PrototypeData', () => {
  it('keeps customer-owned inbound and inventory sample data traceable', () => {
    expect(inboundOrders.length).toBeGreaterThan(0);
    expect(inboundOrders.every((order) => order.owner === '客户所有')).toBe(true);
    expect(inventoryBalances.some((item) => item.status === '在制')).toBe(true);
  });

  it('counts open exception queues that need active handling', () => {
    expect(workQueues).toHaveLength(4);
    expect(getOpenExceptionCount()).toBe(3);
  });

  it('keeps samples and hardware fallback cases explicit', () => {
    expect(sampleScenarios.some((scenario) => scenario.id === 'SAMPLE-MAIN-001')).toBe(true);
    expect(hardwareDevices.some((device) => device.fallback.includes('人工'))).toBe(true);
    expect(getHardwareManualFallbackCount()).toBe(4);
  });
});
