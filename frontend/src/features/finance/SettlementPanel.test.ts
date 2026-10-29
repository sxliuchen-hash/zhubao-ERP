import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SettlementPanel from './SettlementPanel.vue';
import { apiGet, apiPost, ApiRequestError } from '../../services/apiClient';

vi.mock('../../services/apiClient', async () => ({ ...await vi.importActual('../../services/apiClient'), apiGet: vi.fn(), apiPost: vi.fn() }));
const context = { eligible: true, feeTarget: true, deliveryState: 'COMPLETE', deliveryRevision: 'rev-1', targetType: 'CHILD', targetCode: 'ZD-1', customerName: '客户甲', blocks: [], labor: [], current: null };
function button(wrapper: ReturnType<typeof mount>, text: string) { return wrapper.findAll('button').find(item => item.text() === text)!; }

describe('交货后结单稳定请求', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(apiGet).mockResolvedValue(context); });
  it.each([0, 408, 503])('响应丢失或状态 %s 时冻结原请求，换意图不能再提交，重试成功后只刷新', async status => {
    vi.mocked(apiPost).mockRejectedValueOnce(status ? new ApiRequestError('响应丢失', status, status) : new Error('响应丢失')).mockResolvedValueOnce({ code: 'SET-1', status: 'CONFIRMED' });
    const wrapper = mount(SettlementPanel, { props: { targetType: 'CHILD', targetCode: 'ZD-1' } });
    await flushPromises();
    await wrapper.get('[aria-label="客户加工费"]').setValue('100');
    await button(wrapper, '确认结单').trigger('click'); await flushPromises();
    const body = structuredClone(vi.mocked(apiPost).mock.calls[0][1]);
    expect(wrapper.text()).toContain('结单结果待核对');
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined();
    await button(wrapper, '保存草稿').trigger('click');
    expect(apiPost).toHaveBeenCalledTimes(1);
    vi.mocked(apiGet).mockRejectedValueOnce(new Error('刷新失败'));
    await button(wrapper, '原结单请求重试').trigger('click'); await flushPromises();
    expect(vi.mocked(apiPost).mock.calls[1]).toEqual(['/api/finance/order-settlements/confirmations', body]);
    expect(wrapper.text()).toContain('结单已确认 SET-1');
    expect(wrapper.text()).toContain('刷新失败');
    expect(wrapper.text()).not.toContain('原结单请求重试');
    expect(wrapper.emitted('changed')).toHaveLength(1);
    expect(wrapper.emitted('locked')?.at(-1)).toEqual([false]);
  });
  it('明确拒绝后允许修正金额，下一次是新请求', async () => {
    vi.mocked(apiPost).mockRejectedValueOnce(new ApiRequestError('金额不符', 400, 40001)).mockResolvedValueOnce({ code: 'SET-2', status: 'DRAFT' });
    const wrapper = mount(SettlementPanel, { props: { targetType: 'CHILD', targetCode: 'ZD-1' } }); await flushPromises();
    await button(wrapper, '保存草稿').trigger('click'); await flushPromises();
    expect(wrapper.get('fieldset').attributes('disabled')).toBeUndefined();
    const first = vi.mocked(apiPost).mock.calls[0][1] as { requestId: string };
    await wrapper.get('[aria-label="客户加工费"]').setValue('120');
    await button(wrapper, '保存草稿').trigger('click'); await flushPromises();
    const second = vi.mocked(apiPost).mock.calls[1][1] as { requestId: string };
    expect(second.requestId).not.toBe(first.requestId);
  });
  it('迟到的其他单查询不能覆盖当前结单对象', async () => {
    let resolveFirst!: (value: typeof context) => void;
    vi.mocked(apiGet).mockReturnValueOnce(new Promise(done => { resolveFirst = done; })).mockResolvedValueOnce({ ...context, targetCode: 'ZD-2' });
    const wrapper = mount(SettlementPanel, { props: { targetType: 'CHILD', targetCode: 'ZD-1' } });
    await wrapper.setProps({ targetCode: 'ZD-2' }); await flushPromises();
    resolveFirst(context); await flushPromises();
    expect(wrapper.text()).toContain('ZD-2'); expect(wrapper.text()).not.toContain('ZD-1');
  });
});
