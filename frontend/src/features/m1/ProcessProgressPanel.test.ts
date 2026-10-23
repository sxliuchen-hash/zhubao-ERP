import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import ProcessProgressPanel from './ProcessProgressPanel.vue';
import { ApiRequestError, apiGet, apiPost } from '../../services/apiClient';
import {
  fetchProcessProgress,
  fetchProcessProgressHistory,
  openProcessProgress,
  submitProcessProgress,
  type ProcessProgressTarget,
} from '../../services/processProgressApi';

const scanCamera = vi.hoisted(() => vi.fn());

vi.mock('../../services/apiClient', async () => ({ ...await vi.importActual<typeof import('../../services/apiClient')>('../../services/apiClient'), apiGet: vi.fn(), apiPost: vi.fn() }));
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
    vi.mocked(apiGet).mockResolvedValue([]);
    vi.mocked(fetchProcessProgressHistory).mockResolvedValue([]);
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

  it('改单申请未知后按原请求重试，成功后显式另开申请才换新编号', async () => {
    vi.mocked(openProcessProgress).mockResolvedValue(located('MD-CHANGE-1', 2, 30));
    const saved = { id: 'CHG-1', motherOrderCode: 'MD-SPLIT', workOrderCode: 'MD-CHANGE-1', status: 'DRAFT', versionNo: 1, expectedVersion: 0 };
    vi.mocked(apiPost).mockRejectedValueOnce(new Error('响应丢失')).mockResolvedValue(saved);
    const wrapper = mount(ProcessProgressPanel);
    await flushPromises();
    await wrapper.get('[aria-label="进度单号"]').setValue('MD-CHANGE-1');
    await wrapper.get('button[aria-label="定位"]').trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '打开改单办理')!.trigger('click');
    await wrapper.get('[aria-label="改单新要求"]').setValue('改为外圈刻字');
    await wrapper.get('[aria-label="改单新期限"]').setValue('2026-11-01');
    const save = () => wrapper.findAll('button').find((button) => button.text() === '保存改单申请')!;
    await save().trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '原改单请求重试')!.trigger('click');
    await flushPromises();
    const first = (vi.mocked(apiPost).mock.calls[0][1] as { requestId: string }).requestId;
    const second = (vi.mocked(apiPost).mock.calls[1][1] as { requestId: string }).requestId;
    expect(second).toBe(first);

    await wrapper.findAll('button').find(button => button.text() === '另开一份改单申请')!.trigger('click');
    await flushPromises();
    await wrapper.get('[aria-label="改单新要求"]').setValue('改为另一要求');
    vi.mocked(apiPost).mockResolvedValueOnce({ ...saved, id: 'CHG-2' });
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
      motherOrderCode: 'MD-SPLIT',
      workOrderCode: 'MD-CHANGE-1',
    };
    vi.mocked(openProcessProgress).mockResolvedValue(located('MD-CHANGE-1', 2, 30));
    vi.mocked(apiGet).mockResolvedValue([row]);
    vi.mocked(apiPost).mockImplementation(async (path) => ({ ...row, id: String(path).split('/')[4], status: String(path).includes('applications') ? 'APPLIED' : String(path).includes('rejections') ? 'REJECTED' : 'FINANCE_CONFIRMED' }));
    const wrapper = mount(ProcessProgressPanel, { global: { provide: { 'zhubao-auth-session': ref({ effectivePermissions: ['FINANCE_BILLING_POST'] }) } } });
    await flushPromises();
    await wrapper.get('[aria-label="进度单号"]').setValue('MD-CHANGE-1');
    await wrapper.get('button[aria-label="定位"]').trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '打开改单办理')!.trigger('click');
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

    vi.mocked(apiGet).mockResolvedValue([{ ...row, id: 'CHG-REJECT' }]);
    await wrapper.findAll('button').find(button => button.text() === '刷新改单')!.trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().startsWith('CHG-REJECT ·'))!.trigger('click');
    await wrapper.get('[aria-label="改单财务说明"]').setValue('价格不对');
    await wrapper.findAll('button').find((button) => button.text() === '拒绝改单')!.trigger('click');
    await flushPromises();
    expect(apiPost).toHaveBeenCalledWith(
      '/api/business/order-changes/CHG-REJECT/finance-rejections',
      expect.objectContaining({ note: '价格不对' }),
    );

    vi.mocked(apiGet).mockResolvedValue([{ ...row, id: 'CHG-2', status: 'FINANCE_CONFIRMED' }]);
    await wrapper.findAll('button').find(button => button.text() === '刷新改单')!.trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().includes('CHG-2'))!.trigger('click');
    await wrapper.findAll('button').find((button) => button.text() === '生效改单')!.trigger('click');
    await flushPromises();
    const applyCall = vi.mocked(apiPost).mock.calls.find((call) => String(call[0]).includes('/applications'));
    expect(applyCall?.[1]).toEqual(expect.objectContaining({ expectedVersion: 0 }));
    wrapper.unmount();
  });

  it('A 单迟到响应不能覆盖 B 单，改号后旧提交目标立即失效', async () => {
    let finish!: (value: ProcessProgressTarget) => void;
    vi.mocked(openProcessProgress).mockImplementation(code => code === 'A' ? new Promise(resolve => { finish = resolve; }) : Promise.resolve(located('B')));
    const wrapper = mount(ProcessProgressPanel);
    await wrapper.get('[aria-label="进度单号"]').setValue('A');
    await wrapper.get('[aria-label="定位"]').trigger('click');
    await wrapper.get('[aria-label="进度单号"]').setValue('B');
    await wrapper.get('[aria-label="定位"]').trigger('click');
    await flushPromises();
    finish(located('A'));
    await flushPromises();
    expect(wrapper.get('[aria-label="保存工序进度"]').text()).toContain('B ·');
    expect(wrapper.get('[aria-label="保存工序进度"]').text()).not.toContain('A ·');
    await wrapper.get('[aria-label="进度单号"]').setValue('C');
    expect(wrapper.find('[aria-label="保存工序进度"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('提交中只发一次，100% 保存后保留回执，刷新失败不允许再交一遍', async () => {
    let finish!: (value: Awaited<ReturnType<typeof submitProcessProgress>>) => void;
    vi.mocked(submitProcessProgress).mockReturnValue(new Promise(resolve => { finish = resolve; }));
    const wrapper = mount(ProcessProgressPanel, { props: { initialCode: 'ZD-1' } });
    await flushPromises();
    await wrapper.get('[aria-label="工序进度"]').setValue('100');
    await wrapper.get('[aria-label="保存工序进度"]').trigger('submit');
    await wrapper.get('[aria-label="保存工序进度"]').trigger('submit');
    expect(submitProcessProgress).toHaveBeenCalledOnce();
    vi.mocked(fetchProcessProgress).mockRejectedValueOnce(new Error('列表断网'));
    finish({ orderCode: 'ZD-1', processName: '雕刻', percent: 100, version: 5, note: '', operator: '雕刻工', updatedAt: '' });
    await flushPromises();
    expect(wrapper.text()).toContain('已记下 100%');
    expect(wrapper.text()).toContain('仍需正式交工');
    expect(wrapper.text()).toContain('列表断网');
    expect(wrapper.text()).toContain('进度版本 5');
    await wrapper.get('[aria-label="保存工序进度"]').trigger('submit');
    expect(submitProcessProgress).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it('结果未知时保留原内容、版本和请求，明确版本冲突不自动重交', async () => {
    vi.mocked(submitProcessProgress).mockRejectedValueOnce(new Error('网络超时')).mockRejectedValueOnce(new ApiRequestError('版本已变，请重新定位', 409, 40901));
    const wrapper = mount(ProcessProgressPanel, { props: { initialCode: 'ZD-1' } });
    await flushPromises();
    await wrapper.get('[aria-label="进度说明"]').setValue('原说明');
    await wrapper.get('[aria-label="保存工序进度"]').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('原请求结果待核对');
    const first = vi.mocked(submitProcessProgress).mock.calls[0][0];
    await wrapper.get('[aria-label="进度说明"]').setValue('程序改写的值');
    await wrapper.findAll('button').find(button => button.text() === '原请求重试')!.trigger('click');
    await flushPromises();
    expect(vi.mocked(submitProcessProgress).mock.calls[1][0]).toEqual(first);
    expect(wrapper.text()).toContain('版本已变');
    expect(wrapper.text()).not.toContain('原请求结果待核对');
    expect(submitProcessProgress).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('嵌入时只查询精确目标，不加载列表或重复扫码，不把轮次解析成原单', async () => {
    vi.mocked(openProcessProgress).mockResolvedValue(located('ZD-OLD'));
    const wrapper = mount(ProcessProgressPanel, { props: { embedded: true, initialTarget: { objectType: 'WORK_ORDER', code: 'FG-1' } } });
    await flushPromises();
    expect(openProcessProgress).toHaveBeenCalledWith('FG-1');
    expect(fetchProcessProgress).not.toHaveBeenCalled();
    expect(wrapper.find('[aria-label="进度单号"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('进度目标与当前单据或轮次不一致');
    expect(wrapper.find('[aria-label="保存工序进度"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('进度回退需要原因，普通岗位的独立改单中不展示财务确认字段', async () => {
    const wrapper = mount(ProcessProgressPanel, { props: { initialCode: 'ZD-1' } });
    await flushPromises();
    await wrapper.get('[aria-label="工序进度"]').setValue('30');
    await wrapper.get('[aria-label="保存工序进度"]').trigger('submit');
    expect(submitProcessProgress).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('进度回退必须填写原因');
    expect(wrapper.find('[aria-label="改单新要求"]').exists()).toBe(false);
    await wrapper.findAll('button').find(button => button.text() === '打开改单办理')!.trigger('click');
    expect(wrapper.find('[aria-label="改单新要求"]').exists()).toBe(true);
    expect(wrapper.find('[aria-label="改单客户单价"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
