import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import WorkOrderRequirementForm from './WorkOrderRequirementForm.vue';
import { apiPost } from '../../services/apiClient';

vi.mock('../../services/apiClient', async () => ({
  ...await vi.importActual<typeof import('../../services/apiClient')>('../../services/apiClient'),
  apiPost: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(apiPost).mockReset();
});

describe('子单加工要求', () => {
  it('直接保存要求，不调用改单或财务接口', async () => {
    vi.mocked(apiPost).mockResolvedValue({ code: 'WO-1', motherOrderCode: 'MO-1', processingRequirement: '外圈刻字' });
    const wrapper = mount(WorkOrderRequirementForm, { props: { code: 'WO-1', requirement: '内壁刻字' } });
    await wrapper.get('[aria-label="子单加工要求"]').setValue('外圈刻字');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(apiPost).toHaveBeenCalledTimes(1);
    const [path, body] = vi.mocked(apiPost).mock.calls[0];
    expect(path).toBe('/api/business/work-orders/WO-1/requirement');
    expect(String(path)).not.toContain('order-changes');
    expect(body).toEqual(expect.objectContaining({ requirement: '外圈刻字', requestId: expect.stringMatching(/^req-/) }));
    expect(wrapper.text()).toContain('加工要求已保存');
    wrapper.unmount();
  });

  it('没有变化时不提交', async () => {
    const wrapper = mount(WorkOrderRequirementForm, { props: { code: 'WO-1', requirement: '内壁刻字' } });
    await wrapper.get('form').trigger('submit');
    expect(apiPost).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('加工要求没有变化');
    wrapper.unmount();
  });
});
