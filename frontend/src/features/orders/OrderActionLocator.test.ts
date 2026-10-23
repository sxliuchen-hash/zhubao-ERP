import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderActionLocator from './OrderActionLocator.vue';
import ScanInput from '../scan/ScanInput.vue';
import { queryScanContext, type ScanContextResponse } from '../../services/scanContextApi';
import { buildScanContext } from '../scan/scanContextFixture';

vi.mock('../../services/scanContextApi', async () => ({ ...await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi'), queryScanContext: vi.fn() }));
vi.mock('../scan/ScanInput.vue', () => ({ default: { template: '<div />' } }));
beforeEach(() => vi.mocked(queryScanContext).mockReset());
describe('作业目标定位', () => {
  it('多对象必须人工选择，只有规范对象才打开业务动作', async () => {
    vi.mocked(queryScanContext).mockResolvedValueOnce(buildScanContext({ requiresSelection: true, relatedTargets: ['WO-1', 'WO-2'].map(code => ({ objectType: 'WORK_ORDER', code, motherOrderCode: 'MO-1', requirement: '', quantity: null, quantityUnit: null })) })).mockResolvedValueOnce(buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-2' }));
    const wrapper = mount(OrderActionLocator, { props: { active: true, allowedTypes: ['MOTHER_ORDER', 'WORK_ORDER'] } });
    wrapper.getComponent(ScanInput).vm.$emit('recognize', { content: '运单' });
    await flushPromises();
    expect(wrapper.emitted('selected')).toBeUndefined();
    await wrapper.findAll('button')[1].trigger('click');
    await flushPromises();
    expect(queryScanContext).toHaveBeenLastCalledWith({ content: 'WO-2', objectType: 'WORK_ORDER', intent: 'LOOKUP' });
    expect(wrapper.emitted('selected')).toEqual([[{ objectType: 'WORK_ORDER', code: 'WO-2' }]]);
    wrapper.unmount();
  });
  it('收起后的迟到响应不能重新打开动作，不支持的对象明确阻断', async () => {
    let finish!: (value: ScanContextResponse) => void;
    vi.mocked(queryScanContext).mockReturnValueOnce(new Promise(resolve => { finish = resolve; })).mockResolvedValueOnce(buildScanContext({ objectType: 'EXPRESS_NO', code: 'SF-1' }));
    const wrapper = mount(OrderActionLocator, { props: { active: true, allowedTypes: ['WORK_ORDER'] } });
    wrapper.getComponent(ScanInput).vm.$emit('recognize', { content: 'WO-LATE' });
    await wrapper.setProps({ active: false });
    finish(buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-LATE' }));
    await flushPromises();
    expect(wrapper.emitted('selected')).toBeUndefined();
    await wrapper.setProps({ active: true });
    wrapper.getComponent(ScanInput).vm.$emit('recognize', { content: 'SF-1' });
    await flushPromises();
    expect(wrapper.text()).toContain('这类单据不能办理当前作业');
    expect(wrapper.emitted('selected')).toBeUndefined();
    wrapper.unmount();
  });
});
