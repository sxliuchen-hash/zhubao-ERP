import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import OrderChangePanel from './OrderChangePanel.vue';
import { apiGet, apiPost, ApiRequestError } from '../../services/apiClient';

vi.mock('../../services/apiClient', async () => ({ ...await vi.importActual<typeof import('../../services/apiClient')>('../../services/apiClient'), apiGet: vi.fn(), apiPost: vi.fn() }));
vi.mock('../evidence/EvidenceCapture.vue', () => ({ default: { template: '<div>原单证据</div>' } }));
const row = { id: 'CHANGE-1', motherOrderCode: 'MO-1', workOrderCode: 'WO-1', status: 'PENDING_FINANCE', expectedVersion: 2, versionNo: 3, oldRequirement: '原要求', newRequirement: '新要求', customerUnitPrice: 1899, workerFee: 800, customerDelta: 200 };
const mounted: ReturnType<typeof mount<typeof OrderChangePanel>>[] = [];
function panel(finance = false) {
  const wrapper = mount(OrderChangePanel, { props: { target: { orderCode: 'WO-1', motherOrderCode: 'MO-1' } }, global: { provide: { 'zhubao-auth-session': ref({ effectivePermissions: finance ? ['FINANCE_BILLING_POST'] : [] }) } } });
  mounted.push(wrapper); return wrapper;
}
async function click(wrapper: ReturnType<typeof panel>, text: string) { await wrapper.findAll('button').find(button => button.text() === text)!.trigger('click'); await flushPromises(); }
async function choose(wrapper: ReturnType<typeof panel>, id = 'CHANGE-1') { await flushPromises(); await wrapper.findAll('button').find(button => button.text().startsWith(id + ' ·'))!.trigger('click'); }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => { vi.mocked(apiGet).mockReset().mockResolvedValue([row]); vi.mocked(apiPost).mockReset(); });
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

