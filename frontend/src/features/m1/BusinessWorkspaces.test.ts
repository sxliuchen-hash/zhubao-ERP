import { flushPromises, mount } from '@vue/test-utils';
import { nextTick, ref } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../../services/apiClient';
import type { AuthSession } from '../../services/authApi';
import type {
  InboundOrder,
  InventoryBalance,
  InventoryLedgerEntry,
  LossApproval,
  ProductionHandoff,
  QualityCase,
  WorkOrder,
} from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  fetchInventoryWorkspace,
  fetchProductionWorkspace,
  fetchQualityWorkspace,
} from '../../services/businessWorkspaceApi';
import InboundWorkspace from './InboundWorkspace.vue';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import MobileOpsWorkspace from './MobileOpsWorkspace.vue';
import { lookupProcessStation, submitProcessStation } from '../../services/processStationApi';
import { lookupQualityCheck, openQualityRework, submitQualityCheck } from '../../services/qualityCheckApi';
import { queryScanContext } from '../../services/scanContextApi';
import { contextForQuery } from '../scan/scanContextFixture';
import ProductionWorkspace from './ProductionWorkspace.vue';
import QualityWorkspace from './QualityWorkspace.vue';

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

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInboundWorkspace: vi.fn(),
  fetchProductionWorkspace: vi.fn(),
  fetchQualityWorkspace: vi.fn(),
  fetchInventoryWorkspace: vi.fn(),
}));

vi.mock('../../services/mobileIntakeApi', () => ({
  fetchMotherOrder: vi.fn().mockRejectedValue(new Error('母单不存在')),
  lookupMobileIntake: vi.fn(),
  submitMobileIntake: vi.fn(),
}));

vi.mock('../../services/processStationApi', () => ({
  lookupProcessStation: vi.fn(),
  submitProcessStation: vi.fn(),
}));
vi.mock('../../services/evidenceApi', () => ({
  fetchEvidenceBoard: vi.fn().mockResolvedValue({ links: [], historical: [] }),
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));

vi.mock('../../services/qualityCheckApi', () => ({
  lookupQualityCheck: vi.fn(),
  submitQualityCheck: vi.fn(),
  openQualityRework: vi.fn(),
}));

vi.mock('../../services/documentCaptureApi', () => ({
  fetchCaptures: vi.fn().mockResolvedValue([]),
  uploadCapture: vi.fn(),
}));

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn() };
});

const inboundOrder: InboundOrder = {
  code: 'MD-API-001',
  customer: '恒信珠宝',
  material: '18K 金戒托',
  inboundDate: '07-06',
  expressCompany: '顺丰',
  courierNo: 'SF1048329001',
  senderName: '张先生',
  receiverName: '何琳',
  unpackSigner: '何琳',
  goodsName: '戒托来料',
  qty: 12,
  weight: '18.20 g',
  status: '异常待处理',
  video: '失败',
  owner: '客户所有',
  remark: '接口返回的母单',
};

const workOrders: WorkOrder[] = [
  {
    code: 'ZD-20260706-001',
    customer: '恒信珠宝',
    material: '18K 金戒托',
    process: '抛光',
    processCategory: '抛光',
    productCategory: '戒托',
    machineNo: 'ENG-03',
    workItemName: '精抛',
    startedAt: '09:00',
    finishedAt: '11:00',
    completionStatus: '加工中',
    handoffAuditStatus: '待审核',
    assignee: '李华',
    plannedQty: 80,
    finishedQty: 56,
    status: '加工中',
    progress: 70,
  },
  {
    code: 'ZD-20260706-006',
    customer: '雅辰饰品',
    material: '银链半成品',
    process: '称重交接',
    processCategory: '交接',
    productCategory: '银链',
    machineNo: '',
    workItemName: '称重',
    startedAt: '13:00',
    finishedAt: '',
    completionStatus: '待称重',
    handoffAuditStatus: '未通过',
    assignee: '王强',
    plannedQty: 20,
    finishedQty: 0,
    status: '待称重',
    progress: 10,
  },
];

const handoff: ProductionHandoff = {
  code: 'HO-API-001',
  from: '抛光',
  to: '质检',
  inputWeight: '10.000 g',
  outputWeight: '9.800 g',
  lossRate: '2.0%',
  status: '审批阻断',
  tone: 'red',
};

