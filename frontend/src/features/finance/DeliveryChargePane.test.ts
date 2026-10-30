import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { describe, expect, it } from 'vitest';
import DeliveryChargePane from './DeliveryChargePane.vue';

describe('本次交付计费', () => {
  it('空单价不补0，切单不带走金额，保存后能恢复方式和依据', async () => {
    const wrapper = mount(DeliveryChargePane, {
      props: {
        deliveryId: 'FH-20261030-0001',
        customerName: '客户甲',
        shippedQuantity: '300',
        quantityUnit: '颗',
      },
    });
    expect(wrapper.get('[aria-label="第1个单价"]').element).toHaveProperty('value', '');
    expect(wrapper.text()).not.toContain('工资');
    await wrapper.get('[aria-label="第1个项目"]').setValue('雕刻');
    await wrapper.get('[aria-label="第1个数量"]').setValue('300');
    await wrapper.get('[aria-label="第1个单价"]').setValue('2');
    expect(wrapper.text()).toContain('300颗 × 2元／颗');

    await wrapper.setProps({ deliveryId: 'FH-20261030-0002', shippedQuantity: '10' });
    await nextTick();
    expect(wrapper.get('[aria-label="第1个单价"]').element).toHaveProperty('value', '');
    expect(wrapper.get('[aria-label="第1个项目"]').element).toHaveProperty('value', '');
    expect(wrapper.text()).not.toContain('300颗 × 2元／颗');

    await wrapper.setProps({
      deliveryId: 'FH-20261030-0001',
      shippedQuantity: '300',
      saved: {
        deliveryId: 'FH-20261030-0001',
        version: 3,
        lines: [{
          name: '加工费',
          method: 'BASE_MINUS_QUANTITY',
          quantity: '',
          unitPrice: '2',
          baseQuantity: '500',
          exemptQuantity: '38',
          directAmount: '',
          direction: '',
          basis: '免计38颗',
        }],
      },
    });
    await nextTick();
    expect(wrapper.text()).toContain('版本 3');
    expect(wrapper.get('[aria-label="第1个计费方式"]').element).toHaveProperty('value', 'BASE_MINUS_QUANTITY');
    expect(wrapper.get('[aria-label="第1个免计"]').element).toHaveProperty('value', '38');
    expect(wrapper.get('[aria-label="第1个依据"]').element).toHaveProperty('value', '免计38颗');
    expect(wrapper.text()).toContain('（500 − 38）颗 × 2元');
    wrapper.unmount();
  });
});
