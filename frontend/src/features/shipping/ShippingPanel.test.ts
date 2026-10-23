import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../../services/apiClient';
import ShippingPanel from './ShippingPanel.vue';
import { queryScanContext, saveProgressRecord } from '../../services/scanContextApi';
import { shipFinishedStock } from '../../services/businessOperationApi';
import { bindEvidence, uploadEvidenceFile, type StoredEvidenceFile } from '../../services/evidenceApi';
import { buildScanContext } from '../scan/scanContextFixture';
import type { ScanBalanceCandidate, ScanContextResponse } from '../../services/scanContextApi';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn(), saveProgressRecord: vi.fn() };
});

vi.mock('../../services/businessOperationApi', () => ({
  shipFinishedStock: vi.fn(),
}));

vi.mock('../../services/evidenceApi', () => ({
  fetchEvidenceBoard: vi.fn().mockResolvedValue({ links: [], historical: [] }),
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));

function balance(partial: Partial<ScanBalanceCandidate> = {}): ScanBalanceCandidate {
  return {
    materialCode: 'AU9999',
    warehouseCode: 'FINISHED-CUSTOMER',
    ownerType: 'CUSTOMER',
    ownerCustomerId: 1001,
    customerId: 1001,
    motherOrderCode: 'MD-1',
    workOrderCode: 'ZD-1',
    stockStatus: 'FINISHED',
    quantity: 10,
    weight: 20,
    ...partial,
  };
}

function contextOf(input: {
  code?: string;
  customerName?: string;
  balances?: ScanBalanceCandidate[];
  selection?: boolean;
  objectType?: 'WORK_ORDER' | 'OUTBOUND_ORDER';
  outboundOrderCode?: string | null;
  actions?: ScanContextResponse['actions'];
  shipments?: ScanContextResponse['progress']['shipments'];
  workOrderCode?: string | null;
}): ScanContextResponse {
  const balances = input.balances ?? [balance()];
  const selection = input.selection ?? balances.length > 1;
  const objectType = input.objectType ?? 'WORK_ORDER';
  const code = input.code ?? 'ZD-1';
  const base = buildScanContext({
    objectType,
    code,
    motherOrderCode: 'MD-1',
    workOrderCode: input.workOrderCode ?? (objectType === 'WORK_ORDER' ? code : 'ZD-1'),
    customerName: input.customerName ?? '恒信珠宝',
    requirement: '内壁刻字',
    balanceCandidates: balances,
    balanceSelectionRequired: selection,
    actions: input.actions,
    outboundOrderCode: input.outboundOrderCode,
  });
  return {
    ...base,
    order: { ...base.order, customerId: 1001, customerName: input.customerName ?? '恒信珠宝', requirement: '内壁刻字' },
    operationContext: {
      ...base.operationContext,
      balanceCandidates: balances,
      balanceSelectionRequired: selection,
      selectedBalance: selection ? null : (balances[0] ?? null),
      formalOperations: [],
    },
    progress: { ...base.progress, shipments: input.shipments ?? [] },
  };
}

function fileOf(name: string) {
  const file = new File(['photo'], name, { type: 'image/jpeg' });
  Object.defineProperty(file, 'size', { value: 4 });
  return file;
}

async function openPanel(context: ScanContextResponse, content = 'JG|work_order|ZD-1') {
  vi.mocked(queryScanContext).mockResolvedValueOnce(context);
  const wrapper = mount(ShippingPanel, { props: { title: '确认发货', intro: '扫标签后发货' } });
  await wrapper.get('[aria-label="发货贴纸"]').setValue(content);
  await wrapper.get('[aria-label="识别发货对象"]').trigger('click');
  await flushPromises();
  return wrapper;
}

async function pickFile(wrapper: ReturnType<typeof mount>, name: string) {
  const input = wrapper.get('[aria-label="选择证据文件"]');
  Object.defineProperty(input.element, 'files', { configurable: true, value: [fileOf(name)] });
  await input.trigger('change');
}

describe('发货面板', () => {
  beforeEach(() => {
    vi.mocked(queryScanContext).mockReset();
    vi.mocked(shipFinishedStock).mockReset();
    vi.mocked(saveProgressRecord).mockReset();
    vi.mocked(uploadEvidenceFile).mockReset();
    vi.mocked(bindEvidence).mockReset();
  });

  it('复核不扣库，切换仓库撤下旧复核并清空数量和重量', async () => {
    const wrapper = await openPanel(contextOf({ balances: [balance({ warehouseCode: 'WH-A' }), balance({ warehouseCode: 'WH-B' })] }));
    await wrapper.findAll('[aria-label^="选择余额"]')[0].trigger('click');
    await wrapper.get('[aria-label="出货单号"]').setValue('OUT-REVIEW');
    await wrapper.get('[aria-label="物流单号"]').setValue('客户自提');
    await wrapper.get('[aria-label="本次发货数量"]').setValue('10.5');
    await wrapper.get('[aria-label="本次发货重量"]').setValue('1.25');
    await wrapper.get('[aria-label="核对发货"]').trigger('click');
    expect(wrapper.get('[aria-label="发货复核"]').text()).toContain('10.5');
    expect(shipFinishedStock).not.toHaveBeenCalled();
    await wrapper.findAll('[aria-label^="选择余额"]')[1].trigger('click');
    expect(wrapper.find('[aria-label="发货复核"]').exists()).toBe(false);
    expect(wrapper.get('[aria-label="本次发货数量"]').element).toHaveProperty('value', '');
    expect(wrapper.get('[aria-label="本次发货重量"]').element).toHaveProperty('value', '');
    wrapper.unmount();
  });

  it('定位目标与库存维度分别核对，失效仓库不能退回第一笔，轮次不转原单', async () => {
    const context = contextOf({ balances: [balance({ warehouseCode: 'WH-A' })] });
    vi.mocked(queryScanContext).mockResolvedValueOnce(context);
    const wrapper = mount(ShippingPanel, { props: { title: '发货', intro: '', embedded: true, initialTarget: { objectType: 'WORK_ORDER', code: 'ZD-1' }, initialBalance: balance({ warehouseCode: 'WH-OLD' }) } });
    await flushPromises();
    expect(wrapper.find('[aria-label="发货贴纸"]').exists()).toBe(false);
    expect(wrapper.findAll('[aria-label^="选择余额"][aria-pressed="true"]')).toHaveLength(0);
    expect(wrapper.text()).toContain('原库存维度已变化或当前不可发');
    wrapper.unmount();
    vi.mocked(queryScanContext).mockResolvedValueOnce(context);
    const round = mount(ShippingPanel, { props: { title: '发货', intro: '', embedded: true, initialTarget: { objectType: 'WORK_ORDER', code: 'FG-1-R2' } } });
    await flushPromises();
    expect(round.text()).toContain('发货目标与当前单据或执行轮次不一致');
    expect(round.find('[aria-label="核对发货"]').exists()).toBe(false);
    expect(round.find('[aria-label="过程说明"]').exists()).toBe(false);
    round.unmount();
  });

  it('超时锁住原完整维度和小数数量，重试不读取后来修改的物流或数量', async () => {
    const context = contextOf({ balances: [balance({ quantity: 12.75 })] });
    context.order.quantityUnit = '克';
    const wrapper = await openPanel(context);
    await wrapper.get('[aria-label="出货单号"]').setValue('OUT-DECIMAL');
    await wrapper.get('[aria-label="物流单号"]').setValue('客户自提');
    await wrapper.get('[aria-label="本次发货数量"]').setValue('10.5');
    await wrapper.get('[aria-label="本次发货重量"]').setValue('10.5');
    await wrapper.get('[aria-label="核对发货"]').trigger('click');
    vi.mocked(shipFinishedStock).mockRejectedValueOnce(new ApiRequestError('响应超时', 408, 408));
    await wrapper.findAll('button').find(button => button.text() === '确认发货')!.trigger('click');
    await flushPromises();
    const original = vi.mocked(shipFinishedStock).mock.calls[0];
    expect(wrapper.get('fieldset').element).toHaveProperty('disabled', true);
    await wrapper.get('[aria-label="本次发货数量"]').setValue('1');
    await wrapper.get('[aria-label="物流单号"]').setValue('后改单号');
    vi.mocked(shipFinishedStock).mockResolvedValueOnce({ shipmentCode: 'OUT-DECIMAL', workOrderCode: 'ZD-1', ledgerCode: 'STK-DECIMAL', fromWarehouseCode: 'FINISHED-CUSTOMER', toWarehouseCode: 'SHIPPED-CUSTOMER', fromStockStatus: 'FINISHED', toStockStatus: 'SHIPPED', quantity: 10.5, weight: 10.5, logisticsNo: '客户自提', operator: '当前库管员' });
    vi.mocked(queryScanContext).mockRejectedValueOnce(new Error('刷新失败'));
    await wrapper.findAll('button').find(button => button.text() === '原请求重试')!.trigger('click');
    await flushPromises();
    expect(vi.mocked(shipFinishedStock).mock.calls[1]).toEqual(original);
    expect(original[1]).toMatchObject({ quantity: '10.5', weight: '10.5', quantityUnit: '克', logisticsNo: '客户自提' });
    expect(wrapper.find('[aria-label="核对发货"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('发货已成功，刷新失败');
    expect(wrapper.emitted('saved')).toHaveLength(1);
    wrapper.unmount();
  });

  it('物流扫码只填运单号，保留业务目标、表单和待上传照片', async () => {
    const wrapper = await openPanel(contextOf({ balances: [balance()] }));
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('内壁刻字');
    expect(wrapper.get('[aria-label^="选择余额"]').attributes('aria-pressed')).toBe('true');
    await wrapper.get('[aria-label="出货单号"]').setValue('OUT-1');
    await wrapper.get('[aria-label="本次发货数量"]').setValue('4');
    await pickFile(wrapper, 'pack.jpg');
    expect(wrapper.text()).toContain('pack.jpg');
    expect(wrapper.text()).toContain('尚无正式单号');

    vi.mocked(saveProgressRecord).mockResolvedValue({
      code: 'PG-1',
      targetType: 'WORK_ORDER',
      targetCode: 'ZD-1',
      motherOrderCode: 'MD-1',
      stage: 'SHIPPING',
      process: '',
      clauseNo: '',
      note: '正在打包',
      operatorId: 'u1',
      operatorName: '何琳',
      operatorRole: 'WAREHOUSE_KEEPER',
      supplementary: false,
      recordedAt: '2026-10-12T10:00:00',
      requestId: 'req-note',
    });
    await wrapper.get('[aria-label="过程说明"]').setValue('正在打包');
    await wrapper.findAll('button').find((button) => button.text() === '保存过程说明')!.trigger('click');
    await flushPromises();
    expect(saveProgressRecord).toHaveBeenCalledTimes(1);
    expect(shipFinishedStock).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="物流扫码"]').setValue('YT12345678');
    await wrapper.get('[aria-label="填写物流单号"]').trigger('click');
    await wrapper.get('[aria-label="物流扫码"]').setValue('JG|work_order|ZD-9');
    await wrapper.get('[aria-label="填写物流单号"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[aria-label="物流单号"]').element).toHaveProperty('value', 'YT12345678');
    expect(wrapper.get('[aria-label="出货单号"]').element).toHaveProperty('value', 'OUT-1');
    expect(wrapper.get('[aria-label="本次发货数量"]').element).toHaveProperty('value', '4');
    expect(wrapper.text()).toContain('pack.jpg');
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('未识别的物流单号');
    expect(wrapper.get('[aria-label^="选择余额"]').attributes('aria-pressed')).toBe('true');
    expect(queryScanContext).toHaveBeenCalledTimes(1);
    expect(shipFinishedStock).not.toHaveBeenCalled();
  });

  it('多余额必须点选，切换标签后旧响应和旧照片都不能进入新单', async () => {
    const wrapper = await openPanel(contextOf({
      code: 'ZD-A',
      customerName: '恒信甲',
      balances: [balance({ workOrderCode: 'ZD-A' })],
    }), 'JG|work_order|ZD-A');
    await pickFile(wrapper, 'old-pack.jpg');
    expect(wrapper.text()).toContain('old-pack.jpg');

    let finishStale: (value: ScanContextResponse) => void = () => {};
    vi.mocked(queryScanContext).mockImplementationOnce(() => new Promise((resolve) => {
      finishStale = resolve;
    }));
    await wrapper.get('[aria-label="发货贴纸"]').setValue('JG|work_order|ZD-B');
    expect(wrapper.text()).not.toContain('old-pack.jpg');
    const pending = wrapper.get('[aria-label="识别发货对象"]').trigger('click');
    await flushPromises();
    await wrapper.get('[aria-label="发货贴纸"]').setValue('JG|work_order|ZD-C');
    finishStale(contextOf({ code: 'ZD-B', customerName: '雅辰饰品', balances: [balance({ workOrderCode: 'ZD-B' })] }));
    await pending;
    await flushPromises();
    expect(wrapper.text()).not.toContain('雅辰饰品');
    expect(wrapper.text()).not.toContain('old-pack.jpg');

    vi.mocked(queryScanContext).mockResolvedValueOnce(contextOf({
      code: 'ZD-C',
      customerName: '瑞丽金行',
      selection: true,
      balances: [
        balance({ workOrderCode: 'ZD-C', warehouseCode: 'WH-A' }),
        balance({ workOrderCode: 'ZD-C', warehouseCode: 'WH-B', quantity: 4, weight: 8 }),
      ],
    }));
    await wrapper.get('[aria-label="识别发货对象"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('瑞丽金行');
    expect(wrapper.text()).toContain('不会默认第一笔');
    expect(wrapper.findAll('[aria-label^="选择余额"][aria-pressed="true"]')).toHaveLength(0);
    await wrapper.get('[aria-label="核对发货"]').trigger('click');
    expect(wrapper.text()).toContain('请选择完整的物料、仓库、货权和库存状态');
    expect(shipFinishedStock).not.toHaveBeenCalled();
  });

  it('点选的余额才会发货，成功后的刷新和附件失败不会再次扣库', async () => {
    const chosen = balance({ warehouseCode: 'WH-B', workOrderCode: 'ZD-2', quantity: 4, weight: 8 });
    const wrapper = await openPanel(contextOf({
      code: 'ZD-2',
      selection: true,
      balances: [balance({ warehouseCode: 'WH-A', workOrderCode: 'ZD-2' }), chosen],
    }), 'JG|work_order|ZD-2');
    await wrapper.findAll('[aria-label^="选择余额"]').find(button => button.attributes('aria-label')?.includes('仓库WH-B'))!.trigger('click');
    await wrapper.get('[aria-label="出货单号"]').setValue('OUT-2');
    await wrapper.get('[aria-label="物流单号"]').setValue('YT20000001');
    await wrapper.get('[aria-label="本次发货数量"]').setValue('4');
    await wrapper.get('[aria-label="本次发货重量"]').setValue('8');
    let queries = 0;
    vi.mocked(queryScanContext).mockImplementation(async () => {
      queries += 1;
      throw new Error('页面刷新失败');
    });
    vi.mocked(shipFinishedStock).mockResolvedValue({
      shipmentCode: 'OUT-2',
      workOrderCode: 'ZD-2',
      ledgerCode: 'STK-2',
      fromWarehouseCode: 'WH-B',
      toWarehouseCode: 'SHIPPED-CUSTOMER',
      fromStockStatus: 'FINISHED',
      toStockStatus: 'SHIPPED',
      quantity: 4,
      weight: 8,
      logisticsNo: 'YT20000001',
      operator: '何琳',
    });
    await wrapper.get('[aria-label="核对发货"]').trigger('click');
    expect(shipFinishedStock).not.toHaveBeenCalled();
    await wrapper.findAll('button').find((button) => button.text() === '确认发货')!.trigger('click');
    await flushPromises();

    expect(shipFinishedStock).toHaveBeenCalledTimes(1);
    expect(shipFinishedStock).toHaveBeenCalledWith(
      expect.objectContaining({
        operationContext: expect.objectContaining({ warehouseCode: 'WH-B', workOrderCode: 'ZD-2', materialCode: 'AU9999' }),
      }),
      expect.objectContaining({
        shipmentCode: 'OUT-2',
        logisticsNo: 'YT20000001',
        quantity: '4',
        weight: '8',
        requestId: expect.stringMatching(/^ship-/),
      }),
    );
    expect(wrapper.text()).toContain('发货已写入');
    expect(wrapper.text()).toContain('发货已成功，刷新失败');
    expect(wrapper.text()).not.toContain('确认发货失败');

    await wrapper.get('[aria-label="重试刷新"]').trigger('click');
    await flushPromises();
    expect(shipFinishedStock).toHaveBeenCalledTimes(1);
    expect(queries).toBe(2);

    vi.mocked(uploadEvidenceFile).mockRejectedValueOnce(new Error('上传失败'));
    await pickFile(wrapper, 'after.jpg');
    await wrapper.findAll('button').find((button) => button.text() === '上传并关联')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('上传失败');
    expect(shipFinishedStock).toHaveBeenCalledTimes(1);

    vi.mocked(uploadEvidenceFile).mockResolvedValue({ code: 'ATT-1', fileName: 'after.jpg' } as StoredEvidenceFile);
    vi.mocked(bindEvidence).mockResolvedValue({ linkCode: 'L1', attachmentCode: 'ATT-1', scene: 'PACK' });
    await wrapper.findAll('button').find((button) => button.text() === '重试')!.trigger('click');
    await flushPromises();
    expect(bindEvidence).toHaveBeenCalledWith(expect.objectContaining({
      targetType: 'OUTBOUND_ORDER',
      targetCode: 'OUT-2',
      stage: 'SHIPPING',
      scene: 'PACK',
      operationType: 'outbound_shipment',
      operationCode: 'OUT-2',
    }));
    expect(shipFinishedStock).toHaveBeenCalledTimes(1);
  });

  it('结果未知时用原请求和原业务参数再确认一次', async () => {
    const wrapper = await openPanel(contextOf({ balances: [balance()] }));
    vi.mocked(queryScanContext).mockResolvedValue(contextOf({ balances: [balance()] }));
    await wrapper.get('[aria-label="出货单号"]').setValue('OUT-REPLAY');
    await wrapper.get('[aria-label="物流公司"]').setValue('顺丰');
    await wrapper.get('[aria-label="物流单号"]').setValue('YT30000001');
    await wrapper.get('[aria-label="本次发货数量"]').setValue('2');
    await wrapper.get('[aria-label="本次发货重量"]').setValue('5');
    const calls: Array<Record<string, unknown>> = [];
    vi.mocked(shipFinishedStock).mockImplementation(async (_row, extra) => {
      calls.push({ ...extra });
      if (calls.length === 1) throw new Error('后端接口不可用');
      return {
        shipmentCode: 'OUT-REPLAY',
        workOrderCode: 'ZD-1',
        ledgerCode: 'STK-REPLAY',
        fromWarehouseCode: 'FINISHED-CUSTOMER',
        toWarehouseCode: 'SHIPPED-CUSTOMER',
        fromStockStatus: 'FINISHED',
        toStockStatus: 'SHIPPED',
        quantity: 2,
        weight: 5,
        logisticsNo: 'YT30000001',
        operator: '何琳',
      };
    });
    await wrapper.get('[aria-label="核对发货"]').trigger('click');
    await wrapper.findAll('button').find((button) => button.text() === '确认发货')!.trigger('click');
    await flushPromises();

    expect(calls).toHaveLength(2);
    expect(calls[0].requestId).toBe(calls[1].requestId);
    expect(calls[0]).toMatchObject({
      shipmentCode: 'OUT-REPLAY',
      logisticsNo: 'YT30000001',
      expressCompany: '顺丰',
      quantity: '2',
      weight: '5',
    });
    expect(calls[1]).toMatchObject(calls[0]);
    expect(wrapper.text()).toContain('发货已写入');
    expect(wrapper.text()).toContain('STK-REPLAY');
  });

  it('扫描已发货的出货单只展示结果和补证据，不再扣库', async () => {
    const wrapper = await openPanel(contextOf({
      objectType: 'OUTBOUND_ORDER',
      code: 'OUT-DONE',
      outboundOrderCode: 'OUT-DONE',
      balances: [],
      selection: false,
      actions: [{ code: 'SHIP', allowed: false, blockKind: 'STATE', blockReason: '这张出货单已经发货，不能再次扣库存' }],
      shipments: [{
        outboundCode: 'OUT-DONE',
        motherOrderCode: 'MD-1',
        workOrderCode: 'ZD-1',
        logisticsNo: 'YT90000001',
        status: 'SHIPPED',
        ledgerCode: 'STK-DONE',
      }],
    }), 'JG|outbound|OUT-DONE');

    expect(wrapper.text()).toContain('不能再次扣库存');
    expect(wrapper.text()).toContain('OUT-DONE');
    expect(wrapper.text()).toContain('YT90000001');
    expect(wrapper.text()).toContain('可以补传证据');
    expect(wrapper.findAll('button').some((button) => button.text() === '确认发货')).toBe(false);
    vi.mocked(uploadEvidenceFile).mockResolvedValue({ code: 'ATT-2', fileName: 'waybill.jpg' } as StoredEvidenceFile);
    vi.mocked(bindEvidence).mockResolvedValue({ linkCode: 'L2', attachmentCode: 'ATT-2', scene: 'PACK' });
    await pickFile(wrapper, 'waybill.jpg');
    await wrapper.findAll('button').find((button) => button.text() === '上传并关联')!.trigger('click');
    await flushPromises();
    expect(bindEvidence).toHaveBeenCalledWith(expect.objectContaining({
      targetCode: 'OUT-DONE',
      operationCode: 'OUT-DONE',
      operationType: 'outbound_shipment',
    }));
    expect(shipFinishedStock).not.toHaveBeenCalled();
  });
});