const qualityCases: QualityCase[] = [
  {
    code: 'QC-20260706-021',
    workOrder: 'ZD-20260706-001',
    process: '抛光',
    inspector: '周娜',
    defect: '划痕',
    action: '返工',
    status: '待质检',
  },
  {
    code: 'QC-20260706-022',
    workOrder: 'ZD-20260706-006',
    process: '镶石',
    inspector: '周娜',
    defect: '石松',
    action: '放行',
    status: '已完成',
  },
];

const lossApproval: LossApproval = {
  code: 'LOSS-20260706-009',
  workOrder: 'ZD-20260706-001',
  process: '抛光',
  standard: '2.0%',
  actual: '3.8%',
  evidence: '投入 10.000g / 产出 9.620g',
  action: '待审批',
  tone: 'red',
};

const balances: InventoryBalance[] = [
  {
    owner: '恒信珠宝',
    batch: 'MD-20260706-002',
    material: '18K 金戒托',
    location: '成品仓',
    status: '成品',
    qty: 8,
    weight: '12.40 g',
    frozen: '0',
  },
  {
    owner: '雅辰饰品',
    batch: 'ZD-20260706-001',
    material: '银链半成品',
    location: '在制仓',
    status: '在制',
    qty: 20,
    weight: '30.00 g',
    frozen: '0',
  },
];

const ledger: InventoryLedgerEntry = {
  time: '15:20',
  bizCode: 'LEDGER-20260706-001',
  movement: '入库',
  before: '0',
  after: '8',
  source: 'MD-20260706-002',
  operator: '何琳',
};



