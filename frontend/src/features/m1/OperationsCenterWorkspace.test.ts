import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OperationsCenterWorkspace from './OperationsCenterWorkspace.vue';
import { operationsCenterFallback } from '../../data/operationsCenterData';
import { fetchOperationsCenterSnapshot } from '../../services/operationsCenterApi';

vi.mock('../../services/operationsCenterApi', () => ({ fetchOperationsCenterSnapshot: vi.fn() }));

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
    vi.mocked(fetchOperationsCenterSnapshot).mockResolvedValueOnce({ ...operationsCenterFallback, generatedAt: '2026-10-10 12:00:00' });
    const wrapper = mount(OperationsCenterWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('拆包台摄像头');
    vi.mocked(fetchOperationsCenterSnapshot).mockRejectedValueOnce(new Error('网络中断'));
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('网络中断');
    expect(wrapper.text()).toContain('保留最后一次真实采集结果：2026-10-10 12:00:00');
    expect(wrapper.text()).toContain('TRACE-OPS-001');
  });
});
