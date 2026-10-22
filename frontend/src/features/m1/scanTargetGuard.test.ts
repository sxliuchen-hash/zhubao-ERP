import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { queryScanContext } from '../../services/scanContextApi';
import { hasLiveCamera, openCameraStream, readLiveBarcode } from '../scan/cameraBarcode';
import { buildScanContext } from '../scan/scanContextFixture';
import MobileOpsWorkspace from './MobileOpsWorkspace.vue';
import QualityWorkspace from './QualityWorkspace.vue';
import LabelWorkspace from './LabelWorkspace.vue';
import { fetchQualityWorkspace } from '../../services/businessWorkspaceApi';
import { submitMobileIntake } from '../../services/mobileIntakeApi';
import { lookupProcessStation, submitProcessStation } from '../../services/processStationApi';
import { lookupQualityCheck, submitQualityCheck } from '../../services/qualityCheckApi';
import { fetchCustomers, fetchMaterials, fetchShortcuts } from '../../services/masterDataApi';

function mountMobileOps() {
  return mount(MobileOpsWorkspace, {
    global: { provide: { 'zhubao-auth-session': ref({ role: 'ADMIN' } as AuthSession) } },
  });
}

async function openMobileAction(wrapper: ReturnType<typeof mountMobileOps>, label: string) {
  const button = wrapper.findAll('.mobile-order-shortcuts button').find((item) => item.text() === label);
  expect(button).toBeTruthy();
  await button!.trigger('click');
}

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn() };
});

vi.mock('../scan/cameraBarcode', () => ({
  hasLiveCamera: vi.fn(() => false),
  openCameraStream: vi.fn(),
  readLiveBarcode: vi.fn(),
  readBarcodeFromFile: vi.fn(),
}));

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchQualityWorkspace: vi.fn() }));
vi.mock('../../services/businessOperationApi', () => ({
  approveLoss: vi.fn(), dispatchRework: vi.fn(), freezeQualityStock: vi.fn(),
  releaseQualityStock: vi.fn(), reportRework: vi.fn(),
}));
vi.mock('../../services/mobileIntakeApi', () => ({
  lookupMobileIntake: vi.fn(),
  submitMobileIntake: vi.fn(),
  fetchMotherOrder: vi.fn(),
}));
vi.mock('../../services/processStationApi', () => ({
  lookupProcessStation: vi.fn(),
  submitProcessStation: vi.fn(),
}));
vi.mock('../../services/masterDataApi', () => ({
  fetchCustomers: vi.fn(), fetchMaterials: vi.fn(), fetchShortcuts: vi.fn(), fetchTemplates: vi.fn(),
  saveCustomer: vi.fn(), saveMaterial: vi.fn(), saveShortcut: vi.fn(), removeShortcut: vi.fn(),
}));
vi.mock('../../services/evidenceApi', () => ({
  fetchEvidenceBoard: vi.fn().mockResolvedValue({ links: [], historical: [] }),
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));
vi.mock('../../services/qualityCheckApi', () => ({
  lookupQualityCheck: vi.fn(),
  openQualityRework: vi.fn(),
  submitQualityCheck: vi.fn(),
  reportQualityRework: vi.fn(),
  submitQualityRecheck: vi.fn(),
  releaseQualityRound: vi.fn(),
}));
vi.mock('../../services/documentCaptureApi', () => ({
  fetchCaptures: vi.fn().mockResolvedValue([]),
  uploadCapture: vi.fn(),
}));

function stationView(orderCode: string) {
  return {
    orderCode,
    customerName: '恒信珠宝',
    dueDate: '2026-10-20',
    processingRequirement: '内壁刻字',
    stations: [{
      processName: '雕刻',
      requirement: '内壁刻字',
      dueDate: '2026-10-20',
      previousProcessName: '',
      previousSubmitted: true,
      submitted: false,
    }],
    message: '',
  };
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(hasLiveCamera).mockReturnValue(false);
  vi.mocked(fetchCustomers).mockResolvedValue([{ customerId: 1001, customerCode: 'C-1001', name: '恒信珠宝',
    aliases: '', contact: '', phone: '', remark: '', enabled: true }]);
  vi.mocked(fetchMaterials).mockResolvedValue([{ materialCode: 'AU9999', name: '足金', aliases: '', remark: '', enabled: true }]);
  vi.mocked(fetchShortcuts).mockResolvedValue([]);
  vi.mocked(fetchQualityWorkspace).mockResolvedValue({
    metrics: [],
    cases: [{
      code: 'QC-DEFAULT',
      workOrder: 'ZD-DEFAULT',
      process: '抛光',
      inspector: '周娜',
      defect: '划痕',
      action: '待质检',
      status: '待质检',
    }],
    lossApprovals: [],
    dataSource: 'LIVE',
  });
  HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
});

