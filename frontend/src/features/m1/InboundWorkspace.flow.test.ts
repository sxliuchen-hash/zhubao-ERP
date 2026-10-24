import { flushPromises, mount } from '@vue/test-utils';
import { ref } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InboundWorkspace from './InboundWorkspace.vue';
import IntakePanel from '../intake/IntakePanel.vue';
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
  quantityUnit: '件',
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

  it('来料表格、手机卡片与详情保留小数及原单位，旧记录不猜成件', async () => {
    vi.mocked(fetchInboundWorkspace).mockResolvedValue({ checkpoints: [], risks: [], orders: [
      { ...first, qty: 10.5, quantityUnit: '克' },
      { ...second, qty: 120, quantityUnit: undefined },
    ] });
    const wrapper = mount(InboundWorkspace);
    await flushPromises();
    await wrapper.get('.inbound-card').trigger('click');
    expect(wrapper.get('table').text()).toContain('10.5 克');
    expect(wrapper.findAll('.inbound-card')[0].text()).toContain('10.5 克');
    expect(wrapper.get('[aria-label="母单详情"]').text()).toContain('10.5 克');
    expect(wrapper.text()).not.toContain('10 件');
    expect(wrapper.text()).not.toContain('120 件');
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
    expect(empty.text()).not.toContain('来料单据加载失败');
    expect(empty.text()).toContain('生成第一张母单');
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
    expect(wrapper.text()).toContain('不拆，预览流转贴纸');
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

  it('新建母单后自动刷新并选中，已入库单据不再提示确认入库', async () => {
    const wrapper = mount(InboundWorkspace);
    await flushPromises();
    const result = {
      alreadyExists: false, motherOrderCode: 'MD-NEW-1', courierNo: 'SF-NEW-1',
      customerName: '新客户', processingRequirement: '抛光', dueDate: '2026-10-20',
      goodsName: '戒托', quantity: 1, weight: 2.5, ledgerCode: 'STK-NEW-1', qrContent: 'JG|mother|MD-NEW-1',
    };
    vi.mocked(fetchInboundWorkspace).mockResolvedValueOnce({
      checkpoints: [], risks: [], orders: [first, { ...second, code: result.motherOrderCode, customer: result.customerName }],
    });
    vi.mocked(fetchMotherOrder).mockResolvedValue(result);
    const countBefore = vi.mocked(fetchInboundWorkspace).mock.calls.length;
    wrapper.getComponent(IntakePanel).vm.$emit('saved', result);
    await flushPromises();
    expect(fetchInboundWorkspace).toHaveBeenCalledTimes(countBefore + 1);
    expect(wrapper.get('.active-object-card > strong').text()).toBe('MD-NEW-1');
    expect(wrapper.text()).toContain('已收货入库');
    expect(wrapper.findAll('button').some((button) => button.text() === '确认入库')).toBe(false);
  });

  it('从工作台进入时按规范母单目标打开详情', async () => {
    const target = ref({ objectType: 'MOTHER_ORDER', code: second.code, intent: 'INTAKE', token: 1 });
    const wrapper = mount(InboundWorkspace, { global: { provide: { 'zhubao-trace-target': target } } });
    await flushPromises();
    expect(wrapper.get('.active-object-card > strong').text()).toBe(second.code);
    expect(wrapper.get('.inbound-desk').classes()).toContain('is-detail');
  });

  it('快速切换母单时，迟到详情不会替换当前单的拆单或附件目标', async () => {
    let finishFirst: (value: Awaited<ReturnType<typeof fetchMotherOrder>>) => void = () => {};
    const detail = {
      alreadyExists: true, motherOrderCode: second.code, courierNo: 'SF2', customerName: second.customer,
      processingRequirement: '第二单要求', dueDate: '2026-10-22', goodsName: second.goodsName,
      quantity: 3, weight: 5, ledgerCode: 'STK-2', qrContent: `JG|mother|${second.code}`,
    };
    vi.mocked(fetchMotherOrder).mockImplementation(code => code === first.code
      ? new Promise(resolve => { finishFirst = resolve; }) : Promise.resolve(detail));
    const wrapper = mount(InboundWorkspace);
    await flushPromises();
    await wrapper.findAll('.inbound-card')[1].trigger('click');
    await flushPromises();
    finishFirst({ ...detail, motherOrderCode: first.code, processingRequirement: '迟到的第一单要求' });
    await flushPromises();
    expect(wrapper.get('.active-object-card > strong').text()).toBe(second.code);
    expect(wrapper.text()).toContain('第二单要求');
    expect(wrapper.text()).not.toContain('迟到的第一单要求');
    expect(wrapper.getComponent({ name: 'MotherSplitPanel' }).props('motherOrderCode')).toBe(second.code);
  });
});
