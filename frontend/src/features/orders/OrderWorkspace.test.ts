import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import OrderWorkspace from './OrderWorkspace.vue';
import ScanInput from '../scan/ScanInput.vue';
import { fetchOrderWorkspace } from '../../services/orderWorkspaceApi';
import { queryScanContext } from '../../services/scanContextApi';
import type { ScanContextResponse } from '../../services/scanContextApi';
import type { OrderWorkspacePage, OrderWorkspaceRow } from '../../services/orderWorkspaceTypes';
import { ApiRequestError } from '../../services/apiClient';
import { buildScanContext } from '../scan/scanContextFixture';
import { createWorkspaceState } from '../workspace/workspaceContext';
import type { TraceTarget } from '../scan/traceTarget';

vi.mock('../../services/orderWorkspaceApi', () => ({ fetchOrderWorkspace: vi.fn() }));
vi.mock('../../services/scanContextApi', async () => ({ ...await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi'), queryScanContext: vi.fn() }));
vi.mock('../scan/ScanInput.vue', () => ({ default: { props: ['modelValue'], template: '<div data-scanner />' } }));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
function row(code = 'WO-1', extra: Partial<OrderWorkspaceRow> = {}): OrderWorkspaceRow {
  return { rowKey: `WORK_ORDER|${code}`, objectType: 'WORK_ORDER', code, motherOrderCode: 'MO-1', workOrderCode: code,
    executionRoundCode: null, currentProcess: '抛光', customerId: null, customerName: null, goodsName: '戒指',
    materialCode: 'MAT-1', materialName: null, quantity: '10.5', quantityUnit: '克', weight: null, weightUnit: null,
    dueDate: null, businessStatus: '在制', facet: 'IN_PROCESS', facetNote: null, processPercent: null,
    processProgressVersion: null, processFormallyDelivered: false, operatorName: null, updatedAt: null, ...extra };
}
function page(items = [row()], extra: Partial<OrderWorkspacePage> = {}): OrderWorkspacePage {
  return { items, page: 1, size: 20, totalRows: items.length, facetCounts: { ALL: items.length, IN_PROCESS: items.length },
    availableViews: ['ORDERS', 'PRODUCTION', 'QUALITY'], generatedAt: '', ...extra };
}
function context(code: string) { return buildScanContext({ objectType: 'WORK_ORDER', code, motherOrderCode: 'MO-1', requirement: `要求-${code}` }); }
function mountWorkspace(provide: Record<string, unknown> = {}) {
  return mount(OrderWorkspace, { global: { provide: { 'zhubao-visible-tabs': ref(['production']), ...provide } } });
}
async function select(wrapper: ReturnType<typeof mountWorkspace>, code: string) {
  await wrapper.get(`.order-table [aria-label="查看${code}"]`).trigger('click');
  await flushPromises();
}

beforeEach(() => {
  vi.mocked(fetchOrderWorkspace).mockReset().mockResolvedValue(page());
  vi.mocked(queryScanContext).mockReset().mockImplementation(async input => context(input.content));
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
});

describe('统一订单列表与详情', () => {
  it('列表默认收起重复扫码区，当前扫码请求可展开，原目标绑定仍保留', async () => {
    const wrapper = mountWorkspace(); await flushPromises();
    expect(wrapper.get('.order-scan-tools').attributes('open')).toBeUndefined();
    wrapper.getComponent(ScanInput).vm.$emit('activate-camera'); await flushPromises();
    expect(wrapper.get('.order-scan-tools').attributes('open')).toBeDefined();
    wrapper.unmount();
  });
  it('标签入口同时核对菜单与有效权限，子单保持精确类型', async () => {
    const auth = ref({ effectivePermissions: [] as string[] });
    const open = vi.fn();
    const wrapper = mountWorkspace({ 'zhubao-auth-session': auth, 'zhubao-visible-tabs': ref(['label']), 'zhubao-open-label-target': open });
    await flushPromises(); await select(wrapper, 'WO-1');
    expect(wrapper.findAll('button').some(button => button.text() === '预览本单标签')).toBe(false);
    auth.value.effectivePermissions.push('LABEL_PRINT'); await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '预览本单标签')!.trigger('click');
    expect(open).toHaveBeenCalledWith({ objectType: 'WORK_ORDER', code: 'WO-1' });
    wrapper.unmount();
  });
  it('发货核对入口按真实库存菜单及有效权限开放，并传递精确目标', async () => {
    const auth = ref({ effectivePermissions: [] as string[] });
    const open = vi.fn();
    const wrapper = mountWorkspace({ 'zhubao-auth-session': auth, 'zhubao-visible-tabs': ref(['inventory']), 'zhubao-open-shipping': open });
    await flushPromises();
    await select(wrapper, 'WO-1');
    expect(wrapper.findAll('button').some(button => button.text() === '核对库存与发货')).toBe(false);
    auth.value.effectivePermissions.push('BUSINESS_OUTBOUND_SHIPMENT');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '核对库存与发货')!.trigger('click');
    expect(open).toHaveBeenCalledWith({ objectType: 'WORK_ORDER', code: 'WO-1' });
    wrapper.unmount();
  });
  it('初检已提交仍可按放行有效权限进入单内质检核对，未授权时不出现入口', async () => {
    const auth = ref({ effectivePermissions: [] as string[] });
    const open = vi.fn();
    const wrapper = mountWorkspace({ 'zhubao-auth-session': auth, 'zhubao-visible-tabs': ref(['quality']), 'zhubao-open-quality': open });
    await flushPromises();
    await select(wrapper, 'WO-1');
    expect(wrapper.findAll('button').some(button => button.text() === '质检、返工与放行')).toBe(false);
    auth.value.effectivePermissions.push('BUSINESS_QUALITY_RELEASE');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '质检、返工与放行')!.trigger('click');
    expect(open).toHaveBeenCalledWith({ objectType: 'WORK_ORDER', code: 'WO-1' });
    wrapper.unmount();
  });
  it('工序动作按有效权限开放，传递规范目标、办理方式与改单母单', async () => {
    const auth = ref({ effectivePermissions: [] as string[] });
    const openProduction = vi.fn();
    const openChange = vi.fn();
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'WO-1', motherOrderCode: 'MO-1', actions: [{ code: 'REPORT_PROCESS', allowed: true, blockKind: null, blockReason: null }] }));
    const wrapper = mountWorkspace({ 'zhubao-auth-session': auth, 'zhubao-visible-tabs': ref(['progress']), 'zhubao-open-production': openProduction, 'zhubao-open-order-change': openChange });
    await flushPromises();
    await select(wrapper, 'WO-1');
    expect(wrapper.findAll('button').some(button => button.text() === '更新工序进度')).toBe(false);
    expect(wrapper.findAll('button').some(button => button.text() === '打开岗位交工')).toBe(false);
    auth.value.effectivePermissions.push('BUSINESS_PRODUCTION_REPORT');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '更新工序进度')!.trigger('click');
    expect(openProduction).toHaveBeenLastCalledWith({ objectType: 'WORK_ORDER', code: 'WO-1' }, 'progress');
    await wrapper.findAll('button').find(button => button.text() === '打开岗位交工')!.trigger('click');
    expect(openProduction).toHaveBeenLastCalledWith({ objectType: 'WORK_ORDER', code: 'WO-1' }, 'station');
    await wrapper.findAll('button').find(button => button.text() === '改单办理')!.trigger('click');
    expect(openChange).toHaveBeenCalledWith({ objectType: 'WORK_ORDER', code: 'WO-1', motherOrderCode: 'MO-1' });
    wrapper.unmount();
  });
  it('收货按钮同时要求有效权限，保存后的只读刷新失败不会重新打开写表单', async () => {
    const auth = ref({ effectivePermissions: [] as string[] });
    const refresh = ref(0);
    const openIntake = vi.fn();
    const wrapper = mountWorkspace({ 'zhubao-auth-session': auth, 'zhubao-visible-tabs': ref(['inbound']), 'zhubao-order-refresh': refresh, 'zhubao-open-intake': openIntake });
    await flushPromises();
    expect(wrapper.findAll('button').some(button => button.text() === '新建收货')).toBe(false);
    auth.value.effectivePermissions.push('BUSINESS_INBOUND_CONFIRM');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '新建收货')!.trigger('click');
    expect(openIntake).toHaveBeenCalledOnce();
    vi.mocked(fetchOrderWorkspace).mockRejectedValueOnce(new Error('列表刷新失败'));
    refresh.value += 1;
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('列表刷新失败');
    expect(openIntake).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
  it('读取 Q1 原始小数、总数和分类，不自动选择第一行，不把未知进度当 0', async () => {
    vi.mocked(fetchOrderWorkspace).mockResolvedValue(page([row(), row('WO-2', { processPercent: 100 })], { totalRows: 42, facetCounts: { ALL: 42, IN_PROCESS: 35 } }));
    const wrapper = mountWorkspace();
    await flushPromises();
    expect(fetchOrderWorkspace).toHaveBeenCalledWith(expect.objectContaining({ view: 'ORDERS', page: 1, size: 20 }));
    expect(wrapper.text()).toContain('共 42 项目标');
    expect(wrapper.text()).toContain('10.5 克');
    expect(wrapper.text()).toContain('未记录工序进度');
    expect(wrapper.text()).toContain('工序进度 100%');
    expect(wrapper.text()).toContain('本工序未正式交工');
    expect(wrapper.get('.order-table tbody tr:first-child').text()).not.toContain('0%');
    expect(queryScanContext).not.toHaveBeenCalled();
    expect(wrapper.find('[aria-label="订单详情"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="订单视图"]').text()).not.toContain('质量作业');
    wrapper.unmount();
  });

  it('选择精确行后查询，A 的迟到响应不能覆盖 B；共享选择保留 B 的身份', async () => {
    const old = deferred<ScanContextResponse>();
    vi.mocked(fetchOrderWorkspace).mockResolvedValue(page([row(), row('WO-2')]));
    vi.mocked(queryScanContext).mockImplementation(input => input.content === 'WO-1' ? old.promise : Promise.resolve(context('WO-2')));
    const workspace = createWorkspaceState();
    const wrapper = mountWorkspace({ 'zhubao-workspace-context': workspace });
    await flushPromises();
    await select(wrapper, 'WO-1');
    expect(workspace.selection.value).toBeNull();
    await select(wrapper, 'WO-2');
    expect(queryScanContext).toHaveBeenLastCalledWith({ content: 'WO-2', objectType: 'WORK_ORDER', intent: 'LOOKUP' });
    old.resolve(context('WO-1'));
    await flushPromises();
    expect(wrapper.get('[aria-label="订单详情"] h2').text()).toContain('WO-2');
    expect(workspace.selection.value?.code).toBe('WO-2');
    wrapper.unmount();
  });

  it('修改筛选立即撤下旧详情和旧响应，分页使用服务端页信息', async () => {
    const old = deferred<OrderWorkspacePage>();
    const workspace = createWorkspaceState();
    vi.mocked(fetchOrderWorkspace).mockResolvedValueOnce(page([row()], { totalRows: 21 })).mockReturnValueOnce(old.promise).mockResolvedValueOnce(page([row('WO-NEW')]));
    const wrapper = mountWorkspace({ 'zhubao-workspace-context': workspace });
    await flushPromises();
    await select(wrapper, 'WO-1');
    await wrapper.get('.order-pagination button:last-child').trigger('click');
    expect(fetchOrderWorkspace).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }));
    expect(workspace.selection.value).toBeNull();
    expect(wrapper.find('[aria-label="订单详情"]').exists()).toBe(false);
    await wrapper.get('[aria-label="订单搜索"]').setValue('NEW');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    old.resolve(page([row('WO-OLD')], { page: 2 }));
    await flushPromises();
    expect(wrapper.text()).toContain('WO-NEW');
    expect(wrapper.text()).not.toContain('WO-OLD');
    expect(fetchOrderWorkspace).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, query: 'NEW' }));
    wrapper.unmount();
  });

  it('返回和重入保留筛选、页码、视图与位置，不恢复可提交目标', async () => {
    const workspace = createWorkspaceState();
    workspace.rememberReturn({ section: 'orders', query: '戒指', customerId: '7', process: '抛光', facet: 'IN_PROCESS', dueBefore: '2026-10-23', page: 2, scrollTop: 123, view: 'PRODUCTION' });
    vi.mocked(fetchOrderWorkspace).mockResolvedValue(page([row()], { page: 2, totalRows: 21 }));
    const wrapper = mountWorkspace({ 'zhubao-workspace-context': workspace });
    await flushPromises();
    expect(fetchOrderWorkspace).toHaveBeenCalledWith(expect.objectContaining({ page: 2, view: 'PRODUCTION', query: '戒指', customerId: '7', dueBefore: '2026-10-23' }));
    await select(wrapper, 'WO-1');
    await wrapper.get('[aria-label="订单详情"] > button').trigger('click');
    expect(wrapper.get('[aria-label="订单搜索"]').element).toHaveProperty('value', '戒指');
    expect(workspace.returnLocation.value?.page).toBe(2);
    expect(workspace.selection.value).toBeNull();
    wrapper.unmount();
  });

  it('同码多单先人工选择，确认前没有共享目标或业务按钮', async () => {
    vi.mocked(queryScanContext).mockResolvedValueOnce(buildScanContext({ requiresSelection: true, relatedTargets: [
      { objectType: 'MOTHER_ORDER', code: 'MO-1', motherOrderCode: 'MO-1', quantity: null, quantityUnit: null, requirement: null },
      { objectType: 'MOTHER_ORDER', code: 'MO-2', motherOrderCode: 'MO-2', quantity: null, quantityUnit: null, requirement: null },
    ] })).mockResolvedValueOnce(buildScanContext({ objectType: 'MOTHER_ORDER', code: 'MO-2' }));
    const workspace = createWorkspaceState();
    const wrapper = mountWorkspace({ 'zhubao-workspace-context': workspace });
    await flushPromises();
    wrapper.getComponent(ScanInput).vm.$emit('recognize', { content: 'SF-1' });
    await flushPromises();
    expect(queryScanContext).toHaveBeenCalledTimes(1);
    expect(workspace.selection.value).toBeNull();
    expect(wrapper.find('.order-legacy-actions[aria-label]').exists()).toBe(false);
    await wrapper.get('[aria-label="订单详情"] > button:last-child').trigger('click');
    await flushPromises();
    expect(queryScanContext).toHaveBeenLastCalledWith({ content: 'MO-2', objectType: 'MOTHER_ORDER', intent: 'LOOKUP' });
    expect(workspace.selection.value?.code).toBe('MO-2');
    wrapper.unmount();
  });

  it('执行轮次被解析为原子单时拒绝详情和动作，不替换轮次身份', async () => {
    vi.mocked(fetchOrderWorkspace).mockResolvedValue(page([row('FG-1', { executionRoundCode: 'FG-1', workOrderCode: 'WO-1' })]));
    vi.mocked(queryScanContext).mockResolvedValue(context('WO-1'));
    const workspace = createWorkspaceState();
    const wrapper = mountWorkspace({ 'zhubao-workspace-context': workspace });
    await flushPromises();
    await select(wrapper, 'FG-1');
    expect(wrapper.text()).toContain('尚不能准确解析');
    expect(wrapper.find('.order-detail-content').exists()).toBe(false);
    expect(workspace.selection.value).toBeNull();
    wrapper.unmount();
  });

  it('权限、业务允许和真实菜单同时满足才给出原业务办理入口', async () => {
    const current = context('WO-1');
    current.actions = [{ code: 'REPORT_PROCESS', allowed: true, blockKind: null, blockReason: null }];
    vi.mocked(queryScanContext).mockResolvedValue(current);
    const target = ref<TraceTarget | null>(null);
    const openTab = vi.fn();
    const auth = ref({ effectivePermissions: [] as string[] });
    const wrapper = mountWorkspace({ 'zhubao-auth-session': auth, 'zhubao-trace-target': target, 'zhubao-open-tab': openTab });
    await flushPromises();
    await select(wrapper, 'WO-1');
    expect(wrapper.get('[aria-label="原业务办理入口"]').text()).not.toContain('打开岗位交工');
    auth.value.effectivePermissions.push('BUSINESS_PRODUCTION_REPORT');
    await flushPromises();
    await wrapper.get('[aria-label="原业务办理入口"] button').trigger('click');
    expect(target.value).toMatchObject({ objectType: 'WORK_ORDER', code: 'WO-1', intent: 'PRODUCTION' });
    expect(openTab).toHaveBeenCalledWith('production');
    wrapper.unmount();
  });

  it('空结果、无权和网络失败分别展示；不回退样例数据', async () => {
    vi.mocked(fetchOrderWorkspace).mockResolvedValueOnce(page([])).mockRejectedValueOnce(new ApiRequestError('拒绝查询', 403, 40301)).mockRejectedValueOnce(new Error('网络中断'));
    const wrapper = mountWorkspace();
    await flushPromises();
    expect(wrapper.text()).toContain('当前条件下暂无订单');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('无权限：拒绝查询');
    expect(wrapper.text()).not.toContain('当前条件下暂无订单');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toBe('网络中断');
    expect(wrapper.find('.order-card').exists()).toBe(false);
    wrapper.unmount();
  });

  it('全局 LOOKUP 只定位一次；卸载后迟到响应不恢复共享选择', async () => {
    const pending = deferred<ScanContextResponse>();
    vi.mocked(queryScanContext).mockReturnValue(pending.promise);
    const workspace = createWorkspaceState();
    const wrapper = mountWorkspace({ 'zhubao-workspace-context': workspace, 'zhubao-trace-target': ref({ objectType: 'WORK_ORDER', code: 'WO-1', intent: 'LOOKUP', token: 9 }) });
    await flushPromises();
    expect(queryScanContext).toHaveBeenCalledTimes(1);
    wrapper.unmount();
    pending.resolve(context('WO-1'));
    await flushPromises();
    expect(workspace.selection.value).toBeNull();
  });
});
