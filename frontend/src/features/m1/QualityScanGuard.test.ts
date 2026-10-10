import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import QualityWorkspace from './QualityWorkspace.vue';
import { fetchQualityWorkspace } from '../../services/businessWorkspaceApi';
import { lookupQualityCheck, openQualityRework, submitQualityCheck } from '../../services/qualityCheckApi';
import type { QualityCheckResult, QualityCheckView } from '../../services/qualityCheckApi';

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchQualityWorkspace: vi.fn() }));
vi.mock('../../services/businessOperationApi', () => ({
  approveLoss: vi.fn(), dispatchRework: vi.fn(), freezeQualityStock: vi.fn(),
  releaseQualityStock: vi.fn(), reportRework: vi.fn(),
}));
vi.mock('../../services/qualityCheckApi', () => ({
  lookupQualityCheck: vi.fn(), openQualityRework: vi.fn(), submitQualityCheck: vi.fn(),
}));

function view(orderCode: string): QualityCheckView {
  return {
    orderCode, motherOrderCode: 'MD-1', customerName: '客户', dueDate: '2026-10-20',
    processingRequirement: '镜面', ready: true, blockReason: '', inspector: '质检员',
    qualityCaseCode: '', outcome: '', frozen: false, reworkOrderCode: '',
    lines: [{ lineNo: 1, clause: `${orderCode}条款`, verdict: '', reason: '' }],
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchQualityWorkspace).mockResolvedValue({ metrics: [], cases: [], lossApprovals: [], dataSource: 'LIVE' });
});

describe('质检扫码不串单', () => {
  it('识别 A 后改成 B 会清空结果，晚到的旧响应不能占用按钮或覆盖新单', async () => {
    let resolveA: (value: QualityCheckView) => void = () => {};
    let resolveB: (value: QualityCheckView) => void = () => {};
    vi.mocked(lookupQualityCheck).mockImplementation((code: string) => new Promise((resolve) => {
      if (code.endsWith('A')) resolveA = resolve;
      else resolveB = resolve;
    }));
    const wrapper = mount(QualityWorkspace);
    await flushPromises();
    const scan = wrapper.get('[aria-label="质检贴纸"]');
    const recognize = () => wrapper.get('[aria-label="识别质检贴纸"]');

    await scan.setValue('JG|work_order|A');
    await recognize().trigger('click');
    expect((recognize().element as HTMLButtonElement).disabled).toBe(true);

    await scan.setValue('JG|work_order|B');
    expect((recognize().element as HTMLButtonElement).disabled).toBe(false);
    expect(wrapper.text()).toContain('贴纸已变化，请重新识别');
    expect(wrapper.text()).not.toContain('A条款');

    resolveA(view('A'));
    await flushPromises();
    expect(wrapper.text()).not.toContain('A条款');
    expect((recognize().element as HTMLButtonElement).disabled).toBe(false);

    await recognize().trigger('click');
    resolveB(view('B'));
    await flushPromises();
    expect(wrapper.text()).toContain('B条款');
    expect(wrapper.text()).not.toContain('A条款');
  });

  it('提交和返工使用成功识别的单号，旧响应晚到不写回新单', async () => {
    vi.mocked(lookupQualityCheck).mockImplementation(async (code: string) => view(code.endsWith('B') ? 'B' : 'A'));
    let resolveSubmit: (value: QualityCheckResult) => void = () => {};
    vi.mocked(submitQualityCheck).mockImplementation(() => new Promise<QualityCheckResult>((resolve) => { resolveSubmit = resolve; }));
    const wrapper = mount(QualityWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="质检贴纸"]').setValue('JG|work_order|A');
    await wrapper.get('[aria-label="识别质检贴纸"]').trigger('click');
    await flushPromises();
    await wrapper.get('[aria-label="合格 1"]').trigger('click');
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    expect(submitQualityCheck).toHaveBeenCalledWith(expect.objectContaining({ code: 'A' }));

    await wrapper.get('[aria-label="质检贴纸"]').setValue('JG|work_order|B');
    resolveSubmit({
      qualityCaseCode: 'QC-A', orderCode: 'A', motherOrderCode: 'MD-1', inspector: '质检员',
      outcome: '合格', frozen: false, ledgerCode: 'STK', reworkOrderCode: '', lines: [],
    });
    await flushPromises();
    expect(wrapper.text()).not.toContain('QC-A');

    await wrapper.get('[aria-label="识别质检贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('B条款');
    await wrapper.get('[aria-label="不合格 1"]').trigger('click');
    await wrapper.get('[aria-label="原因 1"]').setValue('划伤');
    vi.mocked(submitQualityCheck).mockResolvedValue({
      qualityCaseCode: 'QC-B', orderCode: 'B', motherOrderCode: 'MD-1', inspector: '质检员',
      outcome: '不合格', frozen: true, ledgerCode: 'STK-B', reworkOrderCode: '',
      lines: [{ lineNo: 1, clause: 'B条款', verdict: '不合格', reason: '划伤' }],
    });
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();
    vi.mocked(openQualityRework).mockResolvedValue({
      reworkOrderCode: 'FG-B', qualityCaseCode: 'QC-B', orderCode: 'B', motherOrderCode: 'MD-1',
      ledgerCode: 'STK-R', fromStockStatus: 'FROZEN', toStockStatus: 'REWORK',
    });
    await wrapper.get('[aria-label="开返工"]').trigger('click');
    await flushPromises();
    expect(openQualityRework).toHaveBeenCalledWith('B');
    expect(wrapper.text()).toContain('FG-B');
  });
});
