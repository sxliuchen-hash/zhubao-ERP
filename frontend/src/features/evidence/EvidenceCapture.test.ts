import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bindEvidence, fetchEvidenceBoard, uploadEvidenceFile } from '../../services/evidenceApi';
import { confirmInbound } from '../../services/businessOperationApi';
import EvidenceCapture from './EvidenceCapture.vue';

vi.mock('../../services/evidenceApi', () => ({
  fetchEvidenceBoard: vi.fn(),
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));

vi.mock('../../services/businessOperationApi', () => ({
  confirmInbound: vi.fn(),
  reportProduction: vi.fn(),
}));

const board = {
  links: [],
  historical: [{
    attachmentCode: 'ATT-OLD',
    businessCode: 'MD-1',
    objectType: '',
    fileName: 'old.jpg',
    mediaType: 'image/jpeg',
    checksum: 'sha256:old',
    status: '已上传',
    uploadedAt: '2026-10-01',
    label: '历史单据证据',
    previewUrl: '',
    fileState: 'HISTORICAL',
  }],
};

function fileOf(name: string, type: string, size = 4): File {
  const content = new Uint8Array(size);
  return new File([content], name, { type });
}

async function pick(wrapper: ReturnType<typeof mount>, name: string, type: string, size = 4) {
  const input = wrapper.get('[aria-label="选择证据文件"]');
  Object.defineProperty(input.element, 'files', { configurable: true, value: [fileOf(name, type, size)] });
  await input.trigger('change');
}

describe('现场证据采集', () => {
  beforeEach(() => {
    vi.mocked(fetchEvidenceBoard).mockReset();
    vi.mocked(uploadEvidenceFile).mockReset();
    vi.mocked(bindEvidence).mockReset();
    vi.mocked(confirmInbound).mockReset();
    vi.mocked(fetchEvidenceBoard).mockResolvedValue({ links: [], historical: [] });
    vi.mocked(uploadEvidenceFile).mockResolvedValue({
      code: 'ATT-1', documentCode: 'DOC-MD-1', fileName: 'a.jpg', mediaType: 'image/jpeg',
      size: 4, storageLocation: 'local://a', checksum: 'sha256:a', uploadedAt: '2026-10-12', previewUrl: '/preview',
    });
    vi.mocked(bindEvidence).mockResolvedValue({ linkCode: 'LNK-1', attachmentCode: 'ATT-1', scene: 'UNPACK' });
  });

  it('无单号时文件留在表单，建单后才上传，失败重试不重做业务', async () => {
    const wrapper = mount(EvidenceCapture, {
      props: { targetType: 'MOTHER_ORDER', targetCode: '', stage: 'INTAKE', scene: 'UNPACK', operationType: 'inbound_confirm', operationCode: '' },
    });
    await pick(wrapper, 'a.jpg', 'image/jpeg');
    expect(wrapper.text()).toContain('本地待上传');
    expect(wrapper.text()).toContain('尚无正式单号');
    await wrapper.get('li button').trigger('click');
    expect(uploadEvidenceFile).not.toHaveBeenCalled();

    await wrapper.setProps({ targetCode: 'MD-1', operationCode: 'STK-1' });
    vi.mocked(uploadEvidenceFile).mockRejectedValueOnce(new Error('网络中断'));
    await wrapper.get('li button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('失败');
    expect(confirmInbound).not.toHaveBeenCalled();

    await wrapper.get('li button').trigger('click');
    await flushPromises();
    expect(uploadEvidenceFile).toHaveBeenCalledTimes(2);
    expect(vi.mocked(uploadEvidenceFile).mock.calls[1][0].businessCode).toBe('MD-1');
    expect(bindEvidence).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('已关联');
    expect(confirmInbound).not.toHaveBeenCalled();
  });

  it('上传成功但关联失败时只重试关联', async () => {
    const wrapper = mount(EvidenceCapture, {
      props: {
        targetType: 'MOTHER_ORDER', targetCode: 'MD-1', stage: 'INTAKE', scene: 'UNPACK',
        operationType: 'inbound_confirm', operationCode: 'STK-1',
      },
    });
    await pick(wrapper, 'a.jpg', 'image/jpeg');
    vi.mocked(bindEvidence).mockRejectedValueOnce(new Error('关联失败'));
    await wrapper.get('li button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('已上传待关联');
    expect(wrapper.text()).toContain('证据待补');

    vi.mocked(bindEvidence).mockResolvedValueOnce({ linkCode: 'LNK-1', attachmentCode: 'ATT-1', scene: 'UNPACK' });
    await wrapper.get('li button').trigger('click');
    await flushPromises();
    expect(uploadEvidenceFile).toHaveBeenCalledTimes(1);
    expect(bindEvidence).toHaveBeenCalledTimes(2);
    expect(confirmInbound).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('已关联');
  });

  it('切单不把已选文件转挂到新单，取消选择不清草稿', async () => {
    const wrapper = mount(EvidenceCapture, {
      props: {
        targetType: 'MOTHER_ORDER', targetCode: 'MD-A', stage: 'INTAKE', scene: 'UNPACK',
        operationType: 'inbound_confirm', operationCode: 'STK-A',
      },
    });
    await pick(wrapper, 'a.jpg', 'image/jpeg');
    await wrapper.get('[aria-label="取消选择"]').trigger('click');
    expect(wrapper.text()).toContain('a.jpg');
    await wrapper.get('[aria-label="选择证据文件"]').trigger('cancel');
    expect(wrapper.text()).toContain('a.jpg');

    await wrapper.setProps({ targetCode: 'MD-B', operationCode: 'STK-B' });
    await wrapper.get('li button').trigger('click');
    await flushPromises();
    expect(uploadEvidenceFile).toHaveBeenCalledWith(expect.objectContaining({ businessCode: 'MD-A' }));
    expect(bindEvidence).toHaveBeenCalledWith(expect.objectContaining({ targetCode: 'MD-A', operationCode: 'STK-A' }));
    expect(wrapper.text()).toContain('不会转到新单');
  });

  it('拒绝过大或错误格式，并展示历史单据证据', async () => {
    vi.mocked(fetchEvidenceBoard).mockResolvedValue(board);
    const wrapper = mount(EvidenceCapture, {
      props: { targetType: 'MOTHER_ORDER', targetCode: 'MD-1', stage: 'INTAKE', scene: 'UNPACK' },
    });
    await flushPromises();
    expect(wrapper.text()).toContain('历史单据证据');
    expect(wrapper.text()).toContain('30 秒');
    expect(wrapper.text()).not.toContain('工序');

    await pick(wrapper, 'note.jpg', 'text/plain');
    expect(wrapper.text()).toContain('不能只改扩展名');
    expect(wrapper.findAll('li')).toHaveLength(1);

    await pick(wrapper, 'big.jpg', 'image/jpeg', 8 * 1024 * 1024 + 1);
    expect(wrapper.text()).toContain('超过 8MB');
    expect(uploadEvidenceFile).not.toHaveBeenCalled();
  });

  it('拍照和录像分别启动对应采集，录像仍按 50MB 限制上传关联', async () => {
    const wrapper = mount(EvidenceCapture, {
      props: {
        targetType: 'MOTHER_ORDER', targetCode: 'MD-1', stage: 'INTAKE', scene: 'UNPACK',
        operationType: 'inbound_confirm', operationCode: 'STK-1',
      },
    });
    const photoInput = wrapper.get('[aria-label="拍摄照片证据"]');
    const videoInput = wrapper.get('[aria-label="录制视频证据"]');
    const fileInput = wrapper.get('[aria-label="选择证据文件"]');
    expect(photoInput.attributes()).toMatchObject({ accept: 'image/jpeg,image/png', capture: 'environment' });
    expect(videoInput.attributes()).toMatchObject({ accept: 'video/mp4,video/webm', capture: 'environment' });
    expect(fileInput.attributes('accept')).toBe('image/jpeg,image/png,video/mp4,video/webm');
    expect(fileInput.attributes('capture')).toBeUndefined();

    const photoClick = vi.spyOn(photoInput.element as HTMLInputElement, 'click');
    const videoClick = vi.spyOn(videoInput.element as HTMLInputElement, 'click');
    await wrapper.get('[aria-label="拍照留证"]').trigger('click');
    expect(photoClick).toHaveBeenCalledOnce();
    expect(videoClick).not.toHaveBeenCalled();
    await wrapper.get('[aria-label="录视频留证"]').trigger('click');
    expect(videoClick).toHaveBeenCalledOnce();

    const video = fileOf('现场.mp4', 'video/mp4');
    Object.defineProperty(videoInput.element, 'files', { configurable: true, value: [video] });
    await videoInput.trigger('change');
    expect(wrapper.text()).toContain('现场.mp4');
    await wrapper.get('li button').trigger('click');
    await flushPromises();
    expect(uploadEvidenceFile).toHaveBeenCalledWith(expect.objectContaining({ file: video }));
    expect(bindEvidence).toHaveBeenCalledOnce();

    const oversized = fileOf('过大.mp4', 'video/mp4');
    Object.defineProperty(oversized, 'size', { value: 50 * 1024 * 1024 + 1 });
    Object.defineProperty(videoInput.element, 'files', { configurable: true, value: [oversized] });
    await videoInput.trigger('change');
    expect(wrapper.text()).toContain('视频超过 50MB');
    expect(uploadEvidenceFile).toHaveBeenCalledOnce();
  });

  it('同一文件可以再次选择，预览地址会在卸载时释放', async () => {
    const created: string[] = [];
    const revoked: string[] = [];
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: () => {
        const url = `blob:${created.length + 1}`;
        created.push(url);
        return url;
      },
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: (url: string) => {
        revoked.push(String(url));
      },
    });
    const wrapper = mount(EvidenceCapture, {
      props: { targetType: 'WORK_ORDER', targetCode: 'ZD-1', stage: 'PRODUCTION', scene: 'PROCESS' },
    });
    await pick(wrapper, 'a.jpg', 'image/jpeg');
    await pick(wrapper, 'a.jpg', 'image/jpeg');
    expect(wrapper.findAll('[data-state="LOCAL_PENDING"]')).toHaveLength(2);
    wrapper.unmount();
    expect(revoked).toEqual(created);
  });
});
