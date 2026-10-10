import { describe, expect, it } from 'vitest';
import {
  getHardwareManualFallbackCount,
  getHardwarePocBlockedCount,
  acceptanceDataSets,
  hardwareDevices,
  hardwarePocRecords,
  openSourceSampleMappings,
  productionReadinessItems,
  sampleScenarios,
} from './m1ReviewFixtures';

describe('m1PrototypeData', () => {
  it('keeps hardware fallback cases explicit', () => {
    expect(hardwareDevices.some((device) => device.fallback.includes('人工'))).toBe(true);
    expect(getHardwareManualFallbackCount()).toBe(4);
    expect(hardwarePocRecords.some((record) => record.code === 'POC-SCAN-20260706-001')).toBe(true);
    expect(getHardwarePocBlockedCount()).toBe(1);
  });

  it('binds sample scenarios to open-source references and real acceptance fixtures', () => {
    expect(sampleScenarios.some((scenario) => scenario.id === 'SAMPLE-MAIN-001')).toBe(true);
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
    expect(productionReadinessItems.some((item) => item.code === 'P0-10' && item.defaultAction.includes('账号客户范围'))).toBe(true);
    expect(productionReadinessItems.some((item) => item.code === 'P1-HW-POC-01' && item.defaultAction.includes('hw_poc_record'))).toBe(true);
    expect(productionReadinessItems.filter((item) => item.priority === 'P0').length).toBeGreaterThanOrEqual(7);
    expect(productionReadinessItems.every((item) => item.owner && item.evidence && item.defaultAction)).toBe(true);
  });
});
