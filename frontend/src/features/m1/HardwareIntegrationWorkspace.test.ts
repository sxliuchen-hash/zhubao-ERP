import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HardwareIntegrationWorkspace from './HardwareIntegrationWorkspace.vue';
import { fetchHardwareWorkspace } from '../../services/hardwareIntegrationApi';
import { fetchAuthSession, type AuthSession } from '../../services/authApi';

const hardwareDevices = [{
  code: 'CAM-TEST-001', name: '拆包台摄像头', scene: '拆包留证', protocol: 'USB',
  status: 'manual' as const, fallback: '人工上传照片或视频',
}];

vi.mock('../../services/hardwareIntegrationApi', () => ({ fetchHardwareWorkspace: vi.fn() }));
vi.mock('../../services/authApi', () => ({ fetchAuthSession: vi.fn() }));
vi.mock('../../services/deviceGatewayApi', () => ({
  fetchGatewayDevices: vi.fn().mockResolvedValue([]), fetchGatewaySummary: vi.fn().mockResolvedValue({ totalDevices: 0 }),
  fetchRecentConsumptions: vi.fn().mockResolvedValue([]), fetchRecentReadings: vi.fn().mockResolvedValue([]),
  consumeReading: vi.fn(), ingestDeviceReading: vi.fn(), registerGatewayDevice: vi.fn(),
  rotateGatewayDeviceKey: vi.fn(), setGatewayDeviceEnabled: vi.fn(),
}));

describe('HardwareIntegrationWorkspace', () => {
  beforeEach(() => {
    vi.mocked(fetchAuthSession).mockResolvedValue(null);
    vi.mocked(fetchHardwareWorkspace).mockResolvedValue({ devices: [], tasks: [], pocRecords: [], pocEvidence: [], summary: { totalDevices: 0, fallbackRequired: 0, averageHealth: 0, pocRecords: 0, pocBlocked: 0 } });
  });

  it('接口失败保持设备空态并显示原因，普通账号不显示模拟推送', async () => {
    vi.mocked(fetchHardwareWorkspace).mockRejectedValueOnce(new Error('硬件服务不可用'));
    const wrapper = mount(HardwareIntegrationWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('硬件服务不可用');
    expect(wrapper.text()).not.toContain('拆包台摄像头');
    expect(wrapper.findAll('table.hardware-device-table tbody tr')).toHaveLength(0);
    expect(wrapper.text()).not.toContain('模拟设备推送');
  });

  it('加载 API 设备，管理员仅在明确标记的诊断区使用模拟工具', async () => {
    vi.mocked(fetchHardwareWorkspace).mockResolvedValueOnce({ devices: hardwareDevices, tasks: [], pocRecords: [], pocEvidence: [], summary: { totalDevices: hardwareDevices.length, fallbackRequired: 0, averageHealth: 0, pocRecords: 0, pocBlocked: 0 } });
    vi.mocked(fetchAuthSession).mockResolvedValueOnce({ role: 'ADMIN' } as AuthSession);
    const wrapper = mount(HardwareIntegrationWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('拆包台摄像头');
    expect(wrapper.find('details[aria-label="管理员诊断区"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('提交会写入真实网关读数记录');
  });
});
