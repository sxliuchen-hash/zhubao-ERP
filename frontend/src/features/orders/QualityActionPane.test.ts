import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import QualityActionPane from './QualityActionPane.vue';
import { fetchQualityWorkspace } from '../../services/businessWorkspaceApi';
import { fetchProductionTarget, fetchBagDetail, fetchShippablePortion, submitPortionRelease } from '../../services/productionQualityApi';

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchQualityWorkspace: vi.fn() }));
vi.mock('../../services/productionQualityApi', () => ({ fetchProductionTarget: vi.fn(), fetchBagDetail: vi.fn(), fetchBagAvailability: vi.fn(), submitWholeBagReport: vi.fn(), fetchShippablePortion: vi.fn(), submitPortionInspection: vi.fn(), submitPortionRelease: vi.fn(), submitPortionRework: vi.fn() }));
vi.mock('../quality/QualityPanel.vue', () => ({ default: { template: '<div>当前质检办理</div>' } }));
vi.mock('../quality/LossApplicationPanel.vue', () => ({ default: { props: ['application'], template: '<div data-loss>{{ application.workOrder }}<button @click="$emit(\'locked\', true)">保留原请求</button></div>' } }));
const context = { motherOrderCode: 'MD-1', workOrderCode: 'WO-1', customerId: 1, ownerCustomerId: 1, materialCode: 'MAT', warehouseCode: 'RAW', ownerType: 'CUSTOMER', stockStatus: 'SEMI_FINISHED', quantity: 1, weight: 10 };
beforeEach(() => {
  const bag = { id: 'BAG-REAL', targetCode: 'WO-1', version: 7, quantity: 10, materialCode: '牙骨', requirementVersion: 'REQ-REAL', sourceKey: 'LINE-REAL', weight: { state: 'KNOWN', value: 1 } };
  vi.mocked(fetchProductionTarget).mockResolvedValue({ targetCode: 'WO-1', customerId: 1, quantityUnit: '颗', requirement: '', defaultSteps: [], route: null, bags: [{ bag, availability: {} as never, shippable: { bagId: bag.id, quantity: 0, blockingReasons: [] } }] });
  vi.mocked(fetchBagDetail).mockResolvedValue({ bag, inspections: [{ pass: 6, rework: 4, scrap: 0, pending: 0 }], hold: { released: 0, frozen: 0 }, weight: bag.weight });
  vi.mocked(submitPortionRelease).mockReset().mockResolvedValue({ released: 6, frozen: 0 });
  vi.mocked(fetchQualityWorkspace).mockReset().mockResolvedValue({ metrics: [], lossApprovals: [], cases: [{ code: 'QC-1', workOrder: 'WO-1', process: '抛光', inspector: '质检员', defect: '', status: '待质检', action: '', operationContext: context }] });
  vi.mocked(fetchShippablePortion).mockReset().mockResolvedValue({ bagId: 'WO-1', quantity: 0, blockingReasons: ['穿串未完，不能仅凭质检合格发出'] });
});
function create(permissions = ['BUSINESS_LOSS_APPROVAL']) {
  return mount(QualityActionPane, { props: { target: { objectType: 'WORK_ORDER', code: 'WO-1' }, active: true }, global: { provide: { 'zhubao-auth-session': ref({ effectivePermissions: permissions }) } } });
}
it('损耗按需读取本单唯一来源，关闭办理面板保留原请求', async () => {
  const wrapper = create();
  expect(fetchQualityWorkspace).not.toHaveBeenCalled();
  (wrapper.get('details.section-block').element as HTMLDetailsElement).open = true; await new Promise(resolve => setTimeout(resolve, 0)); await flushPromises();
  expect(wrapper.get('[data-loss]').text()).toContain('WO-1');
  await wrapper.get('[data-loss] button').trigger('click');
  await wrapper.setProps({ active: false }); await wrapper.setProps({ active: true });
  expect(wrapper.find('[data-loss]').exists()).toBe(true);
  expect(fetchQualityWorkspace).toHaveBeenCalledOnce();
});
it('缺少损耗权限不读取来源，读取失败或来源不明确可重试且不猜首条', async () => {
  const forbidden = create([]); expect(forbidden.find('details.section-block').exists()).toBe(false); forbidden.unmount();
  vi.mocked(fetchQualityWorkspace).mockRejectedValueOnce(new Error('读取失败')).mockResolvedValueOnce({ metrics: [], lossApprovals: [], cases: [] });
  const wrapper = create(); (wrapper.get('details.section-block').element as HTMLDetailsElement).open = true; await new Promise(resolve => setTimeout(resolve, 0)); await flushPromises();
  expect(wrapper.text()).toContain('读取失败');
  await wrapper.get('details.section-block button').trigger('click'); await flushPromises();
  expect(wrapper.text()).toContain('尚未取得唯一损耗来源');
  expect(wrapper.find('[data-loss]').exists()).toBe(false);
});
it('缺条件显示具体原因，放行按合格数量而不是百分比', async () => {
  const wrapper = create([]);
  await flushPromises();
  expect(wrapper.get('[aria-label="按袋质检"]').text()).toContain('穿串未完，不能仅凭质检合格发出');
  expect(wrapper.text()).not.toContain('百分比');
  await wrapper.findAll('button').find(button => button.text() === '放行合格部分')!.trigger('click');
  await flushPromises();
  expect(submitPortionRelease).toHaveBeenCalledWith(expect.objectContaining({ bagId: 'BAG-REAL', bagVersion: 7, quantity: 6 }));
  wrapper.unmount();
});
