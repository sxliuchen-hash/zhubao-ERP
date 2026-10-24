import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import { ApiRequestError } from '../../services/apiClient';
import type { TraceTarget } from '../scan/traceTarget';
import ProductionPanel from './ProductionPanel.vue';
import { queryScanContext, saveProgressRecord } from '../../services/scanContextApi';
import { lookupProcessStation, submitProcessStation } from '../../services/processStationApi';
import { bindEvidence, uploadEvidenceFile, type StoredEvidenceFile } from '../../services/evidenceApi';
import { buildScanContext } from '../scan/scanContextFixture';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return {
    ...actual,
    queryScanContext: vi.fn(),
    saveProgressRecord: vi.fn(),
  };
});

vi.mock('../../services/processStationApi', () => ({
  lookupProcessStation: vi.fn(),
  submitProcessStation: vi.fn(),
}));

vi.mock('../../services/evidenceApi', () => ({
  fetchEvidenceBoard: vi.fn().mockResolvedValue({ links: [], historical: [] }),
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));

function stationView(submitted = false) {
  return {
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
      submitted,
    }],
    message: '',
  };
}

function fileOf(name: string) {
  const file = new File(['photo'], name, { type: 'image/jpeg' });
  Object.defineProperty(file, 'size', { value: 4 });
  return file;
}

async function openStation(field = false) {
  vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
    objectType: 'WORK_ORDER',
    code: 'ZD-100',
    customerName: '恒信珠宝',
    goodsName: '来料戒托',
    quantity: 18,
    weight: 12.5,
  }));
  vi.mocked(lookupProcessStation).mockResolvedValue(stationView());
  const wrapper = mount(ProductionPanel, {
    props: { title: '岗位报工', intro: '扫贴纸后交付', field },
  });
  await wrapper.get('[aria-label="贴纸二维码"]').setValue('JG|work_order|ZD-100');
  await wrapper.get('[aria-label="识别贴纸"]').trigger('click');
  await flushPromises();
  return wrapper;
}

