import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { InboundOrder } from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  fetchProductionWorkspace,
  fetchQualityWorkspace,
} from '../../services/businessWorkspaceApi';
import M1Dashboard from './M1Dashboard.vue';

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInboundWorkspace: vi.fn(),
  fetchProductionWorkspace: vi.fn(),
  fetchQualityWorkspace: vi.fn(),
}));

function order(code: string, status: string): InboundOrder {
  return {
    code,
    customer: '恒信珠宝',
    material: '18K 金戒托',
    inboundDate: '07-06',
    expressCompany: '顺丰',
    courierNo: code,
    senderName: '张先生',
    receiverName: '何琳',
    unpackSigner: '何琳',
    goodsName: '戒托来料',
    qty: 1,
    weight: '1.00 g',
    status,
    video: '已关联',
    owner: '客户所有',
    remark: '',
  };
}

describe('M1Dashboard', () => {
  beforeEach(() => {
    vi.mocked(fetchInboundWorkspace).mockResolvedValue({
      checkpoints: [],
      orders: [order('MD-API-001', '待拆包'), order('MD-API-002', '已入库')],
      risks: [],
    });
    vi.mocked(fetchProductionWorkspace).mockResolvedValue({
      stationLoads: [],
      workOrders: [],
      handoffs: [],
    });
    vi.mocked(fetchQualityWorkspace).mockResolvedValue({
      metrics: [],
      cases: [],
      lossApprovals: [],
    });
  });

  it('keeps the selected row and the side panel on the same document', async () => {
    const wrapper = mount(M1Dashboard);
    await flushPromises();

    expect(wrapper.text()).toContain('今日待办');
    expect(wrapper.text()).toContain('当前处理单');
    expect(wrapper.text()).toContain('来料待处理');
    expect(wrapper.text()).not.toContain('分派责任人');
    expect(wrapper.findAll('.mission-kpi-grid article')).toHaveLength(4);
    expect(wrapper.find('.active-record-code strong').text()).toBe('MD-API-001');

    const rows = wrapper.findAll('table.floor-dispatch-table tbody tr');
    await rows[1].trigger('click');

    expect(wrapper.find('.active-record-code strong').text()).toBe('MD-API-002');
    expect(wrapper.find('.active-record-panel').text()).toContain('去收货');
  });

  it('shows an empty list and the failure reason when workspace apis are unavailable', async () => {
    vi.mocked(fetchInboundWorkspace).mockRejectedValueOnce(new Error('待办接口不可用'));
    vi.mocked(fetchProductionWorkspace).mockRejectedValueOnce(new Error('待办接口不可用'));
    vi.mocked(fetchQualityWorkspace).mockRejectedValueOnce(new Error('待办接口不可用'));

    const wrapper = mount(M1Dashboard);
    await flushPromises();

    expect(wrapper.text()).toContain('待办接口不可用');
    expect(wrapper.text()).toContain('暂无待办单据');
    expect(wrapper.find('.active-record-code').exists()).toBe(false);
  });
});