describe('M1 business workspaces', () => {
  beforeEach(() => {
    vi.mocked(fetchInboundWorkspace).mockResolvedValue({
      checkpoints: [],
      orders: [inboundOrder],
      risks: [
        {
          code: inboundOrder.code,
          title: '拆包视频截取失败',
          detail: '接口返回的风险',
          owner: '库管员',
          action: '重试截取',
          tone: 'red',
        },
      ],
    });
    vi.mocked(fetchProductionWorkspace).mockResolvedValue({
      stationLoads: [],
      workOrders,
      handoffs: [handoff],
    });
    vi.mocked(fetchQualityWorkspace).mockResolvedValue({
      metrics: [],
      cases: qualityCases,
      lossApprovals: [lossApproval],
    });
    vi.mocked(fetchInventoryWorkspace).mockResolvedValue({
      balances,
      ledgerEntries: [ledger],
      timeline: [],
      evidence: [],
    });
    vi.mocked(queryScanContext).mockImplementation(async (request) => contextForQuery(request));
  });

  it('renders inbound documents returned by the api', async () => {
    const wrapper = mount(InboundWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('今日母单池');
    expect(wrapper.find('table.inbound-dispatch-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('入库证据槽');
    expect(wrapper.text()).not.toContain('入库校验规则');
    expect(wrapper.text()).toContain('确认入库');
    expect(wrapper.text()).toContain('拆包视频截取失败');
    expect(wrapper.text()).toContain('MD-API-001');
  });

  it('shows an empty inbound list and the failure reason when the api is unavailable', async () => {
    vi.mocked(fetchInboundWorkspace).mockRejectedValueOnce(new Error('来料接口不可用'));
    const wrapper = mount(InboundWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('来料接口不可用');
    expect(wrapper.text()).toContain('暂无来料单据');
    expect(wrapper.text()).not.toContain('MD-API-001');
    expect(wrapper.find('.active-object-card').exists()).toBe(false);
  });

  it('renders production station load and weighing handoff controls', async () => {
    const wrapper = mount(ProductionWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('工序交付');
    expect(wrapper.text()).toContain('今日工单池');
    expect(wrapper.find('table.production-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.production-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-006');
    expect(wrapper.text()).toContain('称重交接阻断');
    expect(wrapper.text()).toContain('设备产量汇总');
    expect(wrapper.text()).toContain('机器档案待匹配');
    expect(wrapper.text()).toContain('机器档案匹配建议');
    expect(wrapper.text()).not.toContain('报工校验规则');
    expect(wrapper.text()).not.toContain('提交报工');
    expect(wrapper.text()).not.toContain('不是回放路径');
    expect(wrapper.text()).toContain('审批阻断');

    await wrapper.findAll('table.production-dispatch-table tbody tr')[0].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
    expect(wrapper.classes()).toContain('is-detail');
  });

  it('filters production orders and keeps the filter after returning from detail', async () => {
    const wrapper = mount(ProductionWorkspace);
    await flushPromises();

    await wrapper.get('[aria-label="客户筛选"]').setValue('恒信');
    expect(wrapper.get('table.production-dispatch-table').text()).toContain('ZD-20260706-001');
    expect(wrapper.get('table.production-dispatch-table').text()).not.toContain('ZD-20260706-006');

    await wrapper.get('.production-card').trigger('click');
    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
    expect(wrapper.classes()).toContain('is-detail');

    await wrapper.get('.detail-back').trigger('click');
    expect(wrapper.classes()).not.toContain('is-detail');
    expect((wrapper.get('[aria-label="客户筛选"]').element as HTMLInputElement).value).toBe('恒信');
    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
  });

  it('shows an empty production list while loading and when the api returns nothing', async () => {
    let release: (value: Awaited<ReturnType<typeof fetchProductionWorkspace>>) => void = () => {};
    vi.mocked(fetchProductionWorkspace).mockReturnValue(new Promise((resolve) => {
      release = resolve;
    }));
    const wrapper = mount(ProductionWorkspace);
    await nextTick();
    expect(wrapper.text()).toContain('正在加载工单');
    expect(wrapper.text()).not.toContain('暂无工单');

    release({ stationLoads: [], workOrders: [], handoffs: [] });
    await flushPromises();
    expect(wrapper.text()).toContain('接口没有返回工单');
    expect(wrapper.text()).toContain('暂无工单');
    expect(wrapper.text()).toContain('暂无称重交接');
    expect(wrapper.find('.active-object-card').exists()).toBe(false);
  });

  it('keeps the production failure and permission text instead of an empty success', async () => {
    vi.mocked(fetchProductionWorkspace).mockRejectedValueOnce(new Error('工单接口不可用'));
    const failed = mount(ProductionWorkspace);
    await flushPromises();
    expect(failed.text()).toContain('工单接口不可用');
    expect(failed.text()).toContain('暂无工单');
    expect(failed.text()).not.toContain('无权限');
    expect(failed.text()).not.toContain('ZD-20260706-001');

    vi.mocked(fetchProductionWorkspace).mockRejectedValueOnce(new ApiRequestError('无权查看生产工单', 403, 40301));
    const denied = mount(ProductionWorkspace);
    await flushPromises();
    expect(denied.text()).toContain('无权限');
    expect(denied.text()).toContain('无权查看生产工单');
    expect(denied.find('.active-object-card').exists()).toBe(false);
    expect(denied.text()).not.toContain('ZD-20260706-001');
  });

  it('renders quality metrics and loss approvals', async () => {
    const wrapper = mount(QualityWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('今日质检池');
    expect(wrapper.find('table.quality-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.quality-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('QC-20260706-021');
    expect(wrapper.text()).toContain('损耗审批');
    expect(wrapper.text()).toContain('提交损耗申请');
    expect(wrapper.text()).toContain('对照加工要求');
    expect(wrapper.text()).not.toContain('冻结异常库存');
    expect(wrapper.text()).not.toContain('生成返工工单');
    expect(wrapper.text()).not.toContain('复检放行入库');
    expect(wrapper.text()).toContain('LOSS-20260706-009');

    await wrapper.findAll('table.quality-dispatch-table tbody tr')[1].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('QC-20260706-022');
    expect(wrapper.classes()).toContain('is-detail');
  });

  it('filters quality cases and keeps the filter after returning', async () => {
    const wrapper = mount(QualityWorkspace);
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '更多筛选')!.trigger('click');
    await wrapper.get('[aria-label="质检状态筛选"]').setValue('已完成');
    expect(wrapper.get('table.quality-dispatch-table').text()).toContain('QC-20260706-022');
    expect(wrapper.get('table.quality-dispatch-table').text()).not.toContain('QC-20260706-021');
    await wrapper.get('.desk-card').trigger('click');
    expect(wrapper.classes()).toContain('is-detail');
    await wrapper.get('.detail-back').trigger('click');
    expect(wrapper.classes()).not.toContain('is-detail');
    expect((wrapper.get('[aria-label="质检状态筛选"]').element as HTMLInputElement).value).toBe('已完成');

    vi.mocked(fetchQualityWorkspace).mockRejectedValueOnce(new ApiRequestError('无权查看质检', 403, 40301));
    const denied = mount(QualityWorkspace);
    await flushPromises();
    expect(denied.text()).toContain('无权限');
    expect(denied.text()).toContain('无权查看质检');
    expect(denied.find('.active-object-card').exists()).toBe(false);
  });

  it('renders inventory ledger details and trace evidence', async () => {
    const wrapper = mount(InventoryTraceWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('客户库存池');
    expect(wrapper.find('table.inventory-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.inventory-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('MD-20260706-002');
    expect(wrapper.text()).toContain('确认发货');
    expect(wrapper.text()).not.toContain('确认出库');
    expect(wrapper.text()).not.toContain('不是回放路径');
    expect(wrapper.text()).toContain('退货入库');
    expect(wrapper.text()).toContain('售后复核');
    expect(wrapper.text()).toContain('客户所有权');
    expect(wrapper.text()).toContain('库存台账明细');
    expect(wrapper.text()).toContain('追溯证据');
    expect(wrapper.text()).toContain('LEDGER-20260706-001');

    await wrapper.findAll('table.inventory-dispatch-table tbody tr')[1].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
    expect(wrapper.classes()).toContain('is-detail');
  });

  it('keeps the inventory status filter after returning and shows permission denial', async () => {
    const wrapper = mount(InventoryTraceWorkspace);
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '更多筛选')!.trigger('click');
    await wrapper.get('[aria-label="库存状态筛选"]').setValue('在制');
    expect(wrapper.get('table.inventory-dispatch-table').text()).toContain('ZD-20260706-001');
    expect(wrapper.get('table.inventory-dispatch-table').text()).not.toContain('MD-20260706-002');
    await wrapper.get('.detail-back').trigger('click');
    expect((wrapper.get('[aria-label="库存状态筛选"]').element as HTMLInputElement).value).toBe('在制');

    vi.mocked(fetchInventoryWorkspace).mockRejectedValueOnce(new ApiRequestError('无权查看库存', 403, 40301));
    const denied = mount(InventoryTraceWorkspace);
    await flushPromises();
    expect(denied.text()).toContain('无权限');
    expect(denied.text()).toContain('无权查看库存');
    expect(denied.find('.active-object-card').exists()).toBe(false);
  });

  it('renders the mobile intake form', async () => {
    const wrapper = mountMobileOps();
    await wrapper.get('.mobile-order-create').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('手机收货');
    expect(wrapper.text()).toContain('快递单号');
    expect(wrapper.find('[aria-label="快递单号"]').exists()).toBe(true);
    expect(wrapper.find('[aria-label="拍面单"]').exists()).toBe(true);
  });

  it('opens a photo picker instead of telling the operator the browser cannot scan', async () => {
    const clicks = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {});
    const media = navigator.mediaDevices;
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
    const wrapper = mountMobileOps();
    await wrapper.get('.mobile-order-create').trigger('click');
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');

    expect(wrapper.text()).not.toContain('当前浏览器不能直接扫码');
    expect(clicks).toHaveBeenCalled();
    clicks.mockRestore();
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: media });
  });

  it('asks for https before opening the camera on an insecure page', async () => {
    const secure = window.isSecureContext;
    Object.defineProperty(window, 'isSecureContext', { configurable: true, value: false });
    const getUserMedia = vi.fn();
    const media = navigator.mediaDevices;
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia } });
    const wrapper = mountMobileOps();
    await wrapper.get('.mobile-order-create').trigger('click');
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');

    expect(wrapper.text()).toContain('请改用 https://');
    expect(getUserMedia).not.toHaveBeenCalled();
    Object.defineProperty(window, 'isSecureContext', { configurable: true, value: secure });
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: media });
  });

  it('shows only the signed-in station submit button', async () => {
    vi.mocked(lookupProcessStation).mockResolvedValue({
      orderCode: 'ZD-100',
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
    });
    const wrapper = mountMobileOps();
    await openMobileAction(wrapper, '岗位报工');
    await wrapper.get('[aria-label="贴纸二维码"]').setValue('JG|work_order|ZD-100');
    await wrapper.get('[aria-label="识别贴纸"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('ZD-100');
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('雕刻要求');
    expect(wrapper.text()).toContain('内壁刻字');
    expect(wrapper.text()).toContain('2026-10-20');
    expect(wrapper.text()).toContain('上一道是否已交');
    expect(wrapper.text()).toContain('无上一道');
    expect(wrapper.text()).toContain('勾选不是报工');
    expect(wrapper.text()).not.toContain('提交雕刻');
    expect(wrapper.text()).not.toContain('提交镶嵌');
    expect(wrapper.text()).not.toContain('提交抛光');
    expect(submitProcessStation).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    expect(submitProcessStation).not.toHaveBeenCalled();
    await wrapper.get('[aria-label="完成核对"]').trigger('click');
    expect(submitProcessStation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('提交雕刻');
    expect(wrapper.text()).not.toContain('提交镶嵌');
    expect(wrapper.text()).not.toContain('提交抛光');
  });

  it('shows the server rejection when the previous station is not handed in', async () => {
    vi.mocked(lookupProcessStation).mockResolvedValue({
      orderCode: 'ZD-100',
      customerName: '恒信珠宝',
      dueDate: '2026-10-20',
      processingRequirement: '内壁刻字',
      stations: [{
        processName: '镶嵌',
        requirement: '内壁刻字',
        dueDate: '2026-10-20',
        previousProcessName: '雕刻',
        previousSubmitted: false,
        submitted: false,
      }],
      message: '',
    });
    vi.mocked(submitProcessStation).mockRejectedValue(new Error('上一道「雕刻」还没交，不能交「镶嵌」'));
    const wrapper = mountMobileOps();
    await openMobileAction(wrapper, '岗位报工');
    await wrapper.get('[aria-label="贴纸二维码"]').setValue('JG|work_order|ZD-100');
    await wrapper.get('[aria-label="识别贴纸"]').trigger('click');
    await flushPromises();
    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    await wrapper.get('[aria-label="完成核对"]').trigger('click');
    const submit = wrapper.findAll('button').find((button) => button.text() === '提交镶嵌');
    expect(submit).toBeTruthy();
    await submit!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('上一道「雕刻」还没交，不能交「镶嵌」');
    expect(wrapper.text()).toContain('未交');
    expect(wrapper.text()).not.toContain('提交雕刻');
    expect(wrapper.text()).not.toContain('提交抛光');
  });

  it('requires every requirement line and records the signed-in inspector', async () => {
    vi.mocked(lookupQualityCheck).mockResolvedValue({
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-100',
      customerName: '恒信珠宝',
      dueDate: '2026-10-20',
      processingRequirement: '内壁刻字\n镶一颗主石\n镜面抛光',
      ready: true,
      blockReason: '',
      inspector: '质检员 周娜',
      qualityCaseCode: '',
      outcome: '',
      frozen: false,
      reworkOrderCode: '',
      lines: [
        { lineNo: 1, clause: '内壁刻字', verdict: '', reason: '' },
        { lineNo: 2, clause: '镶一颗主石', verdict: '', reason: '' },
        { lineNo: 3, clause: '镜面抛光', verdict: '', reason: '' },
      ],
    });
    const wrapper = mount(QualityWorkspace);
    await flushPromises();
    await wrapper.get('[aria-label="质检贴纸"]').setValue('JG|work_order|ZD-100');
    await wrapper.get('[aria-label="识别质检贴纸"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('内壁刻字');
    expect(wrapper.text()).toContain('镶一颗主石');
    expect(wrapper.text()).toContain('镜面抛光');
    expect(wrapper.text()).toContain('质检员 周娜');
    expect(wrapper.find('[aria-label="质检员姓名"]').exists()).toBe(false);
    expect((wrapper.get('[aria-label="提交质检"]').element as HTMLButtonElement).disabled).toBe(true);
    expect(wrapper.text()).toContain('每一条加工要求都要有结果才能提交');

    await wrapper.get('[aria-label="合格 1"]').trigger('click');
    await wrapper.get('[aria-label="合格 2"]').trigger('click');
    expect((wrapper.get('[aria-label="提交质检"]').element as HTMLButtonElement).disabled).toBe(true);

    await wrapper.get('[aria-label="不合格 3"]').trigger('click');
    expect(wrapper.text()).toContain('不合格和让步必须写原因');
    expect((wrapper.get('[aria-label="提交质检"]').element as HTMLButtonElement).disabled).toBe(true);
    await wrapper.get('[aria-label="原因 3"]').setValue('镜面有划痕');
    expect((wrapper.get('[aria-label="提交质检"]').element as HTMLButtonElement).disabled).toBe(false);

    vi.mocked(submitQualityCheck).mockResolvedValue({
      qualityCaseCode: 'QC-ZD-100',
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-100',
      inspector: '质检员 周娜',
      outcome: '不合格',
      frozen: true,
      ledgerCode: 'STK-1',
      reworkOrderCode: '',
      lines: [
        { lineNo: 1, clause: '内壁刻字', verdict: '合格', reason: '' },
        { lineNo: 2, clause: '镶一颗主石', verdict: '合格', reason: '' },
        { lineNo: 3, clause: '镜面抛光', verdict: '不合格', reason: '镜面有划痕' },
      ],
    });
    await wrapper.get('[aria-label="提交质检"]').trigger('click');
    await flushPromises();

    const payload = vi.mocked(submitQualityCheck).mock.calls[0][0];
    expect(payload).not.toHaveProperty('inspector');
    expect(payload.lines).toHaveLength(3);
    expect(wrapper.text()).toContain('质检员 周娜');
    expect(wrapper.text()).toContain('QC-ZD-100');
    expect(wrapper.text()).toContain('半成品已转入冻结');

    vi.mocked(openQualityRework).mockResolvedValue({
      reworkOrderCode: 'FG-ZD-100',
      qualityCaseCode: 'QC-ZD-100',
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-100',
      ledgerCode: 'STK-2',
      fromStockStatus: 'FROZEN',
      toStockStatus: 'REWORK',
    });
    await wrapper.get('[aria-label="开返工"]').trigger('click');
    await flushPromises();

    expect(openQualityRework).toHaveBeenCalledWith('ZD-100', expect.stringMatching(/^quality-/));
    expect(wrapper.text()).toContain('FG-ZD-100');
    expect(wrapper.text()).toContain('MD-100 / ZD-100');
    expect(wrapper.text()).toContain('库存已从冻结转入返工');
  });

  it('手机现场有质检入口，操作页不出现收款', async () => {
    vi.mocked(lookupQualityCheck).mockResolvedValue({
      orderCode: 'ZD-100',
      motherOrderCode: 'MD-100',
      customerName: '恒信珠宝',
      dueDate: '2026-10-20',
      processingRequirement: '内壁刻字',
      ready: true,
      blockReason: '',
      inspector: '质检员 周娜',
      qualityCaseCode: '',
      outcome: '',
      frozen: false,
      reworkOrderCode: '',
      lines: [{ lineNo: 1, clause: '内壁刻字', verdict: '', reason: '' }],
      roundCode: '',
      roundNo: 0,
      roundType: '',
      reworkReported: false,
      canRecheck: false,
      recheckBlock: '',
      canRelease: false,
      releaseBlock: '',
      rounds: [],
    });
    const wrapper = mountMobileOps();
    expect(wrapper.text()).not.toContain('收款');
    await openMobileAction(wrapper, '发货');
    expect(wrapper.text()).toContain('确认发货');
    expect(wrapper.text()).not.toContain('收款');

    await wrapper.get('.mobile-order-back').trigger('click');
    await openMobileAction(wrapper, '质检');
    await wrapper.get('[aria-label="质检贴纸"]').setValue('JG|work_order|ZD-100');
    await wrapper.get('[aria-label="识别质检贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('手机质检');
    expect(wrapper.text()).toContain('质检员 周娜');
    expect(wrapper.text()).toContain('质检员取当前登录账号');
    expect(wrapper.find('[aria-label="质检员姓名"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('收款');
  });
});
