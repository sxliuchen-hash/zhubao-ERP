import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, expect, it, vi } from 'vitest';
import OutsourcingWorkspace from './OutsourcingWorkspace.vue';
import { fetchOutsourcingPosition, submitOutsourcingDispatch, submitOutsourcingReturn } from '../../services/outsourcingApi';

vi.mock('../../services/outsourcingApi', () => ({
  fetchOutsourcingPosition: vi.fn(),
  submitOutsourcingDispatch: vi.fn(),
  submitOutsourcingReturn: vi.fn(),
}));

const position = {
  taskId: 'OT1',
  targetCode: 'MD-1',
  nodeId: 'POLISH_BEAD',
  partyId: 'WX-1',
  partyName: '甲抛光',
  ownerType: 'CUSTOMER',
  ownerRef: '10086',
  inHouseQuantity: 120,
  outQuantity: 80,
  pendingQuantity: 80,
  outsourcingCountsAsInHouseStock: false,
  dispatches: [{ dispatchId: 'OD1', version: 2, quantity: 200, stillOut: 80 }],
  returns: [],
  sources: [
    { bagId: 'BAG-IN', quantity: 120, version: 3, holder: 'IN_HOUSE', inHouseStock: true, sourceKey: 'LINE-1' },
    { bagId: 'BAG-OUT', quantity: 80, version: 1, holder: 'WX-1', inHouseStock: false, sourceKey: 'LINE-1' },
  ],
};

beforeEach(() => {
  vi.mocked(fetchOutsourcingPosition).mockReset().mockResolvedValue(position);
  vi.mocked(submitOutsourcingDispatch).mockReset().mockResolvedValue({ dispatchId: 'OD2' });
  vi.mocked(submitOutsourcingReturn).mockReset().mockResolvedValue({ returnId: 'OR1' });
});

function mountWorkspace() {
  return mount(OutsourcingWorkspace, { props: { orderCode: 'MD-1', active: true } });
}

it('在当前单据里发外和收回，不重复填写客户和来源', async () => {
  const wrapper = mountWorkspace();
  expect(wrapper.text()).not.toContain('直发客户');
  expect(wrapper.find('[aria-label="客户"]').exists()).toBe(false);
  expect(wrapper.find('[aria-label="外协账号"]').exists()).toBe(false);
  await wrapper.get('[aria-label="外协任务号"]').setValue('OT1');
  await wrapper.get('button').trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('甲抛光');
  expect(wrapper.text()).toContain('10086');
  expect(wrapper.text()).toContain('LINE-1');
  expect(wrapper.text()).toContain('在外 80');
  expect(wrapper.text()).toContain('待核 80');
  expect(wrapper.text()).not.toContain('在外不能当成厂内现货');
  const options = wrapper.get('[aria-label="本厂袋"]').findAll('option').map(option => option.text());
  expect(options.join(' ')).toContain('BAG-IN');
  expect(options.join(' ')).not.toContain('BAG-OUT');
  await wrapper.get('[aria-label="本厂袋"]').setValue('BAG-IN');
  await wrapper.get('[aria-label="发外数量"]').setValue('40');
  await wrapper.findAll('button').find(button => button.text() === '发外')!.trigger('click');
  await flushPromises();
  expect(submitOutsourcingDispatch).toHaveBeenCalledWith(expect.objectContaining({
    taskId: 'OT1', bagId: 'BAG-IN', bagVersion: 3, quantity: 40,
  }));
  expect(vi.mocked(submitOutsourcingDispatch).mock.calls[0][0]).not.toHaveProperty('ownerRef');
  await wrapper.get('[aria-label="原发外行"]').setValue('OD1');
  await wrapper.get('[aria-label="实收数量"]').setValue('30');
  await wrapper.get('[aria-label="已确认损耗"]').setValue('0');
  await wrapper.findAll('button').find(button => button.text() === '按原行收回')!.trigger('click');
  await flushPromises();
  expect(submitOutsourcingReturn).toHaveBeenCalledWith(expect.objectContaining({
    dispatchId: 'OD1', dispatchVersion: 2, received: 30, loss: 0, weight: null,
  }));
});

it('任务不属于当前单据时不能发外', async () => {
  vi.mocked(fetchOutsourcingPosition).mockResolvedValue({ ...position, targetCode: 'MD-OTHER' });
  const wrapper = mountWorkspace();
  await wrapper.get('[aria-label="外协任务号"]').setValue('OT1');
  await wrapper.get('button').trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('这张外协任务不属于当前单据');
  expect(wrapper.find('[aria-label="发外数量"]').exists()).toBe(false);
  expect(submitOutsourcingDispatch).not.toHaveBeenCalled();
});
