import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProcessProgressPanel from './ProcessProgressPanel.vue';
import { apiGet, apiPost } from '../../services/apiClient';
import {
  fetchProcessProgress,
  openProcessProgress,
  submitProcessProgress,
  type ProcessProgressTarget,
} from '../../services/processProgressApi';

const scanCamera = vi.hoisted(() => vi.fn());

vi.mock('../../services/apiClient', () => ({ apiGet: vi.fn(), apiPost: vi.fn() }));
vi.mock('../../services/evidenceApi', () => ({
  fetchEvidenceBoard: vi.fn().mockResolvedValue({ links: [], historical: [] }),
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));
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

  it('改单详情可以确认金额、拒绝和按基准版本生效', async () => {
    const row = {
      id: 'CHG-1',
      status: 'PENDING_FINANCE',
      versionNo: 1,
      expectedVersion: 0,
      oldRequirement: '内壁刻字',
      newRequirement: '外圈刻字',
      oldDueDate: '2026-10-20',
      newDueDate: '2026-12-01',
      oldRoute: '雕刻、镶嵌、抛光',
      newRoute: '雕刻、镶嵌',
      reworkProcess: '雕刻',
      amountUnchanged: false,
      customerUnitPrice: null,
      workerFee: null,
      customerDelta: null,
      financeNote: '',
      motherOrderCode: 'MD-CHANGE-1',
    };
    vi.mocked(openProcessProgress).mockResolvedValue(located('MD-CHANGE-1', 2, 30));
    vi.mocked(apiGet).mockResolvedValue([row]);
    vi.mocked(apiPost).mockImplementation(async (path) => ({ ...row, status: String(path).includes('applications') ? 'APPLIED' : 'FINANCE_CONFIRMED' }));
    const wrapper = mount(ProcessProgressPanel);
    await flushPromises();
    await wrapper.get('[aria-label="进度单号"]').setValue('MD-CHANGE-1');
    await wrapper.get('button[aria-label="定位"]').trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '刷新改单')!.trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text().includes('CHG-1'))!.trigger('click');
    expect(wrapper.get('[aria-label="改单详情"]').text()).toContain('内壁刻字');
    expect(wrapper.get('[aria-label="改单详情"]').text()).toContain('外圈刻字');
    expect(wrapper.get('[aria-label="改单详情"]').text()).toContain('基准版本 0');

    await wrapper.findAll('button').find((button) => button.text() === '确认金额不变')!.trigger('click');
    await flushPromises();
    expect(apiPost).toHaveBeenCalledWith(
      '/api/business/order-changes/CHG-1/finance-confirmations',
      expect.objectContaining({ amountUnchanged: true }),
    );

    await wrapper.get('[aria-label="改单财务说明"]').setValue('价格不对');
    await wrapper.findAll('button').find((button) => button.text() === '拒绝改单')!.trigger('click');
    await flushPromises();
    expect(apiPost).toHaveBeenCalledWith(
      '/api/business/order-changes/CHG-1/finance-rejections',
      expect.objectContaining({ note: '价格不对' }),
    );

    await wrapper.findAll('button').find((button) => button.text() === '生效改单')!.trigger('click');
    await flushPromises();
    const applyCall = vi.mocked(apiPost).mock.calls.find((call) => String(call[0]).includes('/applications'));
    expect(applyCall?.[1]).toEqual(expect.objectContaining({ expectedVersion: 0 }));
    wrapper.unmount();
  });
});
