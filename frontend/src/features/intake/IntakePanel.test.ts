import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../../services/apiClient';
import IntakePanel from './IntakePanel.vue';
import { queryScanContext } from '../../services/scanContextApi';
import { fetchMotherOrder, lookupMobileIntake, submitMobileIntake } from '../../services/mobileIntakeApi';
import { bindEvidence, uploadEvidenceFile } from '../../services/evidenceApi';
import { buildScanContext } from '../scan/scanContextFixture';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn() };
});

vi.mock('../../services/mobileIntakeApi', () => ({
  lookupMobileIntake: vi.fn(),
  submitMobileIntake: vi.fn(),
  fetchMotherOrder: vi.fn(),
}));

vi.mock('../../services/evidenceApi', () => ({
  fetchEvidenceBoard: vi.fn().mockResolvedValue({ links: [], historical: [] }),
  uploadEvidenceFile: vi.fn(),
  bindEvidence: vi.fn(),
}));

function fileOf(name: string, type: string) {
  const file = new File(['photo'], name, { type });
  Object.defineProperty(file, 'size', { value: 4 });
  return file;
}

async function pickEvidence(wrapper: ReturnType<typeof mount>, name: string, type: string) {
  const input = wrapper.get('[aria-label="选择证据文件"]');
  Object.defineProperty(input.element, 'files', { configurable: true, value: [fileOf(name, type)] });
  await input.trigger('change');
}

function mountPanel(field = false) {
  return mount(IntakePanel, {
    props: {
      title: '手机收货',
      intro: '扫运单后建单',
      field,
    },
  });
}

async function fillNewWaybill(wrapper: ReturnType<typeof mountPanel>) {
  vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
    objectType: 'EXPRESS_NO',
    code: 'SF1048329001',
    motherOrderCode: null,
    actions: [{ code: 'INTAKE', allowed: true, blockKind: 'NONE', blockReason: null }],
  }));
  await wrapper.get('[aria-label="快递单号"]').setValue('SF1048329001');
  await wrapper.get('[aria-label="识别单号"]').trigger('click');
  await flushPromises();
  await wrapper.get('[aria-label="客户"]').setValue('恒信珠宝');
  await wrapper.get('[aria-label="加工要求"]').setValue('抛光');
  await wrapper.get('[aria-label="完成期限"]').setValue('2026-10-20');
  await wrapper.get('[aria-label="货品名称"]').setValue('戒托');
  await wrapper.get('[aria-label="数量"]').setValue('1');
  await wrapper.get('[aria-label="重量"]').setValue('2.5');
}

