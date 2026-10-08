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
import ProductionWorkspace from './ProductionWorkspace.vue';
import QualityWorkspace from './QualityWorkspace.vue';

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInboundWorkspace: vi.fn(),
  fetchProductionWorkspace: vi.fn(),
  fetchQualityWorkspace: vi.fn(),
  fetchInventoryWorkspace: vi.fn(),
  fetchMobileWorkspace: vi.fn(),
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

  it('renders mobile capability boundaries and offline drafts', async () => {
    const wrapper = mount(MobileOpsWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('移动现场作业台');
    expect(wrapper.text()).toContain('PWA 能力边界');
    expect(wrapper.text()).toContain('离线草稿');
    expect(wrapper.text()).toContain('DRAFT-QC-003');
  });
});
