import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  InboundOrder,
  InventoryBalance,
  InventoryLedgerEntry,
  LossApproval,
  OfflineDraft,
  ProductionHandoff,
  QualityCase,
  WorkOrder,
} from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  fetchInventoryWorkspace,
  fetchMobileWorkspace,
  fetchProductionWorkspace,
  fetchQualityWorkspace,
} from '../../services/businessWorkspaceApi';
import InboundWorkspace from './InboundWorkspace.vue';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import MobileOpsWorkspace from './MobileOpsWorkspace.vue';
import { lookupProcessStation, submitProcessStation } from '../../services/processStationApi';
import { lookupQualityCheck, openQualityRework, submitQualityCheck } from '../../services/qualityCheckApi';
import ProductionWorkspace from './ProductionWorkspace.vue';
import QualityWorkspace from './QualityWorkspace.vue';

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInboundWorkspace: vi.fn(),
  fetchProductionWorkspace: vi.fn(),
  fetchQualityWorkspace: vi.fn(),
  fetchInventoryWorkspace: vi.fn(),
  fetchMobileWorkspace: vi.fn(),
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

vi.mock('../../services/qualityCheckApi', () => ({
  lookupQualityCheck: vi.fn(),
  submitQualityCheck: vi.fn(),
  openQualityRework: vi.fn(),
}));

vi.mock('../../services/documentCaptureApi', () => ({
  fetchCaptures: vi.fn().mockResolvedValue([]),
  uploadCapture: vi.fn(),
}));

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

const offlineDraft: OfflineDraft = {
  code: 'DRAFT-QC-003',
  scene: '缺陷拍照',
  savedAt: '15:06',
  risk: '照片已缓存',
  tone: 'red',
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
    vi.mocked(fetchMobileWorkspace).mockResolvedValue({
      tasks: [],
      capabilities: [],
      offlineDrafts: [offlineDraft],
    });
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

    expect(wrapper.text()).toContain('今日工单池');
    expect(wrapper.find('table.production-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.production-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-006');
    expect(wrapper.text()).toContain('称重交接阻断');
    expect(wrapper.text()).toContain('设备产量汇总');
    expect(wrapper.text()).toContain('机器档案待匹配');
    expect(wrapper.text()).toContain('机器档案匹配建议');
    expect(wrapper.text()).not.toContain('报工校验规则');
    expect(wrapper.text()).toContain('提交报工');
    expect(wrapper.text()).toContain('审批阻断');

    await wrapper.findAll('table.production-dispatch-table tbody tr')[0].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
  });

  it('renders quality metrics and loss approvals', async () => {
    const wrapper = mount(QualityWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('今日质检池');
    expect(wrapper.find('table.quality-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.quality-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('QC-20260706-021');
    expect(wrapper.text()).toContain('损耗审批');
    expect(wrapper.text()).toContain('冻结异常库存');
    expect(wrapper.text()).toContain('生成返工工单');
    expect(wrapper.text()).toContain('提交返工报工');
    expect(wrapper.text()).toContain('复检放行入库');
    expect(wrapper.text()).not.toContain('POST /rework-reports');
    expect(wrapper.text()).toContain('LOSS-20260706-009');

    await wrapper.findAll('table.quality-dispatch-table tbody tr')[1].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('QC-20260706-022');
  });

  it('renders inventory ledger details and trace evidence', async () => {
    const wrapper = mount(InventoryTraceWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('客户库存池');
    expect(wrapper.find('table.inventory-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.inventory-dispatch-table tbody tr.is-selected')).toHaveLength(1);
    expect(wrapper.find('.active-object-card > strong').text()).toBe('MD-20260706-002');
    expect(wrapper.text()).toContain('确认出库');
    expect(wrapper.text()).toContain('退货入库');
    expect(wrapper.text()).toContain('售后复核');
    expect(wrapper.text()).toContain('客户所有权');
    expect(wrapper.text()).toContain('库存台账明细');
    expect(wrapper.text()).toContain('追溯证据');
    expect(wrapper.text()).toContain('LEDGER-20260706-001');

    await wrapper.findAll('table.inventory-dispatch-table tbody tr')[1].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
  });

  it('renders the mobile intake form', async () => {
    const wrapper = mount(MobileOpsWorkspace);
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
    const wrapper = mount(MobileOpsWorkspace);
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');

    expect(wrapper.text()).not.toContain('当前浏览器不能直接扫码');
    expect(clicks).toHaveBeenCalled();
    clicks.mockRestore();
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
    const wrapper = mount(MobileOpsWorkspace);
    await wrapper.get('[aria-label="岗位报工"]').trigger('click');
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
    const wrapper = mount(MobileOpsWorkspace);
    await wrapper.get('[aria-label="岗位报工"]').trigger('click');
    await wrapper.get('[aria-label="贴纸二维码"]').setValue('JG|work_order|ZD-100');
    await wrapper.get('[aria-label="识别贴纸"]').trigger('click');
    await flushPromises();
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

    expect(openQualityRework).toHaveBeenCalledWith('JG|work_order|ZD-100');
    expect(wrapper.text()).toContain('FG-ZD-100');
    expect(wrapper.text()).toContain('MD-100 / ZD-100');
    expect(wrapper.text()).toContain('库存已从冻结转入返工');
  });
});
