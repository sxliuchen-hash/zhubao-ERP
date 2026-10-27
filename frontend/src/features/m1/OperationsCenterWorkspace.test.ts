import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OperationsCenterWorkspace from './OperationsCenterWorkspace.vue';
import type { OperationsCenterSnapshot } from '../../data/operationsCenterData';
import { fetchOperationsCenterSnapshot } from '../../services/operationsCenterApi';

vi.mock('../../services/operationsCenterApi', () => ({ fetchOperationsCenterSnapshot: vi.fn() }));

const collectedSnapshot: OperationsCenterSnapshot = {
  generatedAt: '2026-10-10 12:00:00',
  summary: {
    overallStatus: 'ONLINE', serviceCount: 0, degradedServices: 0, openErrors: 0,
    criticalErrors: 0, openAlerts: 0, criticalAlerts: 0, backupStatus: '正常', uptimeMinutes: 10,
  },
  services: [],
  runtimeMetrics: [],
  errorSummaries: [],
  recentLogs: [{
    time: '2026-10-10 12:00:00', level: 'INFO', source: 'gateway',
    message: '读取现场设备', traceId: 'TRACE-COLLECTED-001', tone: 'green',
  }],
  backupStatuses: [],
  restoreDrillRecords: [],
  alerts: [],
  checklist: [],
  hardwareNodes: [{
    code: 'SCAN-COLLECTED-01', name: '现场扫码枪', scene: '收货', status: '在线',
    protocol: 'USB HID', detail: '本次读取结果', checkedAt: '2026-10-10 12:00:00', tone: 'green',
  }],
  storageVolumes: [],
};

describe('OperationsCenterWorkspace', () => {
  beforeEach(() => vi.resetAllMocks());

  it('首次读取失败显示未知状态，不显示静态监测数据', async () => {
    vi.mocked(fetchOperationsCenterSnapshot).mockRejectedValue(new Error('监测服务不可用'));
    const wrapper = mount(OperationsCenterWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('监测服务不可用');
    expect(wrapper.text()).toContain('尚未取得真实运行状态');
    expect(wrapper.text()).not.toContain('拆包台摄像头');
    expect(wrapper.text()).not.toContain('TRACE-OPS-001');
    expect(wrapper.find('.operations-hero-grid').exists()).toBe(false);
  });

  it('刷新失败保留最后一次真实采集结果并标明采集时间', async () => {
    vi.mocked(fetchOperationsCenterSnapshot).mockResolvedValueOnce(collectedSnapshot);
    const wrapper = mount(OperationsCenterWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('现场扫码枪');
    vi.mocked(fetchOperationsCenterSnapshot).mockRejectedValueOnce(new Error('网络中断'));
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('网络中断');
    expect(wrapper.text()).toContain('保留最后一次真实采集结果：2026-10-10 12:00:00');
    expect(wrapper.text()).toContain('TRACE-COLLECTED-001');
  });
});
