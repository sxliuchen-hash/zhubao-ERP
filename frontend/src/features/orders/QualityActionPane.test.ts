import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import QualityActionPane from './QualityActionPane.vue';
import { fetchQualityWorkspace } from '../../services/businessWorkspaceApi';

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchQualityWorkspace: vi.fn() }));
vi.mock('../quality/QualityPanel.vue', () => ({ default: { template: '<div>当前质检办理</div>' } }));
vi.mock('../quality/LossApplicationPanel.vue', () => ({ default: { props: ['application'], template: '<div data-loss>{{ application.workOrder }}<button @click="$emit(\'locked\', true)">保留原请求</button></div>' } }));
const context = { motherOrderCode: 'MD-1', workOrderCode: 'WO-1', customerId: 1, ownerCustomerId: 1, materialCode: 'MAT', warehouseCode: 'RAW', ownerType: 'CUSTOMER', stockStatus: 'SEMI_FINISHED', quantity: 1, weight: 10 };
beforeEach(() => vi.mocked(fetchQualityWorkspace).mockReset().mockResolvedValue({ metrics: [], lossApprovals: [], cases: [{ code: 'QC-1', workOrder: 'WO-1', process: '抛光', inspector: '质检员', defect: '', status: '待质检', action: '', operationContext: context }] }));
function create(permissions = ['BUSINESS_LOSS_APPROVAL']) {
  return mount(QualityActionPane, { props: { target: { objectType: 'WORK_ORDER', code: 'WO-1' }, active: true }, global: { provide: { 'zhubao-auth-session': ref({ effectivePermissions: permissions }) } } });
}
it('损耗按需读取本单唯一来源，关闭办理面板保留原请求', async () => {
  const wrapper = create();
  expect(fetchQualityWorkspace).not.toHaveBeenCalled();
  wrapper.get('details').element.open = true; await new Promise(resolve => setTimeout(resolve, 0)); await flushPromises();
  expect(wrapper.get('[data-loss]').text()).toContain('WO-1');
  await wrapper.get('[data-loss] button').trigger('click');
  await wrapper.setProps({ active: false }); await wrapper.setProps({ active: true });
  expect(wrapper.find('[data-loss]').exists()).toBe(true);
  expect(fetchQualityWorkspace).toHaveBeenCalledOnce();
});
it('缺少损耗权限不读取来源，读取失败或来源不明确可重试且不猜首条', async () => {
  const forbidden = create([]); expect(forbidden.find('details').exists()).toBe(false); forbidden.unmount();
  vi.mocked(fetchQualityWorkspace).mockRejectedValueOnce(new Error('读取失败')).mockResolvedValueOnce({ metrics: [], lossApprovals: [], cases: [] });
  const wrapper = create(); wrapper.get('details').element.open = true; await new Promise(resolve => setTimeout(resolve, 0)); await flushPromises();
  expect(wrapper.text()).toContain('读取失败');
  await wrapper.get('details button').trigger('click'); await flushPromises();
  expect(wrapper.text()).toContain('尚未取得唯一损耗来源');
  expect(wrapper.find('[data-loss]').exists()).toBe(false);
});
