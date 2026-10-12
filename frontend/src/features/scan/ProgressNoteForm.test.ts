import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bindEvidence, uploadEvidenceFile } from '../../services/evidenceApi';
import { saveProgressRecord, type ScanProgressRecord } from '../../services/scanContextApi';
import ProgressNoteForm from './ProgressNoteForm.vue';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, saveProgressRecord: vi.fn() };
});

vi.mock('../../services/evidenceApi', () => ({
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));

vi.mock('../../services/businessOperationApi', () => ({
  reportProduction: vi.fn(),
  submitProcessStation: vi.fn(),
  shipFinishedStock: vi.fn(),
}));

const savedRecord: ScanProgressRecord = {
  code: 'PG-1',
  targetType: 'WORK_ORDER',
  targetCode: 'ZD-1',
  motherOrderCode: 'MD-1',
  stage: 'PRODUCTION',
  process: '雕刻',
  clauseNo: null,
  note: '第一刀',
  operatorId: 'op-雕刻',
  operatorName: '雕刻工',
  operatorRole: 'OPERATOR',
  supplementary: false,
  recordedAt: '2026-10-12 11:00:00',
  requestId: 'req-1',
};

function fileOf(): File {
  return new File([new Uint8Array([1, 2, 3])], 'note.jpg', { type: 'image/jpeg' });
}

describe('过程说明', () => {
  beforeEach(() => {
    vi.mocked(saveProgressRecord).mockReset();
    vi.mocked(uploadEvidenceFile).mockReset();
    vi.mocked(bindEvidence).mockReset();
    vi.mocked(saveProgressRecord).mockResolvedValue(savedRecord);
    vi.mocked(uploadEvidenceFile).mockResolvedValue({
      code: 'ATT-1', documentCode: 'DOC-ZD-1', fileName: 'note.jpg', mediaType: 'image/jpeg',
      size: 3, storageLocation: 'local://note.jpg', checksum: 'sha256:note', uploadedAt: '2026-10-12', previewUrl: '',
    });
    vi.mocked(bindEvidence).mockResolvedValue({ linkCode: 'LNK-1', attachmentCode: 'ATT-1', scene: 'PROCESS' });
  });

  it('发货准备只显示准备说明，不显示已发货', () => {
    const wrapper = mount(ProgressNoteForm, {
      props: { stage: 'SHIPPING', targetType: 'WORK_ORDER', targetCode: 'ZD-1', motherOrderCode: 'MD-1' },
    });
    expect(wrapper.text()).toContain('发货准备说明');
    expect(wrapper.text()).toContain('不会生成出货单');
    expect(wrapper.text()).toContain('扫码成功还不等于已经留证或已经报工');
    expect(wrapper.text()).not.toContain('已发货');
  });

  it('上传失败保留记录编号，重试证据不再保存过程说明', async () => {
    vi.mocked(uploadEvidenceFile).mockRejectedValue(new Error('网络中断'));
    const wrapper = mount(ProgressNoteForm, {
      props: {
        stage: 'PRODUCTION',
        targetType: 'WORK_ORDER',
        targetCode: 'ZD-1',
        motherOrderCode: 'MD-1',
        processOptions: ['雕刻'],
      },
    });
    await wrapper.get('[aria-label="过程说明"]').setValue('第一刀');
    const input = wrapper.get('[aria-label="选择过程证据"]');
    Object.defineProperty(input.element, 'files', { configurable: true, value: [fileOf()] });
    await input.trigger('change');
    await wrapper.get('button').trigger('click');
    await flushPromises();

    expect(saveProgressRecord).toHaveBeenCalledTimes(1);
    expect(vi.mocked(saveProgressRecord).mock.calls[0][0]).toMatchObject({
      targetCode: 'ZD-1',
      stage: 'PRODUCTION',
      process: '雕刻',
      note: '第一刀',
    });
    expect(vi.mocked(saveProgressRecord).mock.calls[0][0]).not.toHaveProperty('operatorName');
    expect(wrapper.text()).toContain('过程已保存，证据待补');
    expect(wrapper.text()).toContain('PG-1');
    expect(bindEvidence).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="重试证据"]').trigger('click');
    await flushPromises();
    expect(saveProgressRecord).toHaveBeenCalledTimes(1);
    expect(uploadEvidenceFile).toHaveBeenCalledTimes(2);
  });
});
