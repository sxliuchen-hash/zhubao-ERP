import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { queryScanContext } from '../../services/scanContextApi';
import { buildScanContext } from '../scan/scanContextFixture';
import MobileOrderDetail from './MobileOrderDetail.vue';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn() };
});
vi.mock('../scan/ScanInput.vue', () => ({
  default: {
    template: '<div><button data-scan @click="$emit(\'recognize\', { content: \'JG|work_order|WO-1\' })">扫码</button><button data-edit @click="$emit(\'update:modelValue\', \'OTHER\')">改号</button></div>',
  },
}));
vi.mock('../scan/ProgressNoteForm.vue', () => ({ default: { template: '<div data-quality-note />' } }));

beforeEach(() => vi.mocked(queryScanContext).mockReset());

describe('手机扫码订单详情', () => {
  it('连接共享办理容器时只有一个报工入口，不能自行执行交工', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-1', actions: [{ code: 'REPORT_PROCESS', allowed: true, blockKind: null, blockReason: null }] }));
    const wrapper = mount(MobileOrderDetail, { props: { role: 'OPERATOR', unifiedProduction: true } });
    await wrapper.get('[data-scan]').trigger('click');
    await flushPromises();
    expect(wrapper.get('.mobile-order-actions .primary-button').text()).toBe('报工');
    expect(wrapper.find('.mobile-order-actions').text()).not.toContain('更新工序进度');
    expect(wrapper.find('.mobile-order-actions').text()).not.toContain('正式交工');
    await wrapper.get('.mobile-order-actions .primary-button').trigger('click');
    expect(wrapper.emitted('open')?.[0]).toEqual(['station', { code: 'WO-1', objectType: 'WORK_ORDER' }]);
    wrapper.unmount();
  });
  it('操作工优先看到要求和进度操作，换号后旧单立即消失', async () => {
    const context = buildScanContext({
      objectType: 'WORK_ORDER', code: 'WO-1', customerName: '内部客户', goodsName: '戒指',
      actions: [{ code: 'REPORT_PROCESS', allowed: true, blockKind: null, blockReason: null }],
    });
    context.requirements.workOrderRequirement = '抛光后检查表面';
    vi.mocked(queryScanContext).mockResolvedValue(context);
    const wrapper = mount(MobileOrderDetail, { props: { role: 'OPERATOR' } });
    await wrapper.get('[data-scan]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('抛光后检查表面');
    expect(wrapper.text()).toContain('更新工序进度');
    expect(wrapper.text()).toContain('正式交工');
    expect(wrapper.find('.mobile-order-facts').text()).not.toContain('内部客户');
    await wrapper.get('.mobile-order-actions .primary-button').trigger('click');
    expect(wrapper.emitted('open')?.[0]).toEqual(['progress', { code: 'WO-1', objectType: 'WORK_ORDER' }]);
    await wrapper.get('[data-edit]').trigger('click');
    expect(wrapper.find('.mobile-order-card').exists()).toBe(false);
  });

  it('质检只给质检入口，多子单先选具体子单', async () => {
    vi.mocked(queryScanContext)
      .mockResolvedValueOnce(buildScanContext({
        objectType: 'MOTHER_ORDER', code: 'MO-1', requiresSelection: true,
        relatedTargets: [{ objectType: 'WORK_ORDER', code: 'WO-1', motherOrderCode: 'MO-1', requirement: '首件', quantity: 1, quantityUnit: '件' }],
      }))
      .mockResolvedValueOnce(buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-1', requirement: '首件' }));
    const wrapper = mount(MobileOrderDetail, { props: { role: 'QUALITY_INSPECTOR' } });
    await wrapper.get('[data-scan]').trigger('click');
    await flushPromises();
    expect(wrapper.find('.mobile-order-choices').exists()).toBe(true);
    expect(wrapper.find('.mobile-order-card .mobile-order-actions').exists()).toBe(false);
    await wrapper.get('.mobile-order-choices button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('记录质检过程');
    expect(wrapper.text()).toContain('逐条判定质检');
    expect(wrapper.find('.mobile-order-card').text()).not.toContain('更新工序进度');
    await wrapper.get('.mobile-order-card .primary-button').trigger('click');
    expect(wrapper.find('[data-quality-note]').exists()).toBe(true);
    await wrapper.get('.mobile-order-card .secondary-button').trigger('click');
    expect(wrapper.emitted('open')?.[0]).toEqual(['quality', { code: 'WO-1', objectType: 'WORK_ORDER' }]);
  });

  it('收货人员无需贴纸即可建单，运单也可进入收货', async () => {
    const wrapper = mount(MobileOrderDetail, { props: { role: 'RECEIVER' } });
    await wrapper.get('.mobile-order-create').trigger('click');
    expect(wrapper.emitted('open')?.[0]).toEqual(['intake', undefined]);
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'EXPRESS_NO', code: 'SF123' }));
    await wrapper.get('[data-scan]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('创建收货订单');
    await wrapper.get('.mobile-order-card .primary-button').trigger('click');
    expect(wrapper.emitted('open')?.[1]).toEqual(['intake', { code: 'SF123', objectType: 'EXPRESS_NO' }]);
  });

  it('只展示会话有效权限允许的现场操作', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'WORK_ORDER', code: 'WO-1',
      actions: [{ code: 'REPORT_PROCESS', allowed: true, blockKind: null, blockReason: null }],
    }));
    const wrapper = mount(MobileOrderDetail, { props: { role: 'WORKSHOP_LEADER', allowedActions: ['quality'] } });
    await wrapper.get('[data-scan]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('逐条判定质检');
    expect(wrapper.text()).not.toContain('正式交工');
    expect(wrapper.text()).not.toContain('更新工序进度');
  });
});
