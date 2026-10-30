import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import FactoryStockPane from './FactoryStockPane.vue';
import { issueFactoryMaterial, returnFactoryMaterial } from '../../services/factoryStockApi';

vi.mock('../../services/factoryStockApi', () => ({
  issueFactoryMaterial: vi.fn().mockResolvedValue({ issueId: 'LY-1' }),
  returnFactoryMaterial: vi.fn().mockResolvedValue({ returnId: 'TH-1' }),
}));

const positions = [
  { label: '库内' as const, location: 'WH-01', quantity: '60', unit: '颗' },
  { label: '车间' as const, location: 'WS-01', quantity: '40', unit: '颗' },
  { label: '未还' as const, location: 'P-01', quantity: '6', unit: '把' },
];

describe('本厂物料查询与领退', () => {
  it('库内车间和未还分开，领退不提交总余额', async () => {
    const wrapper = mount(FactoryStockPane, {
      props: { itemId: 'FI-1', batchCode: 'PC-1', unit: '颗', positions, issueId: 'LY-1', issueVersion: 1 },
    });
    expect(wrapper.text()).toContain('库内 WH-01 60颗');
    expect(wrapper.text()).toContain('车间 WS-01 40颗');
    expect(wrapper.text()).toContain('未还 P-01 6把');
    expect(wrapper.text()).toContain('没有可手改的总余额');
    expect(wrapper.find('[aria-label="总余额"]').exists()).toBe(false);
    await wrapper.get('[aria-label="来源位置"]').setValue('P-01');
    await wrapper.get('[aria-label="领用数量"]').setValue('6');
    await wrapper.findAll('button').find(button => button.text() === '领用')!.trigger('click');
    expect(issueFactoryMaterial).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('未还不计入可领');

    await wrapper.get('[aria-label="来源位置"]').setValue('WH-01');
    await wrapper.get('[aria-label="车间"]').setValue('WS-01');
    await wrapper.get('[aria-label="领用数量"]').setValue('10');
    await wrapper.findAll('button').find(button => button.text() === '领用')!.trigger('click');
    await flushPromises();
    expect(issueFactoryMaterial).toHaveBeenCalledWith(expect.objectContaining({
      itemId: 'FI-1', batchCode: 'PC-1', fromWarehouse: 'WH-01', workshopCode: 'WS-01', quantity: '10', weight: null,
    }));
    expect(JSON.stringify(vi.mocked(issueFactoryMaterial).mock.calls[0][0])).not.toContain('106');

    await wrapper.setProps({ itemId: 'FI-2' });
    expect((wrapper.get('[aria-label="领用数量"]').element as HTMLInputElement).value).toBe('');

    await wrapper.get('[aria-label="退回数量"]').setValue('4');
    await wrapper.findAll('button').find(button => button.text() === '退回')!.trigger('click');
    await flushPromises();
    expect(returnFactoryMaterial).toHaveBeenCalledWith('LY-1', expect.objectContaining({ quantity: '4', version: 1, weight: null }));
    wrapper.unmount();
  });
});
