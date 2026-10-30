import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { describe, expect, it } from 'vitest';
import EmployeeSettlementPane from './EmployeeSettlementPane.vue';

describe('整单员工核算', () => {
  it('空工价不补0，切目标不串金额，外协和客户账单不出现', async () => {
    const wrapper = mount(EmployeeSettlementPane, {
      props: {
        targetCode: 'MD-20261030-1001',
        employeeQuantity: '462',
        outsourcingQuantity: '20',
        lossQuantity: null,
      },
    });
    expect(wrapper.get('[aria-label="第1个工价"]').element).toHaveProperty('value', '');
    expect(wrapper.text()).toContain('外协 20 不计入');
    expect(wrapper.text()).toContain('损耗尚未核定，不扣工资');
    expect(wrapper.text()).not.toContain('客户账单');
    expect(wrapper.text()).not.toContain('应收');
    await wrapper.get('[aria-label="第1个工价"]').setValue('2');
    await wrapper.get('[aria-label="第1个实作数量"]').setValue('462');

    await wrapper.setProps({ targetCode: 'MD-20261030-1002', employeeQuantity: '10' });
    await nextTick();
    expect(wrapper.get('[aria-label="第1个工价"]').element).toHaveProperty('value', '');
    expect(wrapper.get('[aria-label="第1个实作数量"]').element).toHaveProperty('value', '');

    await wrapper.setProps({
      targetCode: 'MD-20261030-1001',
      employeeQuantity: '462',
      saved: {
        targetCode: 'MD-20261030-1001',
        version: 4,
        shares: [{ payeeAccount: 'payee-a', processName: '抛光', quantity: '462', unitPrice: '2', proxy: false }],
      },
    });
    await nextTick();
    expect(wrapper.text()).toContain('版本 4');
    expect(wrapper.get('[aria-label="第1个计酬账号"]').element).toHaveProperty('value', 'payee-a');
    expect(wrapper.get('[aria-label="第1个工序"]').element).toHaveProperty('value', '抛光');
    expect(wrapper.get('[aria-label="第1个工价"]').element).toHaveProperty('value', '2');
    wrapper.unmount();
  });
});
