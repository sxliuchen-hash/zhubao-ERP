import { flushPromises, mount } from '@vue/test-utils';
import { ref } from 'vue';
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
import QualityPanel from '../quality/QualityPanel.vue';
import InventoryTraceWorkspace from './InventoryTraceWorkspace.vue';
import IntakePanel from '../intake/IntakePanel.vue';
import ProductionPanel from '../production/ProductionPanel.vue';
import { lookupProcessStation, submitProcessStation } from '../../services/processStationApi';
import { lookupQualityCheck, openQualityRework, submitQualityCheck } from '../../services/qualityCheckApi';
import { queryScanContext } from '../../services/scanContextApi';
import { contextForQuery } from '../scan/scanContextFixture';

function mountFieldPanel(component: typeof IntakePanel | typeof ProductionPanel) {
  return mount(component, { props: { title: '岗位作业', intro: '', field: true },
    global: { provide: { 'zhubao-auth-session': ref({ role: 'ADMIN' } as AuthSession) } } });
}

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInboundWorkspace: vi.fn(),
  fetchProductionWorkspace: vi.fn(),
  fetchQualityWorkspace: vi.fn(),
  fetchInventoryWorkspace: vi.fn(),
}));

vi.mock('../../services/mobileIntakeApi', () => ({
  fetchMotherOrder: vi.fn().mockRejectedValue(new Error('母单不存在')),
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
    quantityUnit: '件',
    weight: '12.40 g',
    frozen: '0',
    operationContext: {
      customerId: 1, ownerCustomerId: 1, motherOrderCode: 'MD-20260706-002', workOrderCode: 'ZD-20260706-001',
      materialCode: 'AU750', warehouseCode: 'FINISHED', ownerType: 'CUSTOMER', stockStatus: 'FINISHED', quantity: 8, weight: 12.4,
    },
  },
  {
    owner: '雅辰饰品',
    batch: 'ZD-20260706-001',
    material: '银链半成品',
    location: '在制仓',
    status: '在制',
    qty: 20,
    quantityUnit: '件',
    weight: '30.00 g',
    frozen: '0',
    operationContext: {
      customerId: 2, ownerCustomerId: 2, motherOrderCode: 'MD-20260706-002', workOrderCode: 'ZD-20260706-001',
      materialCode: 'S925', warehouseCode: 'WIP', ownerType: 'CUSTOMER', stockStatus: 'IN_PRODUCTION', quantity: 20, weight: 30,
    },
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

  it('renders inventory ledger details and trace evidence', async () => {
    const wrapper = mount(InventoryTraceWorkspace);
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '加工库存')!.trigger('click');

    expect(wrapper.text()).toContain('客户库存池');
    expect(wrapper.find('table.inventory-dispatch-table').exists()).toBe(true);
    expect(wrapper.findAll('table.inventory-dispatch-table tbody tr.is-selected')).toHaveLength(0);

    await wrapper.findAll('table.inventory-dispatch-table tbody tr')[0].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('MD-20260706-002');
    expect(wrapper.text()).toContain('确认发货');
    expect(wrapper.text()).not.toContain('确认出库');
    expect(wrapper.text()).not.toContain('不是回放路径');
    expect(wrapper.text()).toContain('退货与售后复核');
    expect(wrapper.text()).toContain('客户所有权');
    expect(wrapper.text()).toContain('追溯证据');
    expect(wrapper.text()).toContain('LEDGER-20260706-001');

    await wrapper.findAll('table.inventory-dispatch-table tbody tr')[1].trigger('click');

    expect(wrapper.find('.active-object-card > strong').text()).toBe('ZD-20260706-001');
    expect(wrapper.classes()).toContain('is-detail');
  });

  it('keeps the inventory status filter after returning and shows permission denial', async () => {
    const wrapper = mount(InventoryTraceWorkspace);
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '加工库存')!.trigger('click');
    await wrapper.findAll('button').find((button) => button.text() === '更多筛选')!.trigger('click');
    await wrapper.get('[aria-label="库存状态筛选"]').setValue('在制');
    expect(wrapper.get('table.inventory-dispatch-table').text()).toContain('ZD-20260706-001');
    expect(wrapper.get('table.inventory-dispatch-table').text()).not.toContain('MD-20260706-002');
    await wrapper.get('table.inventory-dispatch-table tbody tr').trigger('click');
    await wrapper.get('.detail-back').trigger('click');
    expect((wrapper.get('[aria-label="库存状态筛选"]').element as HTMLInputElement).value).toBe('在制');

    vi.mocked(fetchInventoryWorkspace).mockRejectedValueOnce(new ApiRequestError('无权查看库存', 403, 40301));
    const denied = mount(InventoryTraceWorkspace);
    await flushPromises();
    await denied.findAll('button').find((button) => button.text() === '加工库存')!.trigger('click');
    expect(denied.text()).toContain('无权限');
    expect(denied.text()).toContain('无权查看库存');
    expect(denied.find('.active-object-card').exists()).toBe(false);
  });

  it('renders the mobile intake form', async () => {
    const wrapper = mountFieldPanel(IntakePanel);
    await flushPromises();

    expect(wrapper.text()).toContain('快递单号');
    expect(wrapper.find('[aria-label="快递单号"]').exists()).toBe(true);
    expect(wrapper.find('[aria-label="拍面单"]').exists()).toBe(true);
  });

  it('opens a photo picker instead of telling the operator the browser cannot scan', async () => {
    const clicks = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {});
    const media = navigator.mediaDevices;
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
    const wrapper = mountFieldPanel(IntakePanel);
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
    const wrapper = mountFieldPanel(IntakePanel);
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');

    expect(wrapper.text()).toContain('请改用 https://');
    expect(getUserMedia).not.toHaveBeenCalled();
    Object.defineProperty(window, 'isSecureContext', { configurable: true, value: secure });
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: media });
  });

  it('shows only the signed-in station submit button', async () => {
    vi.mocked(queryScanContext).mockImplementation(async request => {
      const context = contextForQuery(request);
      context.order.quantity = 8; context.order.weight = 12.4; context.order.quantityUnit = '件';
      return context;
    });
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
    const wrapper = mountFieldPanel(ProductionPanel);
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
    expect(wrapper.text()).toContain('点击下方“确认交工”才会提交');
    expect(wrapper.text()).not.toContain('提交雕刻');
    expect(wrapper.text()).not.toContain('提交镶嵌');
    expect(wrapper.text()).not.toContain('提交抛光');
    expect(submitProcessStation).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    expect(submitProcessStation).not.toHaveBeenCalled();
    expect(wrapper.get('[aria-label="确认雕刻交工"]').element).toHaveProperty('disabled', false);
    expect(submitProcessStation).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('提交镶嵌');
    expect(wrapper.text()).not.toContain('提交抛光');
  });

  it('blocks submission when the previous station is not handed in', async () => {
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
    const wrapper = mountFieldPanel(ProductionPanel);
    await wrapper.get('[aria-label="贴纸二维码"]').setValue('JG|work_order|ZD-100');
    await wrapper.get('[aria-label="识别贴纸"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('上一道「雕刻」还未交工，当前不能提交「镶嵌」');
    expect(wrapper.find('[aria-label="核对 1 内壁刻字"]').exists()).toBe(false);
    const submit = wrapper.get('[aria-label="确认镶嵌交工"]');
    expect(submit.element).toHaveProperty('disabled', true);
    await submit.trigger('click');
    expect(submitProcessStation).not.toHaveBeenCalled();
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
    const wrapper = mount(QualityPanel, { props: { title: '对照加工要求', intro: '' } });
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

});
