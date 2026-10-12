import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../../services/apiClient';
import QualityPanel from './QualityPanel.vue';
import { queryScanContext, saveProgressRecord } from '../../services/scanContextApi';
import {
  lookupQualityCheck,
  openQualityRework,
  releaseQualityRound,
  reportQualityRework,
  submitQualityCheck,
  submitQualityRecheck,
} from '../../services/qualityCheckApi';
import { bindEvidence, uploadEvidenceFile } from '../../services/evidenceApi';
import { buildScanContext } from '../scan/scanContextFixture';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return {
    ...actual,
    queryScanContext: vi.fn(),
    saveProgressRecord: vi.fn(),
  };
});

vi.mock('../../services/qualityCheckApi', () => ({
  lookupQualityCheck: vi.fn(),
  openQualityRework: vi.fn(),
  submitQualityCheck: vi.fn(),
  reportQualityRework: vi.fn(),
  submitQualityRecheck: vi.fn(),
  releaseQualityRound: vi.fn(),
}));

vi.mock('../../services/evidenceApi', () => ({
  fetchEvidenceBoard: vi.fn().mockResolvedValue({ links: [], historical: [] }),
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));

function openView(submitted = false) {
  return {
    orderCode: 'ZD-100',
    motherOrderCode: 'MD-1',
    customerName: '恒信珠宝',
    dueDate: '2026-10-20',
    processingRequirement: '内壁刻字',
    ready: !submitted,
    blockReason: '',
    inspector: '质检员 周娜',
    qualityCaseCode: submitted ? 'QC-ZD-100' : '',
    outcome: submitted ? '不合格' : '',
    frozen: submitted,
    reworkOrderCode: '',
    lines: [{ lineNo: 1, clause: '内壁刻字', verdict: submitted ? '不合格' : '', reason: submitted ? '划伤' : '' }],
    roundCode: submitted ? 'QR-ZD-100-1' : '',
    roundNo: submitted ? 1 : 0,
    roundType: submitted ? 'INITIAL' : '',
    reworkReported: false,
    canRecheck: false,
    recheckBlock: submitted ? '请先开返工' : '',
    canRelease: false,
    releaseBlock: submitted ? '只有最新轮次合格或让步才能放行' : '',
    rounds: [],
  };
}

function fileOf(name: string) {
  const file = new File(['photo'], name, { type: 'image/jpeg' });
  Object.defineProperty(file, 'size', { value: 4 });
  return file;
}

async function openPanel() {
  vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
    objectType: 'WORK_ORDER',
    code: 'ZD-100',
    customerName: '恒信珠宝',
  }));
  vi.mocked(lookupQualityCheck).mockResolvedValue(openView());
  const wrapper = mount(QualityPanel, {
    props: { title: '对照加工要求', intro: '扫贴纸后质检' },
  });
  await wrapper.get('[aria-label="质检贴纸"]').setValue('JG|work_order|ZD-100');
  await wrapper.get('[aria-label="识别质检贴纸"]').trigger('click');
  await flushPromises();
  return wrapper;
}