describe('现场入口防串单', () => {
  it('岗位报工提交使用查询返回的规范单号，输入变化后不能再提交', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'WORK_ORDER',
      code: 'ZD-100',
      customerName: '恒信珠宝',
    }));
    vi.mocked(lookupProcessStation).mockResolvedValue(stationView('ZD-100'));
    const wrapper = mountMobileOps();
    await openMobileAction(wrapper, '岗位报工');
    await wrapper.get('[aria-label="贴纸二维码"]').setValue('  JG|work_order|ZD-100  ');
    await wrapper.get('[aria-label="识别贴纸"]').trigger('click');
    await flushPromises();

    expect(queryScanContext).toHaveBeenCalledWith({
      content: 'JG|work_order|ZD-100',
      intent: 'PRODUCTION',
    });
    expect(lookupProcessStation).toHaveBeenCalledWith('ZD-100');
    expect(submitProcessStation).not.toHaveBeenCalled();
    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    await wrapper.get('[aria-label="完成核对"]').trigger('click');
    expect(submitProcessStation).not.toHaveBeenCalled();
    await wrapper.get('button').element;
    const submit = wrapper.findAll('button').find((button) => button.text() === '提交雕刻');
    expect(submit).toBeTruthy();
    await submit!.trigger('click');
    expect(submitProcessStation).toHaveBeenCalledWith('ZD-100', '雕刻', expect.stringMatching(/^station-/));

    await wrapper.get('[aria-label="贴纸二维码"]').setValue('JG|work_order|ZD-200');
    expect(wrapper.text()).toContain('贴纸已变化，请重新识别');
    expect(wrapper.findAll('button').some((button) => button.text() === '提交雕刻')).toBe(false);
    expect(submitProcessStation).toHaveBeenCalledTimes(1);
  });

  it('已拆母单在选中子单前不报工，并且不使用第一张子单', async () => {
    vi.mocked(queryScanContext).mockResolvedValueOnce(buildScanContext({
      objectType: 'MOTHER_ORDER',
      code: 'MD-1',
      requiresSelection: true,
      relatedTargets: [
        { objectType: 'WORK_ORDER', code: 'ZD-1', motherOrderCode: 'MD-1', requirement: '甲', quantity: 1, quantityUnit: '件' },
        { objectType: 'WORK_ORDER', code: 'ZD-2', motherOrderCode: 'MD-1', requirement: '乙', quantity: 1, quantityUnit: '件' },
      ],
    }));
    const wrapper = mountMobileOps();
    await openMobileAction(wrapper, '岗位报工');
    await wrapper.get('[aria-label="贴纸二维码"]').setValue('JG|mother|MD-1');
    await wrapper.get('[aria-label="识别贴纸"]').trigger('click');
    await flushPromises();

    expect(lookupProcessStation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请选择子单');
    expect(wrapper.find('[aria-label="完成核对"]').exists()).toBe(false);
    expect(wrapper.findAll('button').some((button) => button.text().startsWith('提交'))).toBe(false);

    vi.mocked(queryScanContext).mockResolvedValueOnce(buildScanContext({
      objectType: 'WORK_ORDER',
      code: 'ZD-2',
      customerName: '恒信珠宝',
    }));
    vi.mocked(lookupProcessStation).mockResolvedValue(stationView('ZD-2'));
    await wrapper.get('[aria-label="选择ZD-2"]').trigger('click');
    await flushPromises();
    expect(queryScanContext).toHaveBeenLastCalledWith({
      content: 'ZD-2',
      objectType: 'WORK_ORDER',
      intent: 'PRODUCTION',
    });
    expect(lookupProcessStation).toHaveBeenCalledWith('ZD-2');
    expect(lookupProcessStation).not.toHaveBeenCalledWith('ZD-1');
    expect(wrapper.find('[aria-label="完成核对"]').exists()).toBe(true);
    expect(wrapper.findAll('button').some((button) => button.text().startsWith('提交'))).toBe(false);
  });

  it('收货失败不会打开新建母单，成功时用规范化运单号而不是原始输入', async () => {
    vi.mocked(queryScanContext).mockRejectedValueOnce(new Error('未找到对应单据'));
    const wrapper = mountMobileOps();
    await wrapper.get('.mobile-order-create').trigger('click');
    await wrapper.get('[aria-label="快递单号"]').setValue('CC20261012001');
    await wrapper.get('[aria-label="识别单号"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('未找到对应单据');
    expect(wrapper.text()).not.toContain('新建母单');
    expect(submitMobileIntake).not.toHaveBeenCalled();

    vi.mocked(queryScanContext).mockResolvedValueOnce(buildScanContext({
      objectType: 'EXPRESS_NO',
      code: 'SF1048329001',
      motherOrderCode: null,
      actions: [{ code: 'INTAKE', allowed: true, blockKind: 'NONE', blockReason: null }],
    }));
    await wrapper.get('[aria-label="快递单号"]').setValue('sf 1048329001');
    await wrapper.get('[aria-label="识别单号"]').trigger('click');
    await flushPromises();
    expect(queryScanContext).toHaveBeenLastCalledWith({ content: 'sf 1048329001', intent: 'INTAKE' });
    expect(wrapper.text()).toContain('运单号 SF1048329001');
    await wrapper.get('[aria-label="客户"]').trigger('focus');
    await wrapper.get('[aria-label="客户选项"]').findAll('[role="option"]')[0].trigger('click');
    await wrapper.get('[aria-label="材质"]').trigger('focus');
    await wrapper.get('[aria-label="材质选项"]').findAll('[role="option"]')[0].trigger('click');
    await wrapper.get('[aria-label="加工要求"]').setValue('抛光');
    await wrapper.get('[aria-label="完成期限"]').setValue('2026-10-20');
    await wrapper.get('[aria-label="货品名称"]').setValue('戒托');
    await wrapper.get('[aria-label="数量"]').setValue('1');
    await wrapper.get('[aria-label="重量"]').setValue('2.5');
    await wrapper.get('button').element;
    const create = wrapper.findAll('button').find((button) => button.text() === '生成母单');
    await create!.trigger('click');
    await flushPromises();
    expect(submitMobileIntake).toHaveBeenCalledWith(expect.objectContaining({ courierNo: 'SF1048329001' }));
    expect(submitMobileIntake).not.toHaveBeenCalledWith(expect.objectContaining({ courierNo: 'sf 1048329001' }));
  });

  it('切到岗位报工会停掉收货摄像头，迟到条码不能改岗位目标', async () => {
    const stop = vi.fn();
    vi.mocked(hasLiveCamera).mockReturnValue(true);
    vi.mocked(openCameraStream).mockResolvedValue({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    let finish: (value: string) => void = () => {};
    vi.mocked(readLiveBarcode).mockImplementation(() => new Promise((resolve) => {
      finish = resolve;
    }));
    const wrapper = mountMobileOps();
    await wrapper.get('.mobile-order-create').trigger('click');
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');
    await flushPromises();
    await wrapper.get('.mobile-order-back').trigger('click');
    await openMobileAction(wrapper, '岗位报工');
    expect(stop).toHaveBeenCalled();
    finish('JG|mother|MD-LATE');
    await flushPromises();
    expect(queryScanContext).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('MD-LATE');
  });

  it('质检查不到单时不回落到列表里的默认质检单', async () => {
    vi.mocked(queryScanContext).mockRejectedValueOnce(new Error('未找到对应单据'));
    const wrapper = mount(QualityWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="质检贴纸"]').setValue('JG|work_order|MISSING');
    await wrapper.get('[aria-label="识别质检贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('.quality-check-panel').text()).toContain('未找到对应单据');
    expect(wrapper.get('.quality-check-panel').text()).not.toContain('ZD-DEFAULT');
    expect(lookupQualityCheck).not.toHaveBeenCalled();
    expect(submitQualityCheck).not.toHaveBeenCalled();
  });

  it('标签按上下文打开子单，查无此单时不改去母单或样例', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/label/templates')) {
        return { ok: true, json: async () => ({ code: 0, message: 'ok', data: [] }) } as Response;
      }
      if (url.endsWith('/api/label/work-orders/ZD-1')) {
        return {
          ok: true,
          json: async () => ({
            code: 0,
            message: 'ok',
            data: {
              type: 'WORK_ORDER',
              typeLabel: '子工单',
              primaryCode: 'ZD-1',
              qrContent: 'JG|work_order|ZD-1',
              fields: [{ name: '验证码', value: 'B8N4' }],
              footerLeft: '',
              footerRight: '',
              printedAt: '',
              brandTitle: '',
              templateCode: '',
              printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 },
            },
          }),
        } as Response;
      }
      return { ok: false, json: async () => ({ code: 404, message: '未找到', data: null }) } as Response;
    });
    vi.mocked(queryScanContext).mockResolvedValueOnce(buildScanContext({
      objectType: 'WORK_ORDER',
      code: 'ZD-1',
    }));
    const wrapper = mount(LabelWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('JG|work_order|ZD-1');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('已打开子单 ZD-1');
    const called = () => fetchSpy.mock.calls.map((call) => String(call[0]));
    expect(called().some((url) => url.endsWith('/api/label/work-orders/ZD-1'))).toBe(true);
    expect(called().some((url) => url.includes('/api/label/mother-orders/'))).toBe(false);

    vi.mocked(queryScanContext).mockRejectedValueOnce(new Error('未找到对应单据'));
    await wrapper.get('[aria-label="现场贴纸单号"]').setValue('NO-LABEL');
    await wrapper.get('[aria-label="打开现场贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('未找到对应单据');
    expect(wrapper.text()).not.toContain('已打开子单 ZD-1');
    expect(called().some((url) => url.includes('/api/label/mother-orders/'))).toBe(false);
    fetchSpy.mockRestore();
  });
});
