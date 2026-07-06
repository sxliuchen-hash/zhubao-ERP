import { describe, expect, it } from 'vitest';
import { getOpenExceptionCount, inboundOrders, inventoryBalances, workQueues } from './m1PrototypeData';

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
});