describe('收货面板', () => {
  beforeEach(() => {
    vi.mocked(submitMobileIntake).mockReset();
    vi.mocked(lookupMobileIntake).mockReset();
    vi.mocked(fetchMotherOrder).mockReset();
    vi.mocked(uploadEvidenceFile).mockReset();
    vi.mocked(bindEvidence).mockReset();
  });

  it('建单前可以选文件，同一次确认复用请求编号，改内容后换新编号', async () => {
    vi.mocked(submitMobileIntake).mockRejectedValue(new Error('网络超时'));
    vi.mocked(lookupMobileIntake).mockResolvedValue({
      found: false,
      motherOrderCode: '',
      courierNo: 'SF1048329001',
      customerName: '',
      processingRequirement: '',
      dueDate: '',
      goodsName: '',
    });
    const wrapper = mountPanel();
    await fillNewWaybill(wrapper);
    await pickEvidence(wrapper, 'unpack.jpg', 'image/jpeg');
    expect(submitMobileIntake).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('尚无正式单号，文件留在当前表单');

    const create = () => wrapper.findAll('button').find((button) => button.text() === '生成母单');
    await create()!.trigger('click');
    await flushPromises();
    await create()!.trigger('click');
    await flushPromises();

    expect(submitMobileIntake).toHaveBeenCalledTimes(2);
    const first = vi.mocked(submitMobileIntake).mock.calls[0][0].requestId;
    const second = vi.mocked(submitMobileIntake).mock.calls[1][0].requestId;
    expect(first).toMatch(/^intake-/);
    expect(second).toBe(first);

    await wrapper.get('[aria-label="数量"]').setValue('2');
    await create()!.trigger('click');
    await flushPromises();
    const third = vi.mocked(submitMobileIntake).mock.calls[2][0].requestId;
    expect(third).not.toBe(first);
  });

  it('超时后按运单打开原单，不再提交建单', async () => {
    vi.mocked(submitMobileIntake).mockRejectedValueOnce(new Error('网络超时'));
    vi.mocked(lookupMobileIntake).mockResolvedValue({
      found: true,
      motherOrderCode: 'MD-20261012-001',
      courierNo: 'SF1048329001',
      customerName: '恒信珠宝',
      processingRequirement: '抛光',
      dueDate: '2026-10-20',
      goodsName: '戒托',
    });
    vi.mocked(fetchMotherOrder).mockResolvedValue({
      alreadyExists: true,
      motherOrderCode: 'MD-20261012-001',
      courierNo: 'SF1048329001',
      customerName: '恒信珠宝',
      processingRequirement: '抛光',
      dueDate: '2026-10-20',
      goodsName: '戒托',
      quantity: 1,
      weight: 2.5,
      ledgerCode: 'STK-1',
      qrContent: 'JG|mother|MD-20261012-001',
    });
    const wrapper = mountPanel();
    await fillNewWaybill(wrapper);
    const create = wrapper.findAll('button').find((button) => button.text() === '生成母单');
    await create!.trigger('click');
    await flushPromises();

    expect(submitMobileIntake).toHaveBeenCalledTimes(1);
    expect(lookupMobileIntake).toHaveBeenCalledWith('SF1048329001');
    expect(wrapper.text()).toContain('已按运单打开原单，没有再次建单');
    expect(wrapper.text()).toContain('MD-20261012-001');
  });

  it('上传失败只重试文件，不再次建单', async () => {
    vi.mocked(submitMobileIntake).mockResolvedValue({
      alreadyExists: false,
      motherOrderCode: 'MD-20261012-001',
      courierNo: 'SF1048329001',
      customerName: '恒信珠宝',
      processingRequirement: '抛光',
      dueDate: '2026-10-20',
      goodsName: '戒托',
      quantity: 1,
      weight: 2.5,
      ledgerCode: 'STK-1',
      qrContent: 'JG|mother|MD-20261012-001',
    });
    vi.mocked(uploadEvidenceFile).mockRejectedValue(new Error('上传失败'));
    const wrapper = mountPanel();
    await fillNewWaybill(wrapper);
    await pickEvidence(wrapper, 'unpack.jpg', 'image/jpeg');
    const create = wrapper.findAll('button').find((button) => button.text() === '生成母单');
    await create!.trigger('click');
    await flushPromises();

    const upload = wrapper.findAll('button').find((button) => button.text() === '上传并关联');
    await upload!.trigger('click');
    await flushPromises();
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试');
    await retry!.trigger('click');
    await flushPromises();

    expect(submitMobileIntake).toHaveBeenCalledTimes(1);
    expect(uploadEvidenceFile).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('业务已保存');
    expect(wrapper.text()).toContain('不会再次建单或入库');
  });

  it('登录过期和网络中断都保留已填内容，并沿用原请求', async () => {
    vi.mocked(submitMobileIntake).mockRejectedValue(new ApiRequestError('未登录', 401, 401));
    vi.mocked(lookupMobileIntake).mockRejectedValue(new Error('网络中断'));
    const wrapper = mountPanel();
    await fillNewWaybill(wrapper);
    const create = () => wrapper.findAll('button').find((button) => button.text() === '生成母单');
    await create()!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('登录已过期。已填内容还在');
    expect((wrapper.get('[aria-label="客户"]').element as HTMLInputElement).value).toBe('恒信珠宝');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('抛光');
    expect((wrapper.get('[aria-label="完成期限"]').element as HTMLInputElement).value).toBe('2026-10-20');

    await create()!.trigger('click');
    await flushPromises();
    const first = vi.mocked(submitMobileIntake).mock.calls[0][0].requestId;
    const second = vi.mocked(submitMobileIntake).mock.calls[1][0].requestId;
    expect(second).toBe(first);
    expect((wrapper.get('[aria-label="客户"]').element as HTMLInputElement).value).toBe('恒信珠宝');
  });

  it('手机收货区分草稿和已保存，并留下标签入口', async () => {
    const openTab = vi.fn();
    const openLabel = vi.fn();
    vi.mocked(submitMobileIntake).mockResolvedValue({
      alreadyExists: false,
      motherOrderCode: 'MD-20261012-001',
      courierNo: 'SF1048329001',
      customerName: '恒信珠宝',
      processingRequirement: '抛光',
      dueDate: '2026-10-20',
      goodsName: '戒托',
      quantity: 1,
      weight: 2.5,
      ledgerCode: 'STK-1',
      qrContent: 'JG|mother|MD-20261012-001',
    });
    const wrapper = mount(IntakePanel, {
      props: { title: '手机收货', intro: '扫运单后建单', field: true },
      global: { provide: { 'zhubao-open-tab': openTab, 'zhubao-open-label': openLabel } },
    });
    await fillNewWaybill(wrapper);
    expect(wrapper.text()).toContain('草稿');
    expect(wrapper.text()).toContain('尚未建单');
    expect(wrapper.text()).not.toContain('已保存');

    await wrapper.findAll('button').find((button) => button.text() === '生成母单')!.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('已保存');
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('抛光');
    expect(wrapper.text()).toContain('2026-10-20');
    expect(wrapper.find('[aria-label="收货草稿"]').exists()).toBe(false);
    await wrapper.get('[aria-label="打开标签"]').trigger('click');
    expect(openLabel).toHaveBeenCalledWith('MD-20261012-001');
    expect(wrapper.emitted('saved')?.[0][0]).toEqual(expect.objectContaining({ motherOrderCode: 'MD-20261012-001' }));
    expect(wrapper.text()).not.toContain('收款');
  });

  it('电脑收货同样显示保存结果和打印入口，继续收货后不残留上一张母单', async () => {
    vi.mocked(submitMobileIntake).mockResolvedValue({
      alreadyExists: false, motherOrderCode: 'MD-DESKTOP-1', courierNo: 'SF1048329001',
      customerName: '恒信珠宝', processingRequirement: '抛光', dueDate: '2026-10-20',
      goodsName: '戒托', quantity: 1, weight: 2.5, ledgerCode: 'STK-1', qrContent: 'JG|mother|MD-DESKTOP-1',
    });
    const wrapper = mountPanel();
    await fillNewWaybill(wrapper);
    await wrapper.findAll('button').find((button) => button.text() === '生成母单')!.trigger('click');
    await flushPromises();
    expect(wrapper.get('[aria-label="已保存母单"]').text()).toContain('MD-DESKTOP-1');
    expect(wrapper.get('[aria-label="打开标签"]').text()).toContain('预览并打印');
    await wrapper.findAll('button').find((button) => button.text() === '继续收下一件')!.trigger('click');
    expect(wrapper.find('[aria-label="已保存母单"]').exists()).toBe(false);
    expect(wrapper.get('[aria-label="快递单号"]').element).toHaveProperty('value', '');
  });

  it('超时核对期间改号，不把原运单的母单带到新输入', async () => {
    vi.mocked(submitMobileIntake).mockRejectedValue(new Error('网络超时'));
    let finishLookup: (value: Awaited<ReturnType<typeof lookupMobileIntake>>) => void = () => {};
    vi.mocked(lookupMobileIntake).mockImplementation(() => new Promise(resolve => { finishLookup = resolve; }));
    const wrapper = mountPanel();
    await fillNewWaybill(wrapper);
    await wrapper.findAll('button').find((button) => button.text() === '生成母单')!.trigger('click');
    await flushPromises();
    await wrapper.get('[aria-label="快递单号"]').setValue('SF-NEW');
    finishLookup({ found: true, motherOrderCode: 'MD-OLD', courierNo: 'SF1048329001', customerName: '恒信珠宝', processingRequirement: '抛光', dueDate: '2026-10-20', goodsName: '戒托' });
    await flushPromises();
    expect(fetchMotherOrder).not.toHaveBeenCalled();
    expect(wrapper.emitted('saved')).toBeUndefined();
    expect(wrapper.find('[aria-label="已保存母单"]').exists()).toBe(false);
    expect(wrapper.get('[aria-label="识别单号"]').attributes('disabled')).toBeUndefined();
  });
});
