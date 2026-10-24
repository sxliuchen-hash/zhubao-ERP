import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adoptStorageEdition, fetchMotherLabel, fetchWorkOrderLabel, reprintLabel, type LabelPayload } from '../../services/labelApi';
import { ref } from 'vue';
import { queryScanContext } from '../../services/scanContextApi';
import { buildScanContext } from '../scan/scanContextFixture';
import OrderLabelPanel from './OrderLabelPanel.vue';

vi.mock('../../services/labelApi', async () => ({ ...await vi.importActual<typeof import('../../services/labelApi')>('../../services/labelApi'), adoptStorageEdition: vi.fn(), fetchMotherLabel: vi.fn(), fetchWorkOrderLabel: vi.fn(), reprintLabel: vi.fn() }));
vi.mock('../../services/scanContextApi', async () => ({ ...await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi'), queryScanContext: vi.fn() }));
vi.mock('./PrintJobPanel.vue', () => ({ default: { props: ['payload'], template: '<div data-print-jobs />' } }));
const payload: LabelPayload = { type: 'WORK_ORDER', typeLabel: '子工单', primaryCode: 'WO-1', qrContent: 'JG|work_order|WO-1|1|LI1', fields: [], footerLeft: '', footerRight: '', printedAt: '', brandTitle: '珠宝', templateCode: 'FLOW', printerSetting: { widthMm: 40, heightMm: 30, dpi: 203, printerName: '', copies: 1 } };
const wrappers: ReturnType<typeof mount<typeof OrderLabelPanel>>[] = [];
function panel(code = 'WO-1') { const wrapper = mount(OrderLabelPanel, { props: { initialTarget: { objectType: 'WORK_ORDER', code } } }); wrappers.push(wrapper); return wrapper; }
beforeEach(() => {
  vi.mocked(queryScanContext).mockReset().mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-1' }));
  vi.mocked(fetchWorkOrderLabel).mockReset().mockResolvedValue(payload);
  vi.mocked(fetchMotherLabel).mockReset(); vi.mocked(reprintLabel).mockReset();
});
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()));
describe('精确单据标签', () => {
  it('保存要求换贴超时后保留原请求和快照再核对', async () => {
    vi.mocked(adoptStorageEdition).mockRejectedValueOnce(new Error('网络超时')).mockResolvedValueOnce(1);
    const wrapper = mount(OrderLabelPanel, { props: { externalPayload: payload }, global: { provide: { 'zhubao-auth-session': ref({ role: 'RECEIVER' }) } } }); wrappers.push(wrapper);
    await wrapper.setProps({ externalPayload: { ...payload } }); await flushPromises();
    await wrapper.get('[aria-label="保存要求换贴原因"]').setValue('核对后更新保存要求');
    const submit = () => wrapper.findAll('button').find(button => button.text().includes('确认采用当前') || button.text().includes('用原请求核对'))!;
    await submit().trigger('click'); await flushPromises();
    const first = vi.mocked(adoptStorageEdition).mock.calls[0];
    await submit().trigger('click'); await flushPromises();
    expect(vi.mocked(adoptStorageEdition).mock.calls[1]).toEqual(first);
  });

  it('执行轮次解析为原子单时拦截，不能打印原单代替轮次', async () => {
    const wrapper = panel('ROUND-2'); await flushPromises();
    expect(wrapper.text()).toContain('执行轮次不一致');
    expect(fetchWorkOrderLabel).not.toHaveBeenCalled();
    expect(wrapper.find('[data-print-jobs]').exists()).toBe(false);
  });
  it('标签接口返回另一单或另一类型时拦截预览和打印', async () => {
    vi.mocked(fetchWorkOrderLabel).mockResolvedValue({ ...payload, type: 'MOTHER', primaryCode: 'MO-1' });
    const wrapper = panel(); await flushPromises();
    expect(wrapper.text()).toContain('类型或单号与本单不一致');
    expect(wrapper.find('[aria-label="标签预览"]').exists()).toBe(false);
  });
  it('补打审计超时保留原单和原因，不再次记录也不提交打印任务', async () => {
    vi.mocked(reprintLabel).mockRejectedValue(new Error('超时'));
    const wrapper = panel(); await flushPromises();
    await wrapper.get('[aria-label="补打原因"]').setValue('原贴纸丢失');
    const button = wrapper.findAll('button').find(item => item.text() === '补打并记录')!;
    await button.trigger('click'); await flushPromises(); await button.trigger('click');
    expect(reprintLabel).toHaveBeenCalledTimes(1);
    expect(reprintLabel).toHaveBeenCalledWith({ type: 'WORK_ORDER', primaryCode: 'WO-1', reason: '原贴纸丢失' });
    expect(wrapper.get('[aria-label="补打原因"]').element).toHaveProperty('value', '原贴纸丢失');
    expect(wrapper.text()).toContain('补打记录结果待核对');
    expect(wrapper.text()).toContain('尚未提交新的打印任务');
  });
  it('独立标签工具解析后交给共享原单容器，不在工具页创建易丢失的打印草稿', async () => {
    const open = vi.fn();
    const wrapper = mount(OrderLabelPanel, { global: { provide: { 'zhubao-open-label-target': open } } }); wrappers.push(wrapper);
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('WO-1');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click'); await flushPromises();
    expect(open).toHaveBeenCalledWith({ objectType: 'WORK_ORDER', code: 'WO-1' });
    expect(fetchWorkOrderLabel).not.toHaveBeenCalled();
  });
});
