import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StockLedgerPanel from './StockLedgerPanel.vue';
import { fetchStockLedgers, type StockLedgerRecord } from '../../services/stockLedgerApi';

vi.mock('../../services/stockLedgerApi', () => ({ fetchStockLedgers: vi.fn() }));
const dimension = { ownerType: 'CUSTOMER', ownerCustomerId: 8, customerId: 7, motherOrderCode: 'MO-1', workOrderCode: 'WO-1', materialCode: 'MAT-1', warehouseCode: 'WH-A', stockStatus: 'FINISHED', batchCode: '' };
function entry(code: string): StockLedgerRecord { return { code, bizTime: '2026-10-23T12:00:00Z', documentType: 'customer_return', documentCode: `RET-${code}`, from: { ...dimension, stockStatus: 'SHIPPED' }, to: { ...dimension, warehouseCode: 'RETURN-CHECK' }, quantity: '10.500', weight: '1.250', remark: '原业务说明' }; }
beforeEach(() => vi.mocked(fetchStockLedgers).mockReset());
describe('独立出入库检索', () => {
  it('显示完整来源与去向，保留小数和未知单位，组合检索独立于分页', async () => {
    vi.mocked(fetchStockLedgers).mockResolvedValue([...Array.from({ length: 21 }, (_, index) => entry(`L-${index}`)), { ...entry('COMPANY'), documentType: 'company_receipt', to: { ...dimension, ownerType: 'COMPANY', customerId: null, ownerCustomerId: null, warehouseCode: 'COMPANY-WH' } }]);
    const wrapper = mount(StockLedgerPanel);
    await flushPromises();
    expect(wrapper.text()).toContain('当前账号可见 22 条');
    expect(wrapper.findAll('.ledger-entry')).toHaveLength(20);
    expect(wrapper.get('.ledger-entry').text()).toContain('数量 10.500（单位未提供） / 重量 1.250 克');
    expect(wrapper.get('.ledger-entry').text()).toContain('货主 8');
    expect(wrapper.get('.ledger-entry').text()).toContain('SHIPPED');
    expect(wrapper.get('.ledger-entry').text()).toContain('RETURN-CHECK');
    await wrapper.get('[aria-label="台账分页"] button:last-child').trigger('click');
    expect(wrapper.findAll('.ledger-entry')).toHaveLength(2);
    await wrapper.get('[aria-label="台账仓库"]').setValue('COMPANY-WH');
    await wrapper.get('[aria-label="台账货权"]').setValue('COMPANY');
    expect(wrapper.findAll('.ledger-entry')).toHaveLength(1);
    expect(wrapper.text()).toContain('第 1 / 1 页');
    expect(wrapper.get('.ledger-entry').text()).toContain('公司料');
    await wrapper.get('[aria-label="台账业务单号"]').setValue('RET-L-1');
    expect(wrapper.find('.ledger-entry').exists()).toBe(false);
    wrapper.unmount();
  });
  it('原单精确核对使用真实流水，刷新失败撤下旧记录并显示错误', async () => {
    vi.mocked(fetchStockLedgers).mockResolvedValueOnce([entry('A'), entry('B')]).mockRejectedValueOnce(new Error('没有读取台账的权限'));
    const wrapper = mount(StockLedgerPanel, { props: { initialDocumentCode: 'RET-A' } });
    await flushPromises();
    expect(wrapper.findAll('.ledger-entry')).toHaveLength(1);
    expect(wrapper.get('.ledger-entry').text()).toContain('RET-A');
    await wrapper.findAll('button').find(button => button.text() === '刷新台账')!.trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toBe('没有读取台账的权限');
    expect(wrapper.find('.ledger-entry').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('没有符合条件的可见流水');
    wrapper.unmount();
  });
});
