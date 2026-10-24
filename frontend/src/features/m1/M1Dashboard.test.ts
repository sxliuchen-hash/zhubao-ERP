import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import type { TraceTarget } from '../scan/traceTarget';
import type { InboundOrder } from '../../data/m1PrototypeData';
import {
  fetchInboundWorkspace,
  fetchProductionWorkspace,
  fetchQualityWorkspace,
} from '../../services/businessWorkspaceApi';
import { fetchCustomers } from '../../services/masterDataApi';
import {
  completeDeskTask,
  createDeskTask,
  fetchDeskAssignees,
  fetchDeskTasks,
  type DeskTask,
} from '../../services/deskTaskApi';
import M1Dashboard from './M1Dashboard.vue';

vi.mock('../../services/businessWorkspaceApi', () => ({
  fetchInboundWorkspace: vi.fn(),
  fetchProductionWorkspace: vi.fn(),
  fetchQualityWorkspace: vi.fn(),
}));
vi.mock('../../services/deskTaskApi', () => ({
  fetchDeskTasks: vi.fn(),
  fetchDeskAssignees: vi.fn(),
  createDeskTask: vi.fn(),
  completeDeskTask: vi.fn(),
  cancelDeskTask: vi.fn(),
}));
vi.mock('../../services/masterDataApi', () => ({
  fetchCustomers: vi.fn(),
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

function deskTask(overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    taskCode: 'DT-20261022-0001',
    kind: 'MODELING',
    title: '戒托建模',
    note: '按来图',
    customerId: 1001,
    customerName: '恒信珠宝',
    motherOrderCode: '',
    assigneeUsername: 'designer',
    assigneeName: '建模员',
    dueDate: '2020-01-01',
    status: 'OPEN',
    creatorUsername: 'sales',
    createdAt: '2026-10-22T00:00:00Z',
    requestId: 'create-1',
    version: 1,
    completedAt: '',
    completedBy: '',
    resultNote: '',
    completeRequestId: '',
    cancelledAt: '',
    cancelledBy: '',
    cancelReason: '',
    cancelRequestId: '',
    ...overrides,
  };
}

describe('M1Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchDeskTasks).mockResolvedValue([]);
    vi.mocked(fetchDeskAssignees).mockResolvedValue([]);
    vi.mocked(fetchCustomers).mockResolvedValue([]);
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

    expect(wrapper.text()).toContain('业务单据');
    expect(wrapper.text()).toContain('当前处理单');
    expect(wrapper.text()).toContain('母单记录');
    expect(wrapper.text()).not.toContain('分派责任人');
    expect(wrapper.findAll('.mission-kpi-grid article')).toHaveLength(4);
    expect(wrapper.find('.active-record-code strong').text()).toBe('MD-API-001');

    const rows = wrapper.findAll('table.floor-dispatch-table tbody tr');
    await rows[1].trigger('click');

    expect(wrapper.find('.active-record-code strong').text()).toBe('MD-API-002');
    expect(wrapper.find('.active-record-panel').text()).toContain('继续处理母单');
    expect(wrapper.find('.active-record-panel').text()).toContain('1.00 g');
  });

  it('工作区提供统一详情入口时仅打开所选母单，保留旧业务与轻任务入口', async () => {
    const openOrder = vi.fn();
    const openTab = vi.fn();
    const wrapper = mount(M1Dashboard, { global: { provide: { 'zhubao-open-order': openOrder, 'zhubao-open-tab': openTab } } });
    await flushPromises();
    await wrapper.findAll('table.floor-dispatch-table tbody tr')[1].trigger('click');
    await wrapper.get('.active-record-panel .primary-button').trigger('click');
    expect(openOrder).toHaveBeenCalledWith({ objectType: 'MOTHER_ORDER', code: 'MD-API-002' });
    expect(openTab).not.toHaveBeenCalled();
    expect(wrapper.findAll('button').some(button => button.attributes('aria-label') === '新建收货／母单')).toBe(true);
    wrapper.unmount();
  });

  it('新首页默认收起旧单据列表，仍可按需恢复旧入口', async () => {
    const wrapper = mount(M1Dashboard, { global: { provide: { 'zhubao-open-orders': vi.fn() }, stubs: { DeskInbox: { template: '<section>本岗订单预览</section>' } } } });
    await flushPromises();
    expect(fetchInboundWorkspace).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('本岗订单预览');
    expect(wrapper.find('.floor-dispatch-table').exists()).toBe(false);
    await wrapper.get('.dashboard-history-toggle').trigger('click');
    await flushPromises();
    expect(fetchInboundWorkspace).toHaveBeenCalledOnce();
    expect(wrapper.find('.floor-dispatch-table').exists()).toBe(true);
    expect(wrapper.text()).toContain('本次旧列表返回的可见记录数');
    wrapper.unmount();
  });

  it('有来料读取菜单但没有有效收货权限时不出现快捷建单', async () => {
    const wrapper = mount(M1Dashboard, { global: { provide: { 'zhubao-visible-tabs': ref(['inbound']), 'zhubao-auth-session': ref({ effectivePermissions: [] }) } } });
    await flushPromises();
    expect(wrapper.findAll('button').some(button => button.attributes('aria-label') === '新建收货／母单')).toBe(false);
    wrapper.unmount();
  });

  it('新建收货优先打开动作容器，不切走工作台', async () => {
    const openIntake = vi.fn();
    const openTab = vi.fn();
    const wrapper = mount(M1Dashboard, { global: { provide: { 'zhubao-open-intake': openIntake, 'zhubao-open-tab': openTab } } });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.attributes('aria-label') === '新建收货／母单')!.trigger('click');
    expect(openIntake).toHaveBeenCalledOnce();
    expect(openTab).not.toHaveBeenCalled();
    wrapper.unmount();
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

  it('guides the first receipt without reporting a successful empty workspace as an error', async () => {
    vi.mocked(fetchInboundWorkspace).mockResolvedValueOnce({ checkpoints: [], orders: [], risks: [] });
    const openTab = vi.fn();
    const traceQuery = ref('旧单号');
    const traceTarget = ref<TraceTarget | null>({ objectType: 'MOTHER_ORDER', code: 'OLD', intent: 'INTAKE', token: 1 });
    const wrapper = mount(M1Dashboard, { global: { provide: {
      'zhubao-open-tab': openTab,
      'zhubao-trace-query': traceQuery,
      'zhubao-trace-target': traceTarget,
    } } });
    await flushPromises();

    expect(wrapper.text()).toContain('还没有母单');
    expect(wrapper.find('.operation-feedback.danger').exists()).toBe(false);
    await wrapper.findAll('button').find(button => button.attributes('aria-label') === '新建收货／母单')!.trigger('click');
    expect(openTab).toHaveBeenCalledWith('inbound');
    expect(traceQuery.value).toBe('');
    expect(traceTarget.value).toBeNull();
  });

  it('continues and prints the selected mother order with its exact identity', async () => {
    const openTab = vi.fn();
    const openLabel = vi.fn();
    const traceTarget = ref<TraceTarget | null>(null);
    const wrapper = mount(M1Dashboard, { global: { provide: {
      'zhubao-open-tab': openTab,
      'zhubao-open-label': openLabel,
      'zhubao-trace-target': traceTarget,
    } } });
    await flushPromises();
    await wrapper.findAll('table tbody tr')[1].trigger('click');
    await wrapper.find('.active-record-panel .primary-button').trigger('click');
    expect(traceTarget.value).toMatchObject({ objectType: 'MOTHER_ORDER', code: 'MD-API-002', intent: 'INTAKE' });
    expect(openTab).toHaveBeenCalledWith('inbound');
    await wrapper.findAll('button').find(button => button.text() === '打印此母单标签')!.trigger('click');
    expect(openLabel).toHaveBeenCalledWith('MD-API-002');
  });

  it('keeps overview counts stable while searching and reloads the latest records', async () => {
    const wrapper = mount(M1Dashboard);
    await flushPromises();
    await wrapper.find('input[aria-label="待办查找"]').setValue('MD-API-002');
    expect(wrapper.find('.mission-kpi-grid article strong').text()).toBe('2单');
    expect(wrapper.findAll('table tbody tr')).toHaveLength(1);

    vi.mocked(fetchInboundWorkspace).mockResolvedValueOnce({ checkpoints: [], orders: [order('MD-NEW', '已入库')], risks: [] });
    await wrapper.findAll('button').find(button => button.text() === '刷新单据')!.trigger('click');
    await flushPromises();
    await wrapper.find('input[aria-label="待办查找"]').setValue('');
    expect(wrapper.text()).toContain('MD-NEW');
    expect(wrapper.find('.mission-kpi-grid article strong').text()).toBe('1单');
  });

  it('only loads permitted modules and hides unavailable action entries', async () => {
    const wrapper = mount(M1Dashboard, { global: { provide: {
      'zhubao-visible-tabs': ref(['dashboard', 'production']),
    } } });
    await flushPromises();

    expect(fetchInboundWorkspace).not.toHaveBeenCalled();
    expect(fetchQualityWorkspace).not.toHaveBeenCalled();
    expect(fetchProductionWorkspace).toHaveBeenCalledOnce();
    expect(fetchDeskTasks).toHaveBeenCalledOnce();
    expect(wrapper.text()).not.toContain('新建收货／母单');
    expect(wrapper.text()).not.toContain('标签打印');
    expect(wrapper.text()).not.toContain('母单记录');
  });

  it('shows assigned light tasks and completes one without hiding business documents', async () => {
    vi.mocked(fetchDeskTasks).mockResolvedValue([deskTask()]);
    const wrapper = mount(M1Dashboard, { global: { provide: {
      'zhubao-auth-session': ref({ username: 'designer', role: 'OPERATOR', effectivePermissions: [] }),
    } } });
    await flushPromises();

    expect(wrapper.text()).toContain('我的待办');
    expect(wrapper.text()).toContain('戒托建模');
    expect(wrapper.text()).toContain('逾期');
    expect(wrapper.text()).toContain('MD-API-001');
    expect(wrapper.text()).not.toContain('新建待办');

    vi.mocked(completeDeskTask).mockResolvedValue(deskTask({ status: 'DONE' }));
    vi.mocked(fetchDeskTasks).mockResolvedValueOnce([]);
    await wrapper.get('input[aria-label="结果说明"]').setValue('模型已交');
    await wrapper.get('table.desk-task-table .table-action').trigger('click');
    await flushPromises();

    expect(completeDeskTask).toHaveBeenCalledWith('DT-20261022-0001', expect.objectContaining({
      expectedVersion: 1,
      resultNote: '模型已交',
    }));
    expect(wrapper.text()).toContain('暂无派给我的待办');
    expect(wrapper.text()).toContain('业务单据');
  });

  it('keeps business documents when the light-task list fails', async () => {
    vi.mocked(fetchDeskTasks).mockRejectedValueOnce(new Error('轻任务待办暂时读不到'));
    const wrapper = mount(M1Dashboard);
    await flushPromises();

    expect(wrapper.text()).toContain('轻任务待办暂时读不到');
    expect(wrapper.text()).toContain('MD-API-001');
    expect(wrapper.text()).toContain('业务单据');
  });

  it('opens a create form for people who can assign tasks', async () => {
    vi.mocked(fetchDeskAssignees).mockResolvedValue([{ username: 'designer', operator: '建模员' }]);
    vi.mocked(fetchCustomers).mockResolvedValue([{
      customerId: 1001, customerCode: 'C-1001', name: '恒信珠宝', aliases: '', contact: '', phone: '', remark: '', enabled: true,
    }]);
    vi.mocked(createDeskTask).mockResolvedValue(deskTask());
    const wrapper = mount(M1Dashboard, { global: { provide: {
      'zhubao-auth-session': ref({ username: 'sales', role: 'SALES', effectivePermissions: ['DESK_TASK_ASSIGN'] }),
    } } });
    await flushPromises();

    const create = wrapper.findAll('button').find((button) => button.text() === '新建待办');
    expect(create).toBeTruthy();
    await create!.trigger('click');
    await flushPromises();
    expect(wrapper.find('select[aria-label="待办类型"]').exists()).toBe(true);
    await wrapper.get('input[aria-label="待办标题"]').setValue('吊坠设计');
    await wrapper.get('input[aria-label="待办期限"]').setValue('2026-11-01');
    await wrapper.get('select[aria-label="待办责任人"]').setValue('designer');
    await wrapper.get('form.desk-task-form').trigger('submit');
    await flushPromises();

    expect(createDeskTask).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'MODELING',
      title: '吊坠设计',
      dueDate: '2026-11-01',
      assigneeUsername: 'designer',
      customerId: null,
    }));
    expect(fetchDeskTasks).toHaveBeenLastCalledWith('issued');
  });
});
