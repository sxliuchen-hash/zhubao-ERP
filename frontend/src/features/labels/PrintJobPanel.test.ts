import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet, apiPost, ApiRequestError } from '../../services/apiClient';
import type { LabelPayload } from '../../services/labelApi';
import PrintJobPanel from './PrintJobPanel.vue';

vi.mock('../../services/apiClient', async () => ({ ...await vi.importActual<typeof import('../../services/apiClient')>('../../services/apiClient'), apiGet: vi.fn(), apiPost: vi.fn() }));
const payload: LabelPayload = { type: 'WORK_ORDER', typeLabel: '子工单', primaryCode: 'WO-1', qrContent: 'JG|work_order|WO-1|1|LI1', fields: [], footerLeft: '', footerRight: '', printedAt: '', brandTitle: '珠宝', templateCode: 'FLOW', printerSetting: { widthMm: 40, heightMm: 30, dpi: 203, printerName: '', copies: 1 } };
const frozen = JSON.stringify({ businessCode: 'WO-1', version: 'V1', instance: 'LI1', qr: payload.qrContent, widthMm: 40, heightMm: 30, dpi: 203, fields: [{ name: '原要求', value: '刻字' }] });
const job = { id: 'J1', targetStationId: 'A', targetQueueId: 'Q1', status: 'PENDING', copies: 1, paperCheckResult: '', failureReason: '', predecessorId: '', retryReason: '' };
const batch = { id: 'PB1', businessCode: 'WO-1', contentSnapshot: frozen, jobs: [job] };
const wrappers: ReturnType<typeof mount<typeof PrintJobPanel>>[] = [];
function panel() { const wrapper = mount(PrintJobPanel, { props: { payload } }); wrappers.push(wrapper); return wrapper; }
async function click(wrapper: ReturnType<typeof panel>, text: string) { await wrapper.findAll('button').find(button => button.text() === text)!.trigger('click'); await flushPromises(); }
async function submit(wrapper: ReturnType<typeof panel>) { await flushPromises(); await wrapper.get('input[type=checkbox]').setValue(true); await click(wrapper, '提交到打印工位'); }
beforeEach(() => {
  vi.mocked(apiPost).mockReset().mockResolvedValue(structuredClone(batch));
  vi.mocked(apiGet).mockReset().mockImplementation(async path => path.includes('print-queues') ? [
    { stationId: 'A', queueId: 'Q1', name: '原队列', labelType: 'WORK_ORDER', widthMm: 40, heightMm: 30, enabled: true },
    { stationId: 'B', queueId: 'Q2', name: '备用队列', labelType: 'WORK_ORDER', widthMm: 40, heightMm: 30, enabled: true },
  ] : structuredClone(batch));
});
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()));
describe('原单打印请求与快照', () => {
  it('超时冻结原目标份数和编号，版本变化及冲突仍不能绕过原请求', async () => {
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('超时')).mockRejectedValueOnce(new ApiRequestError('快照版本冲突', 409, 40901)).mockResolvedValueOnce(structuredClone(batch));
    const wrapper = panel(); await submit(wrapper);
    const original = structuredClone(vi.mocked(apiPost).mock.calls[0][1]);
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined();
    await wrapper.setProps({ payload: { ...payload, qrContent: 'JG|work_order|WO-1|2|LI2' } });
    await click(wrapper, '原打印请求重试');
    expect(wrapper.text()).toContain('原打印请求结果待核对');
    await click(wrapper, '原打印请求重试');
    expect(vi.mocked(apiPost).mock.calls.map(call => call[1])).toEqual([original, original, original]);
    expect(wrapper.text()).toContain('PB1');
  });
  it('成功后刷新失败保留冻结快照，只有显式另建草稿才产生新打印请求', async () => {
    const wrapper = panel(); await submit(wrapper);
    vi.mocked(apiGet).mockRejectedValueOnce(new Error('读取失败'));
    await click(wrapper, '刷新目标状态');
    expect(wrapper.text()).toContain('PB1 刷新失败');
    expect(wrapper.text()).toContain('原要求');
    expect(wrapper.text()).toContain('刻字');
    await click(wrapper, '提交到打印工位');
    expect(apiPost).toHaveBeenCalledTimes(1);
    await click(wrapper, '另建一批打印草稿'); await click(wrapper, '提交到打印工位');
    expect(apiPost).toHaveBeenCalledTimes(2);
    expect(vi.mocked(apiPost).mock.calls[1][1]).not.toEqual(vi.mocked(apiPost).mock.calls[0][1]);
  });
  it('改派超时复用原命令，成功后状态刷新失败也保留关联批次及原快照', async () => {
    const wrapper = panel(); await submit(wrapper);
    await wrapper.get('[aria-label="J1改派目标"]').setValue('B|Q2');
    await wrapper.get('[aria-label="J1改派原因"]').setValue('原工位停机');
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('超时'));
    await click(wrapper, '取消原目标并创建关联任务');
    const original = structuredClone(vi.mocked(apiPost).mock.calls[1][1]);
    vi.mocked(apiPost).mockResolvedValueOnce({ ...batch, id: 'PB2', jobs: [{ ...job, id: 'J2', targetStationId: 'B', targetQueueId: 'Q2', predecessorId: 'J1' }] });
    vi.mocked(apiGet).mockRejectedValue(new Error('刷新失败'));
    await click(wrapper, '原改派请求重试');
    expect(vi.mocked(apiPost).mock.calls[2][1]).toEqual(original);
    expect(wrapper.text()).toContain('改派批次 PB2 已创建');
    expect(wrapper.text()).toContain('来源任务 J1');
    expect(wrapper.text()).toContain('已取消');
    expect(wrapper.text()).toContain(payload.qrContent);
  });
  it('当前预览不能改写已有批次版本，改派队列按原快照纸张匹配', async () => {
    const wrapper = panel(); await submit(wrapper);
    await wrapper.setProps({ payload: { ...payload, qrContent: 'JG|work_order|WO-1|2|LI2', printerSetting: { ...payload.printerSetting, widthMm: 60 } } });
    expect(wrapper.text()).toContain('V1 · LI1');
    expect(wrapper.get('[aria-label="J1改派目标"]').findAll('option').map(option => option.attributes('value'))).toContain('B|Q2');
  });
  it('失败只显示原因和取消，取消后不再挡住下一张', async () => {
    vi.mocked(apiPost).mockResolvedValueOnce({ ...batch, jobs: [{ ...job, status: 'FAILED', failureReason: '保存标识载荷无效，不能省略打印' }] });
    const wrapper = panel();
    await submit(wrapper);
    expect(wrapper.text()).toContain('失败：保存要求无法打印');
    expect(wrapper.text()).not.toContain('核纸结果');
    await click(wrapper, '取消');
    expect(apiPost).toHaveBeenCalledWith('/api/label/print-target-jobs/J1/cancellations', {});
    expect(wrapper.text()).toContain('已取消');
  });
  it('权限撤销后禁用核纸与改派，不能通过保留草稿发起写入', async () => {
    const wrapper = panel(); await submit(wrapper);
    await wrapper.setProps({ disabled: true });
    expect(wrapper.get('[aria-label="J1改派目标"]').element.closest('fieldset')?.disabled).toBe(true);
    await click(wrapper, '取消原目标并创建关联任务');
    expect(apiPost).toHaveBeenCalledTimes(1);
  });
  it('无幂等键的核纸记录超时后只读核对，直到服务端返回相同记录才解锁', async () => {
    vi.mocked(apiPost).mockResolvedValueOnce({ ...batch, jobs: [{ ...job, status: 'UNCERTAIN' }] });
    const wrapper = panel(); await submit(wrapper);
    await wrapper.get('[aria-label="J1核纸结果"]').setValue('确认未出纸');
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('超时'));
    await click(wrapper, '记录核纸'); await click(wrapper, '记录核纸');
    expect(apiPost).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('核纸记录结果待核对');
    vi.mocked(apiGet).mockResolvedValueOnce({ ...batch, jobs: [{ ...job, status: 'UNCERTAIN', paperCheckResult: '确认未出纸' }] });
    await click(wrapper, '刷新目标状态');
    expect(wrapper.text()).not.toContain('核纸记录结果待核对');
    expect(wrapper.find('[aria-label="J1改派目标"]').exists()).toBe(true);
  });
});