describe('独立改单面板', () => {
  it('普通岗位可核对要求差异，但没有财务确认字段或未确认就生效的入口', async () => {
    const wrapper = mount(OrderChangePanel, { props: { target: { orderCode: 'WO-1', motherOrderCode: 'MO-1' } }, global: { provide: { 'zhubao-auth-session': ref({ role: 'OPERATOR', effectivePermissions: [] }) } } });
    await wrapper.findAll('button').find(button => button.text() === '刷新改单')!.trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().includes('CHANGE-1'))!.trigger('click');
    expect(wrapper.text()).toContain('原要求');
    expect(wrapper.text()).toContain('新要求');
    expect(wrapper.text()).not.toContain('1899');
    expect(wrapper.find('[aria-label="改单客户单价"]').exists()).toBe(false);
    expect(wrapper.findAll('button').some(button => button.text() === '确认金额不变')).toBe(false);
    expect(wrapper.findAll('button').find(button => button.text() === '生效改单')!.attributes('disabled')).toBeDefined();
    expect(apiPost).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it('申请成功后的只读刷新失败保留真实改单号与成功提示', async () => {
    vi.mocked(apiPost).mockResolvedValue({ ...row, status: 'DRAFT', id: 'CHANGE-SAVED' });
    vi.mocked(apiGet).mockResolvedValueOnce([row]).mockRejectedValue(new Error('查询断网'));
    const wrapper = mount(OrderChangePanel, { props: { target: { orderCode: 'WO-1', motherOrderCode: 'MO-1' } } });
    await flushPromises();
    await wrapper.get('[aria-label="改单新要求"]').setValue('保留本次要求');
    await wrapper.findAll('button').find(button => button.text() === '保存改单申请')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('已保存改单 CHANGE-SAVED');
    expect(wrapper.text()).toContain('只读刷新失败：查询断网');
    expect(apiPost).toHaveBeenCalledOnce();
    expect(apiPost).toHaveBeenCalledWith('/api/business/order-changes', expect.objectContaining({ motherOrderCode: 'MO-1', workOrderCode: 'WO-1', newRequirement: '保留本次要求' }));
    wrapper.unmount();
  });
  it('申请基准来自本目标已生效业务版本，超时后保留完整原请求并阻止换号绕过冲突', async () => {
    vi.mocked(apiGet).mockResolvedValue([
      { ...row, status: 'APPLIED', versionNo: 2 },
      { ...row, id: 'OTHER', workOrderCode: 'WO-2', status: 'APPLIED', versionNo: 9 },
    ]);
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('超时')).mockRejectedValueOnce(new ApiRequestError('基准过期', 409, 40901)).mockResolvedValueOnce({ ...row, status: 'DRAFT' });
    const wrapper = panel(); await flushPromises();
    await wrapper.get('[aria-label="改单新要求"]').setValue('新要求|保留分隔符');
    await click(wrapper, '保存改单申请');
    const original = structuredClone(vi.mocked(apiPost).mock.calls[0][1]);
    expect(original).toMatchObject({ expectedVersion: 2, motherOrderCode: 'MO-1', workOrderCode: 'WO-1' });
    expect(wrapper.get('.change-compose fieldset').attributes('disabled')).toBeDefined();
    await click(wrapper, '原改单请求重试');
    expect(wrapper.text()).toContain('原操作结果待核对');
    await click(wrapper, '原改单请求重试');
    expect(vi.mocked(apiPost).mock.calls.map(call => call[1])).toEqual([original, original, original]);
    await click(wrapper, '保存改单申请');
    expect(apiPost).toHaveBeenCalledTimes(3);
  });
  it('没有读取申请基准时不能写入，列表出现另一母单也不开放保存', async () => {
    vi.mocked(apiGet).mockResolvedValue([{ ...row, motherOrderCode: 'MO-OTHER' }]);
    const wrapper = panel(); await flushPromises(); await click(wrapper, '保存改单申请');
    expect(wrapper.text()).toContain('改单列表与当前母单不一致'); expect(apiPost).not.toHaveBeenCalled();
  });
  it('无幂等键的送审超时后禁止重发，只读核对仍为草稿时继续保留未知状态', async () => {
    vi.mocked(apiGet).mockResolvedValueOnce([{ ...row, status: 'DRAFT' }]);
    vi.mocked(apiPost).mockRejectedValue(new Error('超时'));
    const wrapper = panel(); await choose(wrapper); await click(wrapper, '送财务确认');
    await click(wrapper, '送财务确认'); expect(apiPost).toHaveBeenCalledTimes(1);
    expect(wrapper.findAll('button').some(button => button.text() === '原改单请求重试')).toBe(false);
    vi.mocked(apiGet).mockResolvedValueOnce({ ...row, status: 'DRAFT' });
    await click(wrapper, '只读核对原改单'); expect(wrapper.text()).toContain('结果尚不能确认');
    vi.mocked(apiGet).mockResolvedValueOnce(row);
    await click(wrapper, '只读核对原改单');
    expect(wrapper.text()).toContain('当前为待财务确认，未再次提交原操作');
    expect(wrapper.find('[aria-label="原改单请求待核对"]').exists()).toBe(false);
    expect(apiPost).toHaveBeenCalledTimes(1);
  });
  it('生效固定财务确认基准，提交中切另一改单及迟到列表不能覆盖原响应', async () => {
    const confirmed = { ...row, status: 'FINANCE_CONFIRMED' }; const other = { ...row, id: 'CHANGE-2', expectedVersion: 8 };
    vi.mocked(apiGet).mockResolvedValueOnce([confirmed, other]);
    const wrapper = panel(); await choose(wrapper);
    const stale = deferred<unknown>(); vi.mocked(apiGet).mockReturnValueOnce(stale.promise);
    const refresh = wrapper.findAll('button').find(button => button.text() === '刷新改单')!;
    await refresh.trigger('click');
    const writing = deferred<unknown>(); vi.mocked(apiPost).mockReturnValueOnce(writing.promise);
    const applying = wrapper.findAll('button').find(button => button.text() === '生效改单')!;
    await applying.trigger('click');
    await wrapper.findAll('button').find(button => button.text().startsWith('CHANGE-2 ·'))!.trigger('click');
    expect(vi.mocked(apiPost).mock.calls[0]).toEqual(['/api/business/order-changes/CHANGE-1/applications', expect.objectContaining({ expectedVersion: 2, requestId: expect.any(String) })]);
    vi.mocked(apiGet).mockRejectedValue(new Error('刷新失败'));
    writing.resolve({ ...confirmed, status: 'APPLIED' }); await flushPromises();
    stale.resolve([other]); await flushPromises();
    expect(wrapper.get('[aria-label="改单详情"]').text()).toContain('CHANGE-1 · 已生效');
    expect(wrapper.text()).toContain('改单已生效 CHANGE-1');
    await click(wrapper, '生效改单'); expect(apiPost).toHaveBeenCalledTimes(1);
  });
  it('生效超时重试仍使用原版本和编号，不受列表刷新与其他改单影响', async () => {
    vi.mocked(apiGet).mockResolvedValue([{ ...row, status: 'FINANCE_CONFIRMED' }]);
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('超时')).mockResolvedValueOnce({ ...row, status: 'APPLIED' });
    const wrapper = panel(); await choose(wrapper); await click(wrapper, '生效改单');
    const original = structuredClone(vi.mocked(apiPost).mock.calls[0][1]);
    vi.mocked(apiGet).mockResolvedValue([{ ...row, status: 'FINANCE_CONFIRMED', expectedVersion: 9 }]);
    await click(wrapper, '刷新改单'); await click(wrapper, '原改单请求重试');
    expect(vi.mocked(apiPost).mock.calls[1][1]).toEqual(original);
    expect(original).toMatchObject({ expectedVersion: 2 });
  });
  it('财务差额按原接口明确应收、工序与工人，不把空单价当零', async () => {
    const wrapper = panel(true); await choose(wrapper);
    await click(wrapper, '确认差额'); expect(apiPost).not.toHaveBeenCalled();
    await wrapper.get('[aria-label="改单客户单价"]').setValue('12.50');
    await wrapper.get('[aria-label="改单工人工费"]').setValue('2.25');
    await wrapper.get('[aria-label="改单客户差额"]').setValue('-10.50');
    await wrapper.get('[aria-label="改单工费差额"]').setValue('1.25');
    await click(wrapper, '确认差额'); expect(wrapper.text()).toContain('减额必须指定原应收行');
    await wrapper.get('[aria-label="改单原应收行"]').setValue('AR-1');
    await wrapper.get('[aria-label="改单差额工序"]').setValue('抛光');
    await wrapper.get('[aria-label="改单差额工人"]').setValue('工人甲');
    vi.mocked(apiPost).mockResolvedValue({ ...row, status: 'FINANCE_CONFIRMED' });
    await click(wrapper, '确认差额');
    expect(apiPost).toHaveBeenCalledWith('/api/business/order-changes/CHANGE-1/finance-confirmations', expect.objectContaining({ customerUnitPrice: '12.50', workerFee: '2.25', customerDelta: '-10.50', workerDelta: '1.25', receivableCode: 'AR-1', processName: '抛光', workerName: '工人甲' }));
  });
  it('改单证据组件按改单号保留，切换记录不会重用或销毁另一单本地文件容器', async () => {
    vi.mocked(apiGet).mockResolvedValue([row, { ...row, id: 'CHANGE-2' }]);
    const wrapper = panel(); await choose(wrapper);
    const evidence = wrapper.getComponent({ name: 'EvidenceCapture' }).vm.$;
    await choose(wrapper, 'CHANGE-2'); await choose(wrapper);
    expect(wrapper.findAllComponents({ name: 'EvidenceCapture' })).toHaveLength(2);
    expect(wrapper.getComponent({ name: 'EvidenceCapture' }).vm.$).toBe(evidence);
    expect(evidence.isUnmounted).toBe(false);
  });
  it('成功回执防止陈旧列表倒退状态，已确认金额不能再次确认或拒绝', async () => {
    const wrapper = panel(true); await choose(wrapper);
    vi.mocked(apiPost).mockResolvedValue({ ...row, status: 'FINANCE_CONFIRMED' });
    await click(wrapper, '确认金额不变');
    expect(wrapper.get('[aria-label="改单详情"]').text()).toContain('财务已确认');
    await click(wrapper, '确认金额不变'); await click(wrapper, '拒绝改单');
    expect(apiPost).toHaveBeenCalledTimes(1);
  });
  it('财务填写按改单分别保留，切单不能把另一份草稿带入当前请求', async () => {
    vi.mocked(apiGet).mockResolvedValue([row, { ...row, id: 'CHANGE-2' }]);
    const wrapper = panel(true); await choose(wrapper);
    await wrapper.get('[aria-label="改单客户单价"]').setValue('10.25');
    await choose(wrapper, 'CHANGE-2');
    expect(wrapper.get('[aria-label="改单客户单价"]').element).toHaveProperty('value', '');
    await wrapper.get('[aria-label="改单客户单价"]').setValue('20.50');
    await choose(wrapper);
    expect(wrapper.get('[aria-label="改单客户单价"]').element).toHaveProperty('value', '10.25');
  });
});
