import { flushPromises, mount } from '@vue/test-utils';
import { ref } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InboundWorkspace from './InboundWorkspace.vue';
import type { InboundOrder } from '../../data/m1PrototypeData';
import { fetchInboundWorkspace } from '../../services/businessWorkspaceApi';
import { fetchCaptures } from '../../services/documentCaptureApi';
import { fetchMotherOrder } from '../../services/mobileIntakeApi';
import { fetchMotherSplit } from '../../services/motherSplitApi';

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInboundWorkspace: vi.fn(),
}));

vi.mock('../../services/businessOperationApi', () => ({
  confirmInbound: vi.fn(),
}));

vi.mock('../../services/documentCaptureApi', () => ({
  fetchCaptures: vi.fn(),
}));

vi.mock('../../services/mobileIntakeApi', () => ({
  fetchMotherOrder: vi.fn(),
  lookupMobileIntake: vi.fn(),
  submitMobileIntake: vi.fn(),
}));

vi.mock('../../services/motherSplitApi', () => ({
  fetchMotherSplit: vi.fn(),
  keepMotherOrder: vi.fn(),
  splitMotherOrder: vi.fn(),
}));

vi.mock('../../services/labelApi', () => ({
  fetchMotherLabel: vi.fn(),
  fetchWorkOrderLabel: vi.fn(),
}));

const first: InboundOrder = {
  code: 'MD-LONG-20261008-0001',
  customer: '恒信珠宝',
  material: '18K 金戒托',
  inboundDate: '10-08',
  expressCompany: '顺丰',
  courierNo: 'SF1',
  senderName: '张先生',
  receiverName: '何琳',
  unpackSigner: '何琳',
  goodsName: '戒托',
  qty: 1200000,
  weight: '486.25 g',
  status: '待拆包',
  video: '已关联',
  owner: '客户所有',
  remark: '',
};

const second: InboundOrder = {
  ...first,
  code: 'MD-API-002',
  customer: '瑞禾珠宝',
  qty: 3,
  status: '已入库',
  video: '失败',
};

describe('来料列表与详情', () => {
  beforeEach(() => {
    vi.mocked(fetchInboundWorkspace).mockResolvedValue({
      checkpoints: [],
      orders: [first, second],
      risks: [],
    });
    vi.mocked(fetchCaptures).mockResolvedValue([]);
    vi.mocked(fetchMotherOrder).mockRejectedValue(new Error('母单详情不可用'));
    vi.mocked(fetchMotherSplit).mockResolvedValue({
      motherOrderCode: first.code,
      decision: 'UNDECIDED',
      decisionLabel: '未拆',
      verificationCode: '',
      customerName: first.customer,
      processingRequirement: '抛光',
      dueDate: '2026-10-20',
      goodsName: first.goodsName,
      quantityUnit: '件',
      quantity: first.qty,
      weight: 486.25,
      remainingQuantity: first.qty,
      remainingWeight: 486.25,
      qrContent: '',
      children: [],
    });
  });

  it('按客户筛选，选中后返回仍保留筛选和选中', async () => {
    const wrapper = mount(InboundWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('打开后核对');
    expect(wrapper.text()).toContain('1200000 件');
    expect(wrapper.text()).toContain('MD-LONG-20261008-0001');

    await wrapper.get('[aria-label="客户筛选"]').setValue('瑞禾');
    expect(wrapper.text()).toContain('MD-API-002');
    expect(wrapper.text()).not.toContain(first.code);

    await wrapper.get('.inbound-card').trigger('click');
    expect(wrapper.get('.active-object-card > strong').text()).toContain('MD-API-002');

    await wrapper.get('.detail-back').trigger('click');
    expect(wrapper.get('[aria-label="客户筛选"]').element).toHaveProperty('value', '瑞禾');
    expect(wrapper.get('.active-object-card > strong').text()).toContain('MD-API-002');
  });

  it('无数据和视频失败分别说明，拆单打印入口在母单详情里', async () => {
    vi.mocked(fetchInboundWorkspace).mockResolvedValueOnce({
      checkpoints: [],
      orders: [],
      risks: [],
    });
    const empty = mount(InboundWorkspace);
    await flushPromises();
    expect(empty.text()).toContain('接口没有返回来料单据');
    expect(empty.text()).toContain('暂无来料单据');

    vi.mocked(fetchInboundWorkspace).mockResolvedValue({
      checkpoints: [],
      orders: [first],
      risks: [],
    });
    vi.mocked(fetchCaptures).mockRejectedValueOnce(new Error('视频暂时无法打开'));
    vi.mocked(fetchMotherOrder).mockResolvedValueOnce({
      alreadyExists: true,
      motherOrderCode: first.code,
      courierNo: 'SF1',
      customerName: first.customer,
      processingRequirement: '抛光',
      dueDate: '2026-10-20',
      goodsName: first.goodsName,
      quantity: first.qty,
      weight: 486.25,
      ledgerCode: 'LED-1',
      qrContent: 'JG|mother|MD',
    });
    const wrapper = mount(InboundWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('视频暂时无法打开');
    expect(wrapper.text()).toContain('抛光 / 2026-10-20');
    expect(wrapper.text()).toContain('不拆，打一张贴纸');
  });

  it('追溯关键字和本页扫码一起过滤', async () => {
    const traceQuery = ref('MD-API-002');
    const wrapper = mount(InboundWorkspace, {
      global: { provide: { 'zhubao-trace-query': traceQuery } },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('MD-API-002');
    expect(wrapper.text()).not.toContain(first.code);
  });
});
