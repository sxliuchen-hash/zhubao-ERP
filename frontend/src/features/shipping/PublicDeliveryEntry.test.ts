import { flushPromises, mount } from '@vue/test-utils';
import { expect, it, vi } from 'vitest';
import PublicDeliveryEntry from './PublicDeliveryEntry.vue';
import { fetchPublicDelivery } from '../../services/deliveryApi';
vi.mock('../../services/deliveryApi', () => ({ fetchPublicDelivery: vi.fn(), publicPhotoUrl: (token: string, code: string) => `/api/public/deliveries/${token}/photos/${code}` }));
it('扫码页读取真实公开接口并展示完整快照，停用时不展示旧内容', async () => {
  vi.mocked(fetchPublicDelivery).mockResolvedValue({ accessId: 'a'.repeat(64), deliveryId: 'FH-20261101-0001', customerName: '恒信珠宝', remark: '按袋核对', publicPhotoIds: ['PHOTO'], bags: [{ bagCode: 'FH-20261101-0001-1', material: '牙骨', specification: '8mm', requirement: '精抛光并穿串', quantity: 480, unit: '颗', weight: 35, sourceCode: 'MD-20261029-0003' }] });
  const view = mount(PublicDeliveryEntry, { props: { token: 'a'.repeat(64), bag: '1' } });
  await flushPromises(); expect(fetchPublicDelivery).toHaveBeenCalledWith('a'.repeat(64));
  expect(view.text()).toContain('恒信珠宝'); expect(view.text()).toContain('精抛光并穿串'); expect(view.text()).not.toContain('查看允许的账单');
  expect(view.get('img').attributes('src')).toContain('/photos/PHOTO');
  vi.mocked(fetchPublicDelivery).mockRejectedValue(new Error('公开链接已停用'));
  await view.setProps({ token: 'b'.repeat(64) }); await flushPromises();
  expect(view.text()).toContain('公开链接已停用'); expect(view.text()).not.toContain('精抛光'); expect(view.find('img').exists()).toBe(false);
});
