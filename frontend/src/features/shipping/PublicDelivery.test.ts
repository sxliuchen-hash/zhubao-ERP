import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PublicDeliveryPage from './PublicDeliveryPage.vue';
import { parseDeliveryScan, presentPublicDelivery } from './publicDelivery';

const token = 'a'.repeat(64);

describe('公开交付扫码', () => {
  it('保留厂内码，公开页不因设备授权，停用有明确状态', () => {
    expect(parseDeliveryScan('JG|work_order|ZD-20261016-0001')).toEqual({
      kind: 'factory',
      raw: 'JG|work_order|ZD-20261016-0001',
    });
    expect(parseDeliveryScan(`https://erp.example/d/${token}?from=wechat`).kind).toBe('public');
    const base = {
      disabled: false,
      bagCodes: ['FH-20261030-0001-1'],
      photoIds: ['PHOTO-1'],
      deliveryId: 'FH-20261030-0001',
      loggedIn: false,
      roleAllowsFinance: false,
      device: 'phone',
      network: 'office-lan',
    };
    const outsider = presentPublicDelivery(base);
    expect(outsider.internalHref).toBeNull();
    expect(outsider.financeHref).toBeNull();
    expect(outsider.bagCodes).toEqual(['FH-20261030-0001-1']);
    const insider = presentPublicDelivery({ ...base, loggedIn: true, roleAllowsFinance: true });
    expect(insider.internalHref).toBe('/orders?delivery=FH-20261030-0001');
    expect(insider.internalHref).not.toContain('session');
    expect(insider.financeHref).not.toContain('token');
    expect(presentPublicDelivery({ ...base, disabled: true }).status).toBe('已停用');

    const wrapper = mount(PublicDeliveryPage, {
      props: {
        location: 'JG|mother|MD-20261030-1001',
        disabled: false,
        bagCodes: [],
        photoIds: [],
        deliveryId: '',
        loggedIn: false,
        roleAllowsFinance: false,
        device: 'phone',
        network: 'office-lan',
      },
    });
    expect(wrapper.text()).toContain('厂内工单码 JG|mother|MD-20261030-1001');
    wrapper.unmount();
  });
});