describe('质检面板', () => {
  beforeEach(() => {
    vi.mocked(submitQualityCheck).mockReset();
    vi.mocked(lookupQualityCheck).mockReset();
    vi.mocked(openQualityRework).mockReset();
    vi.mocked(reportQualityRework).mockReset();
    vi.mocked(submitQualityRecheck).mockReset();
    vi.mocked(releaseQualityRound).mockReset();
    vi.mocked(uploadEvidenceFile).mockReset();
    vi.mocked(bindEvidence).mockReset();
    vi.mocked(saveProgressRecord).mockReset();
  });

  it('同一次确认复用请求编号，换判定后换新编号', async () => {
    const wrapper = await openPanel();
    const ids: string[] = [];
    vi.mocked(submitQualityCheck).mockImplementation(async (body) => {
      ids.push(body.requestId ?? '');
      throw new Error('上一轮还不能提交');
    });
    await wrapper.get('[aria-label="不合格 1"]').trigger('click');
    await wrapper.get('[aria-label="原因 1"]').setValue('划伤');
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('上一轮还不能提交');

    await wrapper.get('[aria-label="合格 1"]').trigger('click');
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();
    expect(ids[0]).toMatch(/^quality-/);
    expect(ids[1]).not.toBe(ids[0]);

    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();
    expect(ids[2]).toBe(ids[1]);
    expect(submitQualityCheck).toHaveBeenCalledTimes(3);
  });

  it('超时用原请求再查一次，不换新编号', async () => {
    const wrapper = await openPanel();
    const ids: string[] = [];
    vi.mocked(submitQualityCheck).mockImplementation(async (body) => {
      ids.push(body.requestId ?? '');
      if (ids.length === 1) throw new Error('后端接口不可用');
      return {
        qualityCaseCode: 'QC-ZD-100',
        orderCode: 'ZD-100',
        motherOrderCode: 'MD-1',
        inspector: '质检员 周娜',
        outcome: '不合格',
        frozen: true,
        ledgerCode: 'STK-1',
        reworkOrderCode: '',
        lines: [{ lineNo: 1, clause: '内壁刻字', verdict: '不合格', reason: '划伤' }],
        roundCode: 'QR-ZD-100-1',
        roundNo: 1,
        roundType: 'INITIAL',
      };
    });
    vi.mocked(lookupQualityCheck).mockResolvedValue(openView(true));
    await wrapper.get('[aria-label="不合格 1"]').trigger('click');
    await wrapper.get('[aria-label="原因 1"]').setValue('划伤');
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();
    expect(ids).toHaveLength(2);
    expect(ids[0]).toBe(ids[1]);
    expect(wrapper.text()).toContain('初检已提交');
  });

  it('返工报工后才能复检，放行复用原请求', async () => {
    const wrapper = await openPanel();
    vi.mocked(submitQualityCheck).mockResolvedValue({
      qualityCaseCode: 'QC-ZD-100',
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-1',
      inspector: '质检员 周娜',
      outcome: '不合格',
      frozen: true,
      ledgerCode: 'STK-1',
      reworkOrderCode: '',
      lines: [{ lineNo: 1, clause: '内壁刻字', verdict: '不合格', reason: '划伤' }],
      roundCode: 'QR-ZD-100-1',
      roundNo: 1,
      roundType: 'INITIAL',
    });
    vi.mocked(lookupQualityCheck).mockResolvedValue(openView(true));
    await wrapper.get('[aria-label="不合格 1"]').trigger('click');
    await wrapper.get('[aria-label="原因 1"]').setValue('划伤');
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('请先开返工');
    expect(submitQualityRecheck).not.toHaveBeenCalled();

    vi.mocked(openQualityRework).mockResolvedValue({
      reworkOrderCode: 'FG-ZD-100',
      qualityCaseCode: 'QC-ZD-100',
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-1',
      ledgerCode: 'STK-R',
      fromStockStatus: 'FROZEN',
      toStockStatus: 'REWORK',
    });
    vi.mocked(lookupQualityCheck).mockResolvedValue({
      ...openView(true),
      reworkOrderCode: 'FG-ZD-100',
      recheckBlock: '返工还没报工，不能复检',
    });
    await wrapper.get('[aria-label="开返工"]').trigger('click');
    await flushPromises();
    expect(openQualityRework).toHaveBeenCalledWith('ZD-100', expect.stringMatching(/^quality-/));

    vi.mocked(reportQualityRework).mockResolvedValue({ ledgerCode: 'STK-P', reworkOrderCode: 'FG-ZD-100' });
    vi.mocked(lookupQualityCheck).mockResolvedValue({
      ...openView(true),
      reworkOrderCode: 'FG-ZD-100',
      reworkReported: true,
      canRecheck: true,
      recheckBlock: '',
    });
    await wrapper.get('[aria-label="提交返工报工"]').trigger('click');
    await flushPromises();
    expect(reportQualityRework).toHaveBeenCalledTimes(1);

    vi.mocked(submitQualityRecheck).mockResolvedValue({
      qualityCaseCode: 'QC-ZD-100',
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-1',
      inspector: '质检员 周娜',
      outcome: '让步',
      frozen: false,
      ledgerCode: '',
      reworkOrderCode: 'FG-ZD-100',
      lines: [{ lineNo: 1, clause: '内壁刻字', verdict: '让步', reason: '客户接受' }],
      roundCode: 'QR-ZD-100-2',
      roundNo: 2,
      roundType: 'RECHECK',
    });
    vi.mocked(lookupQualityCheck).mockResolvedValue({
      ...openView(true),
      reworkOrderCode: 'FG-ZD-100',
      reworkReported: true,
      canRecheck: false,
      recheckBlock: '这一轮复检已经提交，不能覆盖',
      canRelease: true,
      releaseBlock: '',
      roundCode: 'QR-ZD-100-2',
      roundType: 'RECHECK',
    });
    await wrapper.get('[aria-label="复检让步 1"]').trigger('click');
    await wrapper.get('[aria-label="复检原因 1"]').setValue('客户接受');
    await wrapper.get('[aria-label="提交复检"]').trigger('click');
    await flushPromises();
    expect(submitQualityRecheck).toHaveBeenCalledWith(expect.objectContaining({
      code: 'ZD-100',
      requestId: expect.stringMatching(/^quality-/),
    }));

    const releaseIds: string[] = [];
    vi.mocked(releaseQualityRound).mockImplementation(async (_code, requestId) => {
      releaseIds.push(requestId ?? '');
      if (releaseIds.length === 1) throw new Error('后端接口不可用');
      return { ledgerCode: 'STK-OUT', releaseResult: '让步' };
    });
    await wrapper.get('[aria-label="放行"]').trigger('click');
    await flushPromises();
    expect(releaseIds).toHaveLength(2);
    expect(releaseIds[0]).toBe(releaseIds[1]);
    expect(wrapper.text()).toContain('已放行');
    expect(wrapper.text()).toContain('原不合格记录仍保留');
  });

  it('过程说明不质检，证据失败只重试文件', async () => {
    const wrapper = await openPanel();
    vi.mocked(saveProgressRecord).mockResolvedValue({
      code: 'PG-1',
      targetType: 'WORK_ORDER',
      targetCode: 'ZD-100',
      motherOrderCode: 'MD-1',
      stage: 'QUALITY',
      process: '',
      clauseNo: '',
      note: '先看一下',
      operatorId: 'u1',
      operatorName: '周娜',
      operatorRole: 'QUALITY_INSPECTOR',
      supplementary: false,
      recordedAt: '2026-10-12T10:00:00',
      requestId: 'req-note',
    });
    await wrapper.get('[aria-label="过程说明"]').setValue('先看一下');
    await wrapper.findAll('button').find((button) => button.text() === '保存过程说明')!.trigger('click');
    await flushPromises();
    expect(saveProgressRecord).toHaveBeenCalledTimes(1);
    expect(submitQualityCheck).not.toHaveBeenCalled();

    vi.mocked(submitQualityCheck).mockResolvedValue({
      qualityCaseCode: 'QC-ZD-100',
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-1',
      inspector: '质检员 周娜',
      outcome: '不合格',
      frozen: true,
      ledgerCode: 'STK-1',
      reworkOrderCode: '',
      lines: [{ lineNo: 1, clause: '内壁刻字', verdict: '不合格', reason: '划伤' }],
      roundCode: 'QR-ZD-100-1',
      roundNo: 1,
      roundType: 'INITIAL',
    });
    vi.mocked(lookupQualityCheck).mockResolvedValue(openView(true));
    await wrapper.get('[aria-label="不合格 1"]').trigger('click');
    await wrapper.get('[aria-label="原因 1"]').setValue('划伤');
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();
    expect(submitQualityCheck).toHaveBeenCalledTimes(1);

    vi.mocked(uploadEvidenceFile).mockRejectedValue(new Error('上传失败'));
    const input = wrapper.get('[aria-label="选择证据文件"]');
    Object.defineProperty(input.element, 'files', { configurable: true, value: [fileOf('check.jpg')] });
    await input.trigger('change');
    await wrapper.findAll('button').find((button) => button.text() === '上传并关联')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('上传失败');

    vi.mocked(uploadEvidenceFile).mockResolvedValue({ code: 'ATT-1', fileName: 'check.jpg' });
    vi.mocked(bindEvidence).mockResolvedValue({ linkCode: 'L1', attachmentCode: 'ATT-1', scene: 'CHECK' });
    await wrapper.findAll('button').find((button) => button.text() === '重试')!.trigger('click');
    await flushPromises();
    expect(uploadEvidenceFile).toHaveBeenCalledTimes(2);
    expect(submitQualityCheck).toHaveBeenCalledTimes(1);
    expect(bindEvidence).toHaveBeenCalledWith(expect.objectContaining({
      operationCode: 'QR-ZD-100-1',
      operationType: 'quality_round',
      scene: 'CHECK',
    }));
  });

  it('登录过期保留不合格原因', async () => {
    const wrapper = await openPanel();
    vi.mocked(submitQualityCheck).mockRejectedValue(new ApiRequestError('未登录', 401, 401));
    await wrapper.get('[aria-label="不合格 1"]').trigger('click');
    await wrapper.get('[aria-label="原因 1"]').setValue('划伤');
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('登录已过期。已填内容还在');
    expect((wrapper.get('[aria-label="原因 1"]').element as HTMLTextAreaElement).value).toBe('划伤');
    expect(wrapper.find('[aria-label="质检员姓名"]').exists()).toBe(false);
  });

  it('手机逐条判定，让步要原因，返工仍走原面板', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'WORK_ORDER',
      code: 'ZD-100',
      customerName: '恒信珠宝',
    }));
    vi.mocked(lookupQualityCheck).mockResolvedValue({
      ...openView(),
      lines: [
        { lineNo: 1, clause: '内壁刻字', verdict: '', reason: '' },
        { lineNo: 2, clause: '镜面抛光', verdict: '', reason: '' },
      ],
    });
    const wrapper = mount(QualityPanel, {
      props: { title: '手机质检', intro: '逐条判定', field: true },
    });
    await wrapper.get('[aria-label="质检贴纸"]').setValue('JG|work_order|ZD-100');
    await wrapper.get('[aria-label="识别质检贴纸"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('质检员 周娜');
    expect(wrapper.text()).toContain('质检员取当前登录账号');
    expect(wrapper.find('[aria-label="质检员姓名"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('内壁刻字');
    expect(wrapper.text()).not.toContain('镜面抛光');
    expect(wrapper.find('[aria-label="提交质检"]').exists()).toBe(false);

    await wrapper.get('[aria-label="不合格 1"]').trigger('click');
    expect((wrapper.get('[aria-label="下一条"]').element as HTMLButtonElement).disabled).toBe(true);
    expect(submitQualityCheck).not.toHaveBeenCalled();
    await wrapper.get('[aria-label="原因 1"]').setValue('划伤');
    expect((wrapper.get('[aria-label="下一条"]').element as HTMLButtonElement).disabled).toBe(false);
    await wrapper.get('[aria-label="下一条"]').trigger('click');

    expect(wrapper.text()).toContain('镜面抛光');
    await wrapper.get('[aria-label="让步 2"]').trigger('click');
    expect((wrapper.get('[aria-label="提交质检"]').element as HTMLButtonElement).disabled).toBe(true);
    await wrapper.get('[aria-label="原因 2"]').setValue('客户接受');
    expect((wrapper.get('[aria-label="提交质检"]').element as HTMLButtonElement).disabled).toBe(false);

    vi.mocked(submitQualityCheck).mockResolvedValue({
      qualityCaseCode: 'QC-ZD-100',
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-1',
      inspector: '质检员 周娜',
      outcome: '不合格',
      frozen: true,
      ledgerCode: 'STK-1',
      reworkOrderCode: '',
      lines: [
        { lineNo: 1, clause: '内壁刻字', verdict: '不合格', reason: '划伤' },
        { lineNo: 2, clause: '镜面抛光', verdict: '让步', reason: '客户接受' },
      ],
      roundCode: 'QR-ZD-100-1',
      roundNo: 1,
      roundType: 'INITIAL',
    });
    vi.mocked(lookupQualityCheck).mockResolvedValue({
      ...openView(true),
      lines: [
        { lineNo: 1, clause: '内壁刻字', verdict: '不合格', reason: '划伤' },
        { lineNo: 2, clause: '镜面抛光', verdict: '让步', reason: '客户接受' },
      ],
    });
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();
    expect(submitQualityCheck).toHaveBeenCalledWith(expect.objectContaining({
      lines: [
        { lineNo: 1, verdict: '不合格', reason: '划伤' },
        { lineNo: 2, verdict: '让步', reason: '客户接受' },
      ],
    }));
    expect(wrapper.find('[aria-label="开返工"]').exists()).toBe(true);
    expect(wrapper.find('[aria-label="提交返工报工"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="提交复检"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="放行"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('收款');
  });
});
