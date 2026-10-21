import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import MobileOpsWorkspace from './MobileOpsWorkspace.vue';

vi.mock('../intake/IntakePanel.vue', () => ({ default: { template: '<div data-job="intake" />' } }));
vi.mock('../production/ProductionPanel.vue', () => ({ default: { template: '<div data-job="station" />' } }));
vi.mock('../quality/QualityPanel.vue', () => ({ default: { template: '<div data-job="quality" />' } }));
vi.mock('../shipping/ShippingPanel.vue', () => ({ default: { template: '<div data-job="shipping" />' } }));
vi.mock('./ProcessProgressPanel.vue', () => ({ default: { template: '<div data-job="progress" />' } }));
vi.mock('./MobileOrderDetail.vue', () => ({
  default: {
    template: '<div data-job="detail"><button data-open="intake" @click="$emit(\'open\', \'intake\')" /><button data-open="station" @click="$emit(\'open\', \'station\')" /><button data-open="progress" @click="$emit(\'open\', \'progress\')" /><button data-open="quality" @click="$emit(\'open\', \'quality\')" /><button data-open="shipping" @click="$emit(\'open\', \'shipping\')" /></div>',
  },
}));

function forRole(role: string, coverDuties?: string[], effectivePermissions?: string[]) {
  return mount(MobileOpsWorkspace, {
    global: { provide: { 'zhubao-auth-session': ref({ role, coverDuties, effectivePermissions } as AuthSession) } },
  });
}

describe('MobileOpsWorkspace', () => {
  it('only opens modes granted by effective permissions', async () => {
    const wrapper = forRole('WORKSHOP_LEADER', [], ['BUSINESS_QUALITY_FREEZE']);
    await wrapper.get('[data-open="station"]').trigger('click');
    expect(wrapper.find('[data-job="station"]').exists()).toBe(false);
    await wrapper.get('[data-open="progress"]').trigger('click');
    expect(wrapper.find('[data-job="progress"]').exists()).toBe(false);
    await wrapper.get('[data-open="quality"]').trigger('click');
    expect(wrapper.find('[data-job="quality"]').exists()).toBe(true);
  });

  it('starts the receiver at the scan detail and opens collection', async () => {
    const wrapper = forRole('RECEIVER');
    expect(wrapper.find('[data-job="detail"]').exists()).toBe(true);
    await wrapper.get('[data-open="intake"]').trigger('click');
    expect(wrapper.find('[data-job="intake"]').exists()).toBe(true);
    await wrapper.get('.mobile-order-back').trigger('click');
    expect(wrapper.find('[data-job="detail"]').exists()).toBe(true);
  });

  it('opens reporting and progress from the operator detail', async () => {
    const wrapper = forRole('OPERATOR');
    await wrapper.get('[data-open="station"]').trigger('click');
    expect(wrapper.find('[data-job="station"]').exists()).toBe(true);
    expect(wrapper.find('[data-job="intake"]').exists()).toBe(false);
    expect(wrapper.find('[data-job="quality"]').exists()).toBe(false);
    await wrapper.get('.mobile-order-back').trigger('click');
    await wrapper.get('[data-open="progress"]').trigger('click');
    expect(wrapper.find('[data-job="progress"]').exists()).toBe(true);
  });

  it('opens inspection from the inspector detail', async () => {
    const wrapper = forRole('QUALITY_INSPECTOR');
    await wrapper.get('[data-open="quality"]').trigger('click');
    expect(wrapper.find('[data-job="quality"]').exists()).toBe(true);
    expect(wrapper.find('[data-job="station"]').exists()).toBe(false);
  });

  it('keeps shipping available to warehouse staff', async () => {
    const wrapper = forRole('WAREHOUSE_KEEPER');
    await wrapper.get('[data-open="intake"]').trigger('click');
    expect(wrapper.find('[data-job="intake"]').exists()).toBe(true);
    await wrapper.get('.mobile-order-back').trigger('click');
    await wrapper.get('[data-open="shipping"]').trigger('click');
    expect(wrapper.find('[data-job="shipping"]').exists()).toBe(true);
  });

  it('lets business managers cover receiving, reporting and inspection', async () => {
    const wrapper = forRole('BUSINESS_MANAGER', ['INTAKE', 'REPORT', 'QUALITY']);
    expect(wrapper.find('[data-job="detail"]').exists()).toBe(true);
    await wrapper.get('[data-open="intake"]').trigger('click');
    expect(wrapper.find('[data-job="intake"]').exists()).toBe(true);
    await wrapper.get('.mobile-order-back').trigger('click');
    await wrapper.get('[data-open="station"]').trigger('click');
    expect(wrapper.find('[data-job="station"]').exists()).toBe(true);
    await wrapper.get('.mobile-order-back').trigger('click');
    await wrapper.get('[data-open="quality"]').trigger('click');
    expect(wrapper.find('[data-job="quality"]').exists()).toBe(true);
    await wrapper.get('.mobile-order-back').trigger('click');
    await wrapper.get('[data-open="progress"]').trigger('click');
    expect(wrapper.find('[data-job="progress"]').exists()).toBe(true);
    await wrapper.get('.mobile-order-back').trigger('click');
    await wrapper.get('[data-open="shipping"]').trigger('click');
    expect(wrapper.find('[data-job="shipping"]').exists()).toBe(false);
  });

  it('does not offer submissions to a management role without field permissions', () => {
    const wrapper = forRole('GENERAL_MANAGER');
    expect(wrapper.text()).toContain('当前岗位没有现场提交操作');
    expect(wrapper.find('[data-job]').exists()).toBe(false);
  });
});
