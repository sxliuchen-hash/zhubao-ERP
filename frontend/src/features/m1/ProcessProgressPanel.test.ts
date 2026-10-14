import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProcessProgressPanel from './ProcessProgressPanel.vue';
import { apiPost } from '../../services/apiClient';
import {
  fetchProcessProgress,
  openProcessProgress,
  submitProcessProgress,
  type ProcessProgressTarget,
} from '../../services/processProgressApi';

const scanCamera = vi.hoisted(() => vi.fn());

vi.mock('../../services/apiClient', () => ({ apiGet: vi.fn(), apiPost: vi.fn() }));
vi.mock('../../services/processProgressApi', () => ({
  fetchProcessProgress: vi.fn(),
  fetchProcessProgressHistory: vi.fn(),
  openProcessProgress: vi.fn(),
  submitProcessProgress: vi.fn(),
}));
vi.mock('../scan/cameraBarcode', () => ({ hasLiveCamera: () => true }));
vi.mock('../scan/useScanner', async () => {
  const { ref: vueRef } = await import('vue');
  return {
    useScanner: () => ({
      scanning: vueRef(false),
      cameraActive: vueRef(false),
      hint: vueRef(''),
      videoRef: vueRef(null),
      stop: vi.fn(),
      scanCamera,
      scanPhoto: vi.fn(),
    }),
  };
});

function located(code: string, version = 4, percent = 60): ProcessProgressTarget {
  return {
    selectChild: false,
    orderCode: code,
    motherOrderCode: 'MD-SPLIT',
    customerName: '恒信珠宝',
    processingRequirement: '内壁刻字',
    dueDate: '2026-11-01',
    currentProcess: '雕刻',
    formallyDelivered: false,
    percent,
    version,
    children: [],
  };
}

describe('ProcessProgressPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchProcessProgress).mockResolvedValue([{
      orderCode: 'ZD-1',
      motherOrderCode: 'MD-SPLIT',
      customerName: '恒信珠宝',
      currentProcess: '雕刻',
      businessStatus: '待正式交付',
      percent: 0,
      version: 9,
      operator: '',
      updatedAt: '',
      dueDate: '2026-11-01',
    }]);
    vi.mocked(openProcessProgress).mockImplementation(async (code: string) => {
      if (code === 'MD-SPLIT') {
        return {
          selectChild: true,
          orderCode: '',
          motherOrderCode: 'MD-SPLIT',
          customerName: '恒信珠宝',
          processingRequirement: '',
          dueDate: '',
          currentProcess: '',
          formallyDelivered: false,
          percent: 0,
          version: 0,
          children: [{ code: 'ZD-1', customerName: '恒信珠宝', processes: '雕刻' }],
        };
      }
      return located(code);
    });
    vi.mocked(submitProcessProgress).mockImplementation(async (body) => ({
      orderCode: body.code,
      processName: '雕刻',
      percent: body.percent,
      version: body.expectedVersion + 1,
      note: body.note,
      operator: '雕刻工',
      updatedAt: '2026-10-14T08:00:00',
    }));
    scanCamera.mockResolvedValue('JG|mother|MD-CAM-1');
  });

  it('手输定位、相机识别和选择子单都查询目标，提交使用定位返回的版本', async () => {
    const wrapper = mount(ProcessProgressPanel);
    await flushPromises();

    await wrapper.get('[aria-label="进度单号"]').setValue('MD-HAND-1');
    await wrapper.get('button[aria-label="定位"]').trigger('click');
    await flushPromises();
    expect(openProcessProgress).toHaveBeenCalledWith('MD-HAND-1');

    await wrapper.get('button[aria-label="打开摄像头"]').trigger('click');
    await flushPromises();
    expect(scanCamera).toHaveBeenCalled();
    expect(openProcessProgress).toHaveBeenCalledWith('JG|mother|MD-CAM-1');

    await wrapper.get('[aria-label="进度单号"]').setValue('MD-SPLIT');
    await wrapper.get('button[aria-label="定位"]').trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === 'ZD-1')!.trigger('click');
    await flushPromises();
    expect(openProcessProgress).toHaveBeenCalledWith('ZD-1');
    expect((wrapper.get('[aria-label="工序进度"]').element as HTMLSelectElement).value).toBe('60');

    const progressForm = wrapper.findAll('form').find((form) => form.text().includes('提交进度'));
    await progressForm!.trigger('submit');
    await flushPromises();
    expect(submitProcessProgress).toHaveBeenCalledWith(expect.objectContaining({
      code: 'ZD-1',
      percent: 60,
      expectedVersion: 4,
    }));
    expect(vi.mocked(submitProcessProgress).mock.calls[0][0].expectedVersion).not.toBe(9);
    wrapper.unmount();
  });

  it('改单申请失败后重试复用请求编号，要求变化才换新编号', async () => {
    vi.mocked(openProcessProgress).mockResolvedValue(located('MD-CHANGE-1', 2, 30));
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('响应丢失')).mockResolvedValue({ id: 'CHG-1' });
    const wrapper = mount(ProcessProgressPanel);
    await flushPromises();
    await wrapper.get('[aria-label="进度单号"]').setValue('MD-CHANGE-1');
    await wrapper.get('button[aria-label="定位"]').trigger('click');
    await flushPromises();
    await wrapper.get('[aria-label="改单新要求"]').setValue('改为外圈刻字');
    await wrapper.get('[aria-label="改单新期限"]').setValue('2026-11-01');
    const save = () => wrapper.findAll('button').find((button) => button.text() === '保存改单申请')!;
    await save().trigger('click');
    await flushPromises();
    await save().trigger('click');
    await flushPromises();
    const first = (vi.mocked(apiPost).mock.calls[0][1] as { requestId: string }).requestId;
    const second = (vi.mocked(apiPost).mock.calls[1][1] as { requestId: string }).requestId;
    expect(second).toBe(first);

    await wrapper.get('[aria-label="改单新要求"]').setValue('改为另一要求');
    vi.mocked(apiPost).mockResolvedValueOnce({ id: 'CHG-2' });
    await save().trigger('click');
    await flushPromises();
    const third = (vi.mocked(apiPost).mock.calls[2][1] as { requestId: string }).requestId;
    expect(third).not.toBe(first);
    wrapper.unmount();
  });
});
