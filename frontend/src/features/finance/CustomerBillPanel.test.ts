import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import { fetchStatement, recordReceipt, type CustomerStatement } from '../../services/financeApi';
import { ApiRequestError } from '../../services/apiClient';
import CustomerBillPanel from './CustomerBillPanel.vue';

vi.mock('../../services/financeApi', () => ({ fetchStatement: vi.fn(), recordReceipt: vi.fn() }));
const statement: CustomerStatement = { customerId: 1001, customerName: '客户甲', stockQuantity: 0, stockWeight: 0, receivableAmount: 10, receivedAmount: 0, outstandingAmount: 10, prepaymentAmount: 0, lines: [], receipts: [], stockNote: '' };
const saved = { code: 'RC-1', amount: 100, receivedOn: '2026-10-24', allocations: [] };
const mounted: ReturnType<typeof mount<typeof CustomerBillPanel>>[] = [];
function panel(permissions = ['FINANCE_CUSTOMER_READ', 'FINANCE_RECEIPT']) {
  const wrapper = mount(CustomerBillPanel, { props: { context: { customerId: 1001, yearMonth: '2026-10' }, initialMode: 'receipt' }, global: { provide: { 'zhubao-auth-session': ref({ effectivePermissions: permissions }) } } });
  mounted.push(wrapper); return wrapper;
}
async function click(wrapper: ReturnType<typeof panel>, text: string) { await wrapper.findAll('button').find(button => button.text() === text)!.trigger('click'); await flushPromises(); }
async function fill(wrapper: ReturnType<typeof panel>) { await flushPromises(); await wrapper.get('[aria-label="收款金额"]').setValue('100'); await wrapper.get('[aria-label="收款日期"]').setValue('2026-10-24'); }
beforeEach(() => { vi.mocked(fetchStatement).mockReset().mockResolvedValue(statement); vi.mocked(recordReceipt).mockReset().mockResolvedValue(saved); });
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));
describe('原客户账单与收款', () => {
  it('收款可超过本月未收，空分摊保留全部预收，账单总额不自动填写收款金额', async () => {
    const wrapper = panel(); await flushPromises();
    expect(wrapper.get('[aria-label="收款金额"]').element).toHaveProperty('value', '');
    await fill(wrapper); await click(wrapper, '确认登记收款');
    expect(recordReceipt).toHaveBeenCalledWith(expect.objectContaining({ customerId: 1001, amount: 100, allocations: [], idempotencyKey: expect.any(String) }));
    expect(wrapper.text()).toContain('收款单号 RC-1');
    await click(wrapper, '确认登记收款'); expect(recordReceipt).toHaveBeenCalledTimes(1);
  });
  it('多应收行分摊保留实际数组及预收，不强制等于本次收款或当前月未收', async () => {
    const wrapper = panel(); await fill(wrapper);
    await wrapper.get('[aria-label="核销账单号"]').setValue('AR-OLD-MONTH'); await wrapper.get('[aria-label="核销金额"]').setValue('30.25');
    await click(wrapper, '增加分摊行'); await wrapper.get('[aria-label="第2个核销账单号"]').setValue('AR-2'); await wrapper.get('[aria-label="第2个核销金额"]').setValue('20.50');
    await click(wrapper, '确认登记收款');
    expect(recordReceipt).toHaveBeenCalledWith(expect.objectContaining({ amount: 100, allocations: [{ receivableCode: 'AR-OLD-MONTH', amount: 30.25 }, { receivableCode: 'AR-2', amount: 20.5 }] }));
  });
  it('收款超时固定原金额、分摊及幂等键，再读账单或传入另一行都不能改写原请求', async () => {
    vi.mocked(recordReceipt).mockRejectedValueOnce(new Error('超时')).mockRejectedValueOnce(new ApiRequestError('请求冲突', 409, 40901)).mockResolvedValueOnce(saved);
    const wrapper = panel(); await fill(wrapper); await wrapper.get('[aria-label="核销账单号"]').setValue('AR-1'); await wrapper.get('[aria-label="核销金额"]').setValue('80');
    await click(wrapper, '确认登记收款');
    const original = structuredClone(vi.mocked(recordReceipt).mock.calls[0][0]);
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined();
    await wrapper.setProps({ context: { customerId: 1001, yearMonth: '2026-10', receivableCode: 'AR-OTHER' }, entryRevision: 2 });
    await click(wrapper, '重新查询账单'); await click(wrapper, '原收款请求重试');
    expect(wrapper.text()).toContain('原收款结果待核对');
    await click(wrapper, '原收款请求重试');
    expect(vi.mocked(recordReceipt).mock.calls.map(call => call[0])).toEqual([original, original, original]);
  });
  it('登记成功后刷新失败只重读账单，明确另开一笔才生成新幂等键', async () => {
    vi.mocked(fetchStatement).mockResolvedValueOnce(statement).mockRejectedValueOnce(new Error('刷新失败')).mockResolvedValue(statement);
    const wrapper = panel(); await fill(wrapper); await click(wrapper, '确认登记收款');
    expect(wrapper.text()).toContain('请只重试查询，不要再次登记');
    await click(wrapper, '重新查询账单'); await click(wrapper, '确认登记收款');
    expect(recordReceipt).toHaveBeenCalledTimes(1);
    await click(wrapper, '另开一笔收款'); await wrapper.get('[aria-label="收款金额"]').setValue('20'); await click(wrapper, '确认登记收款');
    expect(recordReceipt).toHaveBeenCalledTimes(2);
    expect(vi.mocked(recordReceipt).mock.calls[1][0].idempotencyKey).not.toBe(vi.mocked(recordReceipt).mock.calls[0][0].idempotencyKey);
    expect(wrapper.text()).toContain('RC-1');
  });
  it('只读角色没有收款字段或写入口，无财务权限时不渲染金额', async () => {
    const wrapper = panel(['FINANCE_CUSTOMER_READ']); await flushPromises();
    expect(wrapper.find('[aria-label="收款金额"]').exists()).toBe(false);
    expect(wrapper.findAll('button').some(button => button.text() === '确认登记收款')).toBe(false);
    expect(recordReceipt).not.toHaveBeenCalled();
    const denied = panel([]); await flushPromises(); expect(denied.find('[aria-label="本客户账单"]').exists()).toBe(false);
    expect(denied.find('[aria-label="原客户收款草稿"]').exists()).toBe(false);
  });
  it('异客账单或应收行不能显示在本客户下', async () => {
    vi.mocked(fetchStatement).mockResolvedValue({ ...statement, customerId: 1002, customerName: '另一客户秘密' });
    const wrapper = panel(); await flushPromises(); expect(wrapper.text()).toContain('账单响应与本客户不一致');
    expect(wrapper.text()).not.toContain('另一客户秘密');
  });
  it('非法空金额或未明确分摊金额不发送写请求，已知拒绝后可以调整原草稿', async () => {
    const wrapper = panel(); await fill(wrapper); await wrapper.get('[aria-label="核销账单号"]').setValue('AR-1');
    await click(wrapper, '确认登记收款'); expect(recordReceipt).not.toHaveBeenCalled();
    await wrapper.get('[aria-label="核销金额"]').setValue('40');
    vi.mocked(recordReceipt).mockRejectedValueOnce(new ApiRequestError('超出原行可分摊余额', 400, 422));
    await click(wrapper, '确认登记收款'); expect(wrapper.find('[aria-label="原客户收款草稿"] fieldset').attributes('disabled')).toBeUndefined();
    await wrapper.get('[aria-label="核销金额"]').setValue('20'); await click(wrapper, '确认登记收款');
    expect(vi.mocked(recordReceipt).mock.calls[1][0].idempotencyKey).not.toBe(vi.mocked(recordReceipt).mock.calls[0][0].idempotencyKey);
  });
});
