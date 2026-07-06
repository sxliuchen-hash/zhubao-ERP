import { describe, expect, it } from 'vitest';
import {
  getHardwareManualFallbackCount,
  getOpenExceptionCount,
  hardwareDevices,
  inboundCheckpoints,
  inboundRisks,
  inboundOrders,
  inventoryBalances,
  inventoryLedgerEntries,
  mobileCapabilities,
  offlineDrafts,
  productionHandoffs,
  productionStationLoads,
  qualityMetrics,
  sampleScenarios,
  traceEvidence,
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

  it('keeps M2 workspace review samples centralized', () => {
    expect(inboundCheckpoints.some((item) => item.label === '视频完整率')).toBe(true);
    expect(inboundRisks.every((risk) => risk.owner && risk.action)).toBe(true);
    expect(productionStationLoads.some((station) => station.bottleneck.includes('称重'))).toBe(true);
    expect(productionHandoffs.some((handoff) => handoff.status === '审批阻断')).toBe(true);
    expect(qualityMetrics.some((metric) => metric.label === '损耗阻断')).toBe(true);
    expect(inventoryLedgerEntries.every((entry) => entry.source && entry.operator)).toBe(true);
    expect(traceEvidence.some((item) => item.status.includes('失败'))).toBe(true);
    expect(mobileCapabilities.some((item) => item.label === '离线草稿')).toBe(true);
    expect(offlineDrafts.some((draft) => draft.tone === 'red')).toBe(true);
  });
});
