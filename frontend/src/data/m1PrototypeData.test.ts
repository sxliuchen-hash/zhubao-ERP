import { describe, expect, it } from 'vitest';
import {
  getHardwareManualFallbackCount,
  getHardwarePocBlockedCount,
  getOpenExceptionCount,
  acceptanceDataSets,
  hardwareDevices,
  hardwarePocRecords,
  inboundCheckpoints,
  inboundRisks,
  inboundOrders,
  inventoryBalances,
  inventoryLedgerEntries,
  lossApprovals,
  mobileCapabilities,
  offlineDrafts,
  openSourceSampleMappings,
  productionReadinessItems,
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
    expect(hardwarePocRecords.some((record) => record.code === 'POC-SCAN-20260706-001')).toBe(true);
    expect(getHardwarePocBlockedCount()).toBe(1);
  });

  it('keeps M2 workspace review samples centralized', () => {
    expect(inboundCheckpoints.some((item) => item.label === '视频完整率')).toBe(true);
    expect(inboundRisks.every((risk) => risk.owner && risk.action)).toBe(true);
    expect(productionStationLoads.some((station) => station.station === '雕刻' && station.bottleneck.includes('操机签字'))).toBe(true);
    expect(productionStationLoads.some((station) => station.station === '抛光' && station.bottleneck.includes('交单审核'))).toBe(true);
    expect(productionHandoffs.some((handoff) => handoff.status === '审批阻断')).toBe(true);
    expect(qualityMetrics.some((metric) => metric.label === '损耗阻断')).toBe(true);
    expect(inventoryLedgerEntries.every((entry) => entry.source && entry.operator)).toBe(true);
    expect(traceEvidence.some((item) => item.status.includes('失败'))).toBe(true);
    expect(mobileCapabilities.some((item) => item.label === '离线草稿')).toBe(true);
    expect(offlineDrafts.some((draft) => draft.tone === 'red')).toBe(true);
  });

  it('keeps loss approval samples usable for wage trial commands', () => {
    const overLimitLoss = lossApprovals.find((approval) => approval.code === 'LOSS-20260706-009');
    const missingWeightLoss = lossApprovals.find((approval) => approval.code === 'LOSS-20260706-010');

    expect(overLimitLoss).toBeDefined();
    expect(overLimitLoss?.standard).toMatch(/^\d+(\.\d+)?%$/);
    expect(overLimitLoss?.actual).toMatch(/^\d+(\.\d+)?%$/);
    expect(overLimitLoss?.evidence).toMatch(/投入 \d+(\.\d+)?g \/ 产出 \d+(\.\d+)?g/);
    expect(overLimitLoss?.action).toContain('审批');
    expect(missingWeightLoss?.actual).toBe('待确认');
    expect(missingWeightLoss?.evidence).toContain('缺少产出称重照片');
    expect(missingWeightLoss?.tone).toBe('amber');
  });

  it('binds sample scenarios to open-source references and real acceptance fixtures', () => {
    expect(openSourceSampleMappings).toHaveLength(4);
    expect(openSourceSampleMappings.every((mapping) => mapping.referenceUrl.startsWith('https://'))).toBe(true);
    expect(openSourceSampleMappings.every((mapping) => sampleScenarios.some((scenario) => scenario.id === mapping.mappedScenarioId))).toBe(true);
    expect(openSourceSampleMappings.some((mapping) => mapping.source.includes('ERPNext'))).toBe(true);
    expect(openSourceSampleMappings.some((mapping) => mapping.source.includes('Odoo'))).toBe(true);
    expect(openSourceSampleMappings.some((mapping) => mapping.source.includes('InvenTree'))).toBe(true);

    const realFlow = acceptanceDataSets.find((dataset) => dataset.code === 'REAL-FLOW-20260706-001');
    expect(realFlow).toBeDefined();
    expect(realFlow?.fixture).toContain('real-flow-scenario-20260706.json');
    expect(realFlow?.automatedTest).toBe('BusinessOperationRealFlowTest');
    expect(realFlow?.covers).toContain('客户所有权库存维度');
    expect(realFlow?.covers).toContain('售后复核分流');
    expect(realFlow?.evidence).toContain('HO-20260706-021');
    expect(realFlow?.gaps.length).toBeGreaterThan(0);
  });

  it('keeps production readiness gates explicit before pilot launch', () => {
    expect(productionReadinessItems.length).toBeGreaterThanOrEqual(10);
    expect(productionReadinessItems.some((item) => item.code === 'P0-02' && item.title === '损耗计算口径')).toBe(true);
    expect(productionReadinessItems.some((item) => item.code === 'P0-09' && item.title.includes('备份'))).toBe(true);
    expect(productionReadinessItems.some((item) => item.code === 'P0-10' && item.category === '权限')).toBe(true);
    expect(productionReadinessItems.some((item) => item.code === 'P1-HW-POC-01' && item.defaultAction.includes('hw_poc_record'))).toBe(true);
    expect(productionReadinessItems.filter((item) => item.priority === 'P0').length).toBeGreaterThanOrEqual(7);
    expect(productionReadinessItems.every((item) => item.owner && item.evidence && item.defaultAction)).toBe(true);
  });
});
