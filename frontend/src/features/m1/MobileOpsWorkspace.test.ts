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

function forRole(role: string) {
  return mount(MobileOpsWorkspace, {
    global: { provide: { 'zhubao-auth-session': ref({ role } as AuthSession) } },
  });
}

describe('MobileOpsWorkspace', () => {
  it('shows the receiver only the collection flow', () => {
    const wrapper = forRole('RECEIVER');
    expect(wrapper.find('[data-job="intake"]').exists()).toBe(true);
    expect(wrapper.find('[role="tablist"]').exists()).toBe(false);
    expect(wrapper.find('.field-extra-modes').exists()).toBe(false);
  });

  it('opens reporting for operators and keeps progress reporting secondary', async () => {
    const wrapper = forRole('OPERATOR');
    expect(wrapper.find('[data-job="station"]').exists()).toBe(true);
    expect(wrapper.find('[data-job="intake"]').exists()).toBe(false);
    expect(wrapper.find('[data-job="quality"]').exists()).toBe(false);
    await wrapper.get('.field-extra-actions button').trigger('click');
    expect(wrapper.find('[data-job="progress"]').exists()).toBe(true);
  });

  it('opens inspection directly for inspectors', () => {
    const wrapper = forRole('QUALITY_INSPECTOR');
    expect(wrapper.find('[data-job="quality"]').exists()).toBe(true);
    expect(wrapper.find('[data-job="station"]').exists()).toBe(false);
  });

  it('keeps shipping available to warehouse staff without putting it beside collection', async () => {
    const wrapper = forRole('WAREHOUSE_KEEPER');
    expect(wrapper.find('[data-job="intake"]').exists()).toBe(true);
    await wrapper.get('.field-extra-actions button').trigger('click');
    expect(wrapper.find('[data-job="shipping"]').exists()).toBe(true);
  });

  it('does not offer submissions to a management role without field permissions', () => {
    const wrapper = forRole('GENERAL_MANAGER');
    expect(wrapper.text()).toContain('当前岗位没有现场提交操作');
    expect(wrapper.find('[data-job]').exists()).toBe(false);
  });
});
