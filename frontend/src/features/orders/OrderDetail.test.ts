import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderDetail from './OrderDetail.vue';
import OrderEvidence from './OrderEvidence.vue';
import { buildScanContext } from '../scan/scanContextFixture';
import { fetchHistoricalEvidencePage, fetchLinkedEvidencePage, type EvidenceLink, type HistoricalEvidence } from '../../services/evidenceApi';

vi.mock('../../services/evidenceApi', () => ({ fetchHistoricalEvidencePage: vi.fn(), fetchLinkedEvidencePage: vi.fn() }));
vi.mock('../evidence/EvidencePreviewLink.vue', () => ({ default: { props: ['attachmentCode'], template: '<span>{{ attachmentCode }}</span>' } }));

beforeEach(() => {
  vi.mocked(fetchLinkedEvidencePage).mockReset().mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
  vi.mocked(fetchHistoricalEvidencePage).mockReset().mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
});

describe('共享单据详情与附件', () => {
  it('要求按原来源显示，未填不补模板，流转保留原单号；附件按需读取', async () => {
    const context = buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-1', motherOrderCode: 'MO-1' });
    context.requirements.motherRequirement = '原母单快照';
    context.requirements.stationRequirements = [{ source: 'PROCESS', process: '抛光', text: '本岗要求' }];
    context.progress.handoffs = [{ process: '雕刻', operator: '师傅', ledgerCode: 'LED-1', createdAt: '2026-10-23' }];
    const wrapper = mount(OrderDetail, { props: { context } });
    expect(wrapper.text()).toContain('母单要求：原母单快照');
    expect(wrapper.text()).toContain('子单／本轮次要求：未填写');
    expect(wrapper.text()).toContain('岗位 抛光：本岗要求');
    expect(fetchLinkedEvidencePage).not.toHaveBeenCalled();
    await wrapper.get('.order-detail-tabs button:nth-child(2)').trigger('click');
    expect(wrapper.text()).toContain('LED-1');
    expect(wrapper.text()).toContain('尚无跨母子单聚合时间线');
    await wrapper.get('.order-detail-tabs button:nth-child(3)').trigger('click');
    await flushPromises();
    expect(fetchLinkedEvidencePage).toHaveBeenCalledWith({ targetType: 'WORK_ORDER', targetCode: 'WO-1', limit: 20 });
    expect(fetchHistoricalEvidencePage).toHaveBeenCalledWith({ targetType: 'WORK_ORDER', targetCode: 'WO-1', limit: 20 });
    wrapper.unmount();
  });

  it('两类附件独立失败与分页，保留原归属，不使用返回的裸预览地址', async () => {
    vi.mocked(fetchLinkedEvidencePage).mockResolvedValueOnce({ items: [{ linkCode: 'L1', targetCode: 'WO-1', attachmentCode: 'ATT-1', fileName: '原单照片', previewUrl: '不可信原地址' } as EvidenceLink], nextCursor: 18, hasMore: true });
    vi.mocked(fetchHistoricalEvidencePage).mockRejectedValueOnce(new Error('无权查看历史附件')).mockResolvedValueOnce({ items: [{ attachmentCode: 'OLD-1', businessCode: 'WO-1', fileName: '原历史照片' } as HistoricalEvidence], nextCursor: null, hasMore: false });
    const wrapper = mount(OrderEvidence, { props: { targetType: 'WORK_ORDER', targetCode: 'WO-1' } });
    await flushPromises();
    expect(wrapper.text()).toContain('原单照片');
    expect(wrapper.text()).toContain('无权查看历史附件');
    expect(wrapper.html()).not.toContain('不可信原地址');
    await wrapper.get('[role="alert"] button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('原历史照片');
    await wrapper.get('.secondary-button').trigger('click');
    await flushPromises();
    expect(fetchLinkedEvidencePage).toHaveBeenLastCalledWith({ targetType: 'WORK_ORDER', targetCode: 'WO-1', afterId: 18, limit: 20 });
    expect(wrapper.text()).toContain('原单照片');
    wrapper.unmount();
  });

  it('切单关闭旧附件视图，迟到响应不能显示在新单，分区回到概况', async () => {
    let resolve!: (value: { items: EvidenceLink[]; nextCursor: null; hasMore: false }) => void;
    vi.mocked(fetchLinkedEvidencePage).mockReturnValueOnce(new Promise(done => { resolve = done; }));
    const wrapper = mount(OrderDetail, { props: { context: buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-1' }) } });
    await wrapper.get('.order-detail-tabs button:nth-child(3)').trigger('click');
    await wrapper.setProps({ context: buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-2' }) });
    resolve({ items: [{ linkCode: 'L1', fileName: '旧客户私有文件' } as EvidenceLink], nextCursor: null, hasMore: false });
    await flushPromises();
    expect(wrapper.find('[aria-label="本单附件"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('旧客户私有文件');
    expect(wrapper.get('.order-detail-tabs button').attributes('aria-pressed')).toBe('true');
    wrapper.unmount();
  });
});
