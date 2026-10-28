import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HardwareIntegrationWorkspace from './HardwareIntegrationWorkspace.vue';
import { fetchHardwareWorkspace } from '../../services/hardwareIntegrationApi';
import { fetchAuthSession, type AuthSession } from '../../services/authApi';

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
    vi.mocked(fetchHardwareWorkspace).mockResolvedValue({ pocRecords: [], pocEvidence: [], summary: { pocRecords: 0, pocBlocked: 0 } });
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

  it('没有固定健康数据，管理员保留明确标记的诊断工具', async () => {
    vi.mocked(fetchHardwareWorkspace).mockResolvedValueOnce({ pocRecords: [], pocEvidence: [], summary: { pocRecords: 0, pocBlocked: 0 } });
    vi.mocked(fetchAuthSession).mockResolvedValueOnce({ role: 'ADMIN' } as AuthSession);
    const wrapper = mount(HardwareIntegrationWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('暂无现场验证记录');
    expect(wrapper.find('details[aria-label="管理员诊断区"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('提交会写入真实网关读数记录');
  });
});