describe('生产面板', () => {
  async function openBatch(delivered = 2, available = 3, finalProcess = false) {
    const view = { ...stationView(), stations: [{ ...stationView().stations[0]!, previousProcessName: '前道', previousSubmitted: false, totalQuantity: 10, deliveredQuantity: delivered, availableQuantity: available, finalProcess, batches: [] }] };
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-100', quantity: 10, weight: 12.5 }));
    vi.mocked(lookupProcessStation).mockResolvedValue(view);
    const wrapper = mount(ProductionPanel, { props: { title: '分批交工', intro: '', field: true, embedded: true, initialTarget: { objectType: 'WORK_ORDER', code: 'ZD-100' } } });
    await flushPromises(); return wrapper;
  }
  it('前道部分交工即可登记，超可交数量被拦截，下一批使用新编号并保留证据草稿', async () => {
    const wrapper = await openBatch();
    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    await wrapper.get('[aria-label="本次交工数量"]').setValue('4');
    expect(wrapper.get('[aria-label="确认雕刻交工"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[aria-label="本次交工数量"]').setValue('3');
    const firstEvidence = wrapper.getComponent({ name: 'EvidenceCapture' }).vm.$;
    vi.mocked(submitProcessStation).mockResolvedValue({ orderCode: 'ZD-100', processName: '雕刻', ledgerCode: '', batchCode: 'PB-1', batchQuantity: 3, deliveredQuantity: 5, remainingQuantity: 5, processCompleted: false, movedToSemiFinished: false, stockStatus: 'WIP' });
    await wrapper.get('[aria-label="确认雕刻交工"]').trigger('click'); await flushPromises();
    expect(submitProcessStation).toHaveBeenLastCalledWith('ZD-100', '雕刻', expect.any(String), { quantity: '3' });
    expect(wrapper.text()).toContain('本批 3，累计 5，剩余 5');
    expect(wrapper.text()).not.toContain('本单工序已完成');
    await wrapper.findAll('button').find(button => button.text() === '登记下一批')!.trigger('click');
    expect(wrapper.findAllComponents({ name: 'EvidenceCapture' })).toHaveLength(2);
    expect(wrapper.findAllComponents({ name: 'EvidenceCapture' })[0]!.vm.$).toBe(firstEvidence);
    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    await wrapper.get('[aria-label="本次交工数量"]').setValue('3');
    await wrapper.get('[aria-label="确认雕刻交工"]').trigger('click'); await flushPromises();
    expect(vi.mocked(submitProcessStation).mock.calls.at(-1)![2]).not.toBe(vi.mocked(submitProcessStation).mock.calls.at(-2)![2]);
    wrapper.unmount();
  });
  it('末道数量交齐后可以只补记重量，提交期间保留原数量重量请求', async () => {
    const wrapper = await openBatch(10, 0, true);
    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    await wrapper.get('[aria-label="本次交工数量"]').setValue('0');
    expect(wrapper.get('[aria-label="确认雕刻交工"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[aria-label="末道总重量"]').setValue('12.3');
    vi.mocked(submitProcessStation).mockRejectedValueOnce(new Error('响应丢失'));
    await wrapper.get('[aria-label="确认雕刻交工"]').trigger('click'); await flushPromises();
    const original = vi.mocked(submitProcessStation).mock.calls.at(-1);
    expect(original?.[3]).toEqual({ quantity: '0', finalWeight: '12.3' });
    expect(wrapper.get('[aria-label="末道总重量"]').attributes('disabled')).toBeDefined();
    vi.mocked(submitProcessStation).mockResolvedValueOnce({ orderCode: 'ZD-100', processName: '雕刻', ledgerCode: 'STK-LAST', batchCode: 'PB-W', batchQuantity: 0, deliveredQuantity: 10, remainingQuantity: 0, processCompleted: true, movedToSemiFinished: true, stockStatus: 'SEMI_FINISHED' });
    await wrapper.findAll('button').find(button => button.text() === '原交工请求重试')!.trigger('click'); await flushPromises();
    expect(vi.mocked(submitProcessStation).mock.calls.at(-1)).toEqual(original);
    expect(wrapper.text()).toContain('下一步由质检处理'); wrapper.unmount();
  });

  it('数量缺失或上道未交时提前阻断，逐条勾选不产生交工', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-100', quantity: null, weight: null }));
    vi.mocked(lookupProcessStation).mockResolvedValue(stationView());
    const wrapper = mount(ProductionPanel, { props: { title: '交工', intro: '', field: true, embedded: true, initialTarget: { objectType: 'WORK_ORDER', code: 'ZD-100' } } });
    await flushPromises();
    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    expect(wrapper.get('[aria-label="确认雕刻交工"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[aria-label="确认雕刻交工"]').trigger('click');
    expect(submitProcessStation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('数量或重量尚未读取完整');
    wrapper.unmount();

    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-100', quantity: 18, weight: 12.5 }));
    vi.mocked(lookupProcessStation).mockResolvedValue({ ...stationView(), stations: [{ ...stationView().stations[0], processName: '镶嵌', previousProcessName: '雕刻', previousSubmitted: false }] });
    const blocked = mount(ProductionPanel, { props: { title: '交工', intro: '', field: true, embedded: true, initialTarget: { objectType: 'WORK_ORDER', code: 'ZD-100' } } });
    await flushPromises();
    expect(blocked.text()).toContain('上一道「雕刻」还未交工');
    expect(blocked.get('[aria-label="确认镶嵌交工"]').attributes('disabled')).toBeDefined();
    expect(submitProcessStation).not.toHaveBeenCalled();
    blocked.unmount();
  });
  beforeEach(() => {
    vi.mocked(queryScanContext).mockReset();
    vi.mocked(submitProcessStation).mockReset();
    vi.mocked(lookupProcessStation).mockReset();
    vi.mocked(uploadEvidenceFile).mockReset();
    vi.mocked(bindEvidence).mockReset();
    vi.mocked(saveProgressRecord).mockReset();
  });

  it('固定轮次不能被解析成原单，不加载原单岗位或开放提交', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-100' }));
    const wrapper = mount(ProductionPanel, { props: { title: '正式交工', intro: '', embedded: true, initialTarget: { objectType: 'WORK_ORDER', code: 'FG-100-R2' } } });
    await flushPromises();
    expect(queryScanContext).toHaveBeenCalledWith({ content: 'FG-100-R2', objectType: 'WORK_ORDER', intent: 'PRODUCTION' });
    expect(lookupProcessStation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('报工目标与当前单据或执行轮次不一致');
    expect(wrapper.find('[aria-label="过程说明"]').exists()).toBe(false);
    expect(submitProcessStation).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('现场定位到当前可办理工序，切换查看不会自动核对或交工', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-100' }));
    const view = stationView();
    vi.mocked(lookupProcessStation).mockResolvedValue({ ...view, stations: [...view.stations, { ...view.stations[0], processName: '镶嵌', requirement: '爪位对称' }] });
    const wrapper = mount(ProductionPanel, { props: { title: '正式交工', intro: '', field: true, embedded: true, initialTarget: { objectType: 'WORK_ORDER', code: 'ZD-100' } } });
    await flushPromises();
    expect(wrapper.get('[aria-label="选择工序 雕刻"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.find('[aria-label="完成核对"]').exists()).toBe(false);
    await wrapper.get('[aria-label="选择工序 镶嵌"]').trigger('click');
    expect(wrapper.get('[aria-label="本岗位加工要求"]').text()).toContain('爪位对称');
    expect(submitProcessStation).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('进度目标按规范单号打开，别的意图不报工', async () => {
    const target = ref<TraceTarget | null>(null);
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'WORK_ORDER',
      code: 'ZD-9',
      customerName: '瑞丽金行',
    }));
    vi.mocked(lookupProcessStation).mockResolvedValue({ ...stationView(), orderCode: 'ZD-9' });
    mount(ProductionPanel, {
      props: { title: '岗位报工', intro: '扫贴纸后交付' },
      global: { provide: { 'zhubao-trace-target': target } },
    });
    target.value = { objectType: 'WORK_ORDER', code: 'ZD-8', intent: 'SHIPPING', token: 1 };
    await flushPromises();
    expect(queryScanContext).not.toHaveBeenCalled();

    target.value = { objectType: 'WORK_ORDER', code: 'ZD-9', intent: 'PRODUCTION', token: 2 };
    await flushPromises();
    expect(queryScanContext).toHaveBeenCalledWith({
      content: 'ZD-9',
      objectType: 'WORK_ORDER',
      intent: 'PRODUCTION',
    });
    expect(lookupProcessStation).toHaveBeenCalledWith('ZD-9');
    expect(lookupProcessStation).not.toHaveBeenCalledWith('ZD-8');
  });

  it('同一次确认复用请求编号，换工序后换新编号', async () => {
    const wrapper = await openStation();
    vi.mocked(lookupProcessStation).mockResolvedValue(stationView(false));
    const ids: string[] = [];
    vi.mocked(submitProcessStation).mockImplementation(async (_code, process, requestId) => {
      ids.push(`${process}:${requestId}`);
      if (process === '雕刻') throw new ApiRequestError('上一道还没交', 400, 40001);
      return {
        orderCode: 'ZD-100',
        processName: process,
        ledgerCode: 'STK-2',
        movedToSemiFinished: false,
        stockStatus: 'WIP',
      };
    });
    await wrapper.get('button').element;
    await wrapper.findAll('button').find((button) => button.text() === '提交雕刻')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('上一道还没交');

    vi.mocked(lookupProcessStation).mockResolvedValue({
      ...stationView(),
      stations: [
        stationView().stations[0],
        {
          processName: '镶嵌',
          requirement: '镶石',
          dueDate: '2026-10-20',
          previousProcessName: '雕刻',
          previousSubmitted: true,
          submitted: false,
        },
      ],
    });
    await wrapper.get('[aria-label="识别贴纸"]').trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '提交镶嵌')!.trigger('click');
    await flushPromises();

    const carvingId = ids[0].split(':')[1];
    const inlayId = ids[1].split(':')[1];
    expect(carvingId).toMatch(/^station-/);
    expect(inlayId).not.toBe(carvingId);

    await wrapper.findAll('button').find((button) => button.text() === '提交雕刻')!.trigger('click');
    await flushPromises();
    expect(ids[2].split(':')[1]).toBe(carvingId);
    expect(submitProcessStation).toHaveBeenCalledTimes(3);
  });

  it('超时用原请求再查一次，不换新编号', async () => {
    const wrapper = await openStation();
    const ids: string[] = [];
    vi.mocked(submitProcessStation).mockImplementation(async (_code, _process, requestId) => {
      ids.push(requestId ?? '');
      if (ids.length === 1) throw new Error('后端接口不可用');
      return {
        orderCode: 'ZD-100',
        processName: '雕刻',
        ledgerCode: 'STK-REPLAY',
        movedToSemiFinished: false,
        stockStatus: 'WIP',
      };
    });
    vi.mocked(lookupProcessStation).mockResolvedValue(stationView(true));
    await wrapper.findAll('button').find((button) => button.text() === '提交雕刻')!.trigger('click');
    await flushPromises();

    expect(ids).toHaveLength(1);
    expect(wrapper.text()).toContain('交工结果待核对');
    await wrapper.findAll('button').find(button => button.text() === '原交工请求重试')!.trigger('click');
    await flushPromises();
    expect(ids).toHaveLength(2);
    expect(ids[0]).toBe(ids[1]);
    expect(wrapper.text()).toContain('雕刻已交');
    expect(wrapper.text()).toContain('业务已保存');
  });

  it('过程说明不交付，证据失败只重试文件', async () => {
    const wrapper = await openStation();
    vi.mocked(saveProgressRecord).mockResolvedValue({
      code: 'PG-1',
      targetType: 'WORK_ORDER',
      targetCode: 'ZD-100',
      motherOrderCode: 'MD-1',
      stage: 'PRODUCTION',
      process: '雕刻',
      clauseNo: '',
      note: '刻到一半',
      operatorId: 'u1',
      operatorName: '林海',
      operatorRole: 'OPERATOR',
      supplementary: false,
      recordedAt: '2026-10-12T10:00:00',
      requestId: 'req-note',
    });
    await wrapper.get('[aria-label="过程说明"]').setValue('刻到一半');
    await wrapper.findAll('button').find((button) => button.text() === '保存过程说明')!.trigger('click');
    await flushPromises();
    expect(saveProgressRecord).toHaveBeenCalledTimes(1);
    expect(submitProcessStation).not.toHaveBeenCalled();

    vi.mocked(submitProcessStation).mockResolvedValue({
      orderCode: 'ZD-100',
      processName: '雕刻',
      ledgerCode: 'STK-1',
      movedToSemiFinished: false,
      stockStatus: 'WIP',
    });
    vi.mocked(lookupProcessStation).mockResolvedValue(stationView(true));
    await wrapper.findAll('button').find((button) => button.text() === '提交雕刻')!.trigger('click');
    await flushPromises();
    expect(submitProcessStation).toHaveBeenCalledTimes(1);

    vi.mocked(uploadEvidenceFile).mockRejectedValue(new Error('上传失败'));
    const input = wrapper.get('[aria-label="选择证据文件"]');
    Object.defineProperty(input.element, 'files', { configurable: true, value: [fileOf('finish.jpg')] });
    await input.trigger('change');
    await wrapper.findAll('button').find((button) => button.text() === '上传并关联')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('上传失败');

    vi.mocked(uploadEvidenceFile).mockResolvedValue({ code: 'ATT-1', fileName: 'finish.jpg' } as StoredEvidenceFile);
    vi.mocked(bindEvidence).mockResolvedValue({ linkCode: 'L1', attachmentCode: 'ATT-1', scene: 'PROCESS' });
    await wrapper.findAll('button').find((button) => button.text() === '重试')!.trigger('click');
    await flushPromises();
    expect(uploadEvidenceFile).toHaveBeenCalledTimes(2);
    expect(submitProcessStation).toHaveBeenCalledTimes(1);
    expect(bindEvidence).toHaveBeenCalledWith(expect.objectContaining({
      operationCode: 'STK-1',
      operationType: 'process_station_report',
      processName: '雕刻',
      scene: 'PROCESS',
    }));
  });

  it('手机核对要求与整单数量后一次确认交工，登录过期重试沿用原编号', async () => {
    const wrapper = await openStation(true);
    expect(wrapper.text()).toContain('当前子单');
    expect(wrapper.text()).toContain('来料戒托');
    expect(wrapper.text()).toContain('点击下方“确认交工”才会提交');
    expect(wrapper.findAll('button').some((button) => button.text() === '提交雕刻')).toBe(false);

    await wrapper.get('[aria-label="核对 1 内壁刻字"]').setValue(true);
    expect(submitProcessStation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('18 件');
    expect(wrapper.text()).toContain('12.5 克');

    const ids: string[] = [];
    vi.mocked(submitProcessStation).mockImplementation(async (_code, _process, requestId) => {
      ids.push(requestId ?? '');
      throw new ApiRequestError('未登录', 401, 401);
    });
    const submit = () => wrapper.findAll('button').find((button) => button.text() === '确认雕刻交工');
    await submit()!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('登录已过期。已填内容还在');
    expect(wrapper.text()).toContain('ZD-100');
    await submit()!.trigger('click');
    await flushPromises();
    expect(ids).toHaveLength(2);
    expect(ids[0]).toBe(ids[1]);
    expect(ids[0]).toMatch(/^station-/);
  });
});
