import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, ref } from 'vue';
import { ApiRequestError } from '../../services/apiClient';
import IntakePanel from './IntakePanel.vue';
import { queryScanContext } from '../../services/scanContextApi';
import { fetchMotherOrder, lookupMobileIntake, submitMobileIntake } from '../../services/mobileIntakeApi';
import { bindEvidence, fetchEvidenceBoard, uploadEvidenceFile } from '../../services/evidenceApi';
import { buildScanContext } from '../scan/scanContextFixture';
import { fetchCustomers, fetchMaterials, fetchShortcuts, fetchTemplates, removeShortcut, saveCustomer, saveMaterial, saveShortcut } from '../../services/masterDataApi';
import { composeRequirement } from './requirementText';
import RequirementComposer from './RequirementComposer.vue';
import EvidenceCapture from '../evidence/EvidenceCapture.vue';

vi.mock('../m1/PrepStockPanel.vue', () => ({ default: defineComponent({ name: 'PrepStockPanel', props: ['scope', 'initialSource'], setup() { return { draft: ref('') }; }, template: '<input v-model="draft" aria-label="寄存原草稿" />' }) }));

vi.mock('../../services/masterDataApi', () => ({
  fetchCustomers: vi.fn(), fetchMaterials: vi.fn(), fetchShortcuts: vi.fn(), fetchTemplates: vi.fn(),
  saveCustomer: vi.fn(), saveMaterial: vi.fn(), saveShortcut: vi.fn(), removeShortcut: vi.fn(),
}));

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
  await flushPromises();
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

async function chooseFilterOption(wrapper: ReturnType<typeof mount>, label: string, text?: string) {
  await wrapper.get(`[aria-label="${label}"]`).trigger('focus');
  const options = wrapper.get(`[aria-label="${label}选项"]`).findAll('[role="option"]');
  const option = text ? options.find((item) => item.text().includes(text)) : options[0];
  expect(option, `${label} ${text ?? ''}`).toBeTruthy();
  await option!.trigger('click');
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
  await chooseFilterOption(wrapper, '客户', '恒信珠宝');
  await chooseFilterOption(wrapper, '材质', '足金');
  await wrapper.get('[aria-label="加工要求"]').setValue('抛光');
  await wrapper.get('[aria-label="完成期限"]').setValue('2026-10-20');
  await wrapper.get('[aria-label="货品名称"]').setValue('戒托');
  await wrapper.get('[aria-label="数量"]').setValue('1');
  await wrapper.get('[aria-label="重量"]').setValue('2.5');
}

describe('收货面板', () => {
  beforeEach(() => {
    vi.mocked(fetchCustomers).mockResolvedValue([{ customerId: 1001, customerCode: 'C-1001', name: '恒信珠宝',
      aliases: '', contact: '', phone: '', remark: '', enabled: true }]);
    vi.mocked(fetchMaterials).mockResolvedValue([{ materialCode: 'AU9999', name: '足金', aliases: '', remark: '', enabled: true }]);
    vi.mocked(fetchShortcuts).mockResolvedValue([]);
    vi.mocked(fetchTemplates).mockResolvedValue([]);
    vi.mocked(submitMobileIntake).mockReset();
    vi.mocked(lookupMobileIntake).mockReset();
    vi.mocked(fetchMotherOrder).mockReset();
    vi.mocked(uploadEvidenceFile).mockReset();
    vi.mocked(bindEvidence).mockReset();
    vi.mocked(fetchEvidenceBoard).mockReset();
    vi.mocked(fetchEvidenceBoard).mockResolvedValue({ links: [], historical: [] });
  });

  it('同一运单按可见母单分组查看证据且不自动选第一张', async () => {
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      requiresSelection: true,
      relatedTargets: [
        { objectType: 'MOTHER_ORDER', code: 'MD-1', motherOrderCode: 'MD-1', requirement: null, quantity: 3, quantityUnit: '件' },
        { objectType: 'MOTHER_ORDER', code: 'MD-2', motherOrderCode: 'MD-2', requirement: null, quantity: 5, quantityUnit: '件' },
        { objectType: 'EXPRESS_NO', code: 'SF1048329001', motherOrderCode: null, requirement: null, quantity: null, quantityUnit: null },
      ],
    }));
    vi.mocked(fetchEvidenceBoard).mockImplementation(async ({ targetCode }) => ({ links: [], historical: [{
      attachmentCode: `ATT-${targetCode}`, businessCode: targetCode, objectType: 'MOTHER_ORDER',
      fileName: `${targetCode}-拆包照片.jpg`, mediaType: 'image/jpeg', checksum: '', status: 'ACTIVE',
      uploadedAt: '', label: '历史证据', sceneCode: 'UNPACK', previewUrl: '', fileState: 'HISTORICAL',
    }] }));
    const wrapper = mountPanel();
    await wrapper.get('[aria-label="快递单号"]').setValue('SF1048329001');
    await wrapper.get('[aria-label="识别单号"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('关联 2 张可见母单');
    expect(wrapper.find('[aria-label="已保存母单"]').exists()).toBe(false);
    await wrapper.findAll('button').find(button => button.text() === '查看关联证据')!.trigger('click');
    await flushPromises();
    expect(fetchEvidenceBoard).toHaveBeenCalledTimes(2);
    expect(fetchEvidenceBoard).toHaveBeenCalledWith({ targetType: 'MOTHER_ORDER', targetCode: 'MD-1' });
    expect(fetchEvidenceBoard).toHaveBeenCalledWith({ targetType: 'MOTHER_ORDER', targetCode: 'MD-2' });
    expect(wrapper.text()).toContain('MD-1-拆包照片.jpg');
    expect(wrapper.text()).toContain('MD-2-拆包照片.jpg');
    await wrapper.get('[aria-label="快递单号"]').setValue('SF-OTHER');
    expect(wrapper.find('[aria-label="运单关联证据"]').exists()).toBe(false);
    wrapper.unmount();
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
    expect(vi.mocked(submitMobileIntake).mock.calls[0][0].expressCompany).toBe('');

    await wrapper.get('[aria-label="数量"]').setValue('2');
    await create()!.trigger('click');
    await flushPromises();
    const third = vi.mocked(submitMobileIntake).mock.calls[2][0].requestId;
    expect(third).not.toBe(first);
  });

  it('超时后保留原请求号，不按运单猜测母单', async () => {
    vi.mocked(submitMobileIntake).mockRejectedValue(new Error('网络超时'));
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
    expect(lookupMobileIntake).not.toHaveBeenCalled();
    expect(fetchMotherOrder).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('网络超时');
    await create!.trigger('click');
    await flushPromises();
    expect(vi.mocked(submitMobileIntake).mock.calls[1][0].requestId)
      .toBe(vi.mocked(submitMobileIntake).mock.calls[0][0].requestId);
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
    expect((wrapper.get('[aria-label="客户"]').element as HTMLInputElement).value).toContain('恒信珠宝');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('抛光');
    expect((wrapper.get('[aria-label="完成期限"]').element as HTMLInputElement).value).toBe('2026-10-20');

    await create()!.trigger('click');
    await flushPromises();
    const first = vi.mocked(submitMobileIntake).mock.calls[0][0].requestId;
    const second = vi.mocked(submitMobileIntake).mock.calls[1][0].requestId;
    expect(second).toBe(first);
    expect((wrapper.get('[aria-label="客户"]').element as HTMLInputElement).value).toContain('恒信珠宝');
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

    await wrapper.get('.field-dock button').trigger('click');
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

  it('收货员新建母单时可用搜索词新增客户和材质并选中', async () => {
    vi.mocked(saveCustomer).mockResolvedValue({ customerId: 2088, customerCode: 'C-2088', name: '翠玉坊',
      aliases: '', contact: '', phone: '', remark: '', enabled: true });
    vi.mocked(saveMaterial).mockResolvedValue({ materialCode: 'MAT-JADE', name: '翡翠', aliases: '', remark: '', enabled: true });
    const wrapper = mount(IntakePanel, {
      props: { title: '新建来料', intro: '建单' },
      global: { provide: { 'zhubao-auth-session': ref({ role: 'RECEIVER' }) } },
    });
    await flushPromises();
    await wrapper.get('[aria-label="收货来源"]').setValue('CUSTOMER_DELIVERY');
    expect(wrapper.text()).toContain('新增客户档案');
    expect(wrapper.text()).toContain('新增材质');

    await wrapper.findAll('button').find((button) => button.text() === '新增客户档案')!.trigger('click');
    expect(wrapper.text()).toContain('请填写要新增的客户名称');

    await wrapper.get('[aria-label="客户"]').setValue('翠玉坊');
    await wrapper.findAll('button').find((button) => button.text() === '新增客户档案')!.trigger('click');
    await flushPromises();
    expect(saveCustomer).toHaveBeenCalledWith(expect.objectContaining({ name: '翠玉坊', customerId: 0 }));
    expect((wrapper.get('[aria-label="客户"]').element as HTMLInputElement).value).toContain('翠玉坊');
    expect(wrapper.text()).toContain('已新增客户档案「翠玉坊」');

    await wrapper.get('[aria-label="材质"]').setValue('翡翠');
    await wrapper.findAll('button').find((button) => button.text() === '新增材质')!.trigger('click');
    await flushPromises();
    expect(saveMaterial).toHaveBeenCalledWith(expect.objectContaining({ name: '翡翠' }));
    expect((wrapper.get('[aria-label="材质"]').element as HTMLInputElement).value).toContain('翡翠');
    wrapper.unmount();
  });

  it('没有档案维护权限时不显示新增客户和材质', async () => {
    const wrapper = mount(IntakePanel, {
      props: { title: '新建来料', intro: '建单' },
      global: { provide: { 'zhubao-auth-session': ref({ role: 'SALES' }) } },
    });
    await flushPromises();
    await wrapper.get('[aria-label="收货来源"]').setValue('CUSTOMER_DELIVERY');
    expect(wrapper.text()).not.toContain('新增客户档案');
    expect(wrapper.find('[aria-label="新增材质名称"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="新快捷词"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('业务管理只有客户档案顶岗时才能新增客户和快捷词', async () => {
    const blocked = mount(IntakePanel, {
      props: { title: '新建来料', intro: '建单' },
      global: { provide: { 'zhubao-auth-session': ref({ role: 'BUSINESS_MANAGER', coverDuties: ['INTAKE'] }) } },
    });
    await flushPromises();
    await blocked.get('[aria-label="收货来源"]').setValue('CUSTOMER_DELIVERY');
    expect(blocked.text()).not.toContain('新增客户档案');
    expect(blocked.find('[aria-label="新快捷词"]').exists()).toBe(false);
    blocked.unmount();

    const allowed = mount(IntakePanel, {
      props: { title: '新建来料', intro: '建单' },
      global: { provide: { 'zhubao-auth-session': ref({ role: 'BUSINESS_MANAGER', coverDuties: ['MASTER'] }) } },
    });
    await flushPromises();
    await allowed.get('[aria-label="收货来源"]').setValue('CUSTOMER_DELIVERY');
    expect(allowed.text()).toContain('新增客户档案');
    expect(allowed.find('[aria-label="新快捷词"]').exists()).toBe(true);
    allowed.unmount();
  });

  it('客户和材质在同一个框里按关键字筛选', async () => {
    vi.mocked(fetchCustomers).mockResolvedValue([
      { customerId: 1001, customerCode: 'C-1001', name: '恒信珠宝', aliases: '恒信', contact: '', phone: '', remark: '', enabled: true },
      { customerId: 1002, customerCode: 'C-1002', name: '翠玉坊', aliases: '', contact: '', phone: '', remark: '', enabled: true },
    ]);
    vi.mocked(fetchMaterials).mockResolvedValue([
      { materialCode: 'AU9999', name: '足金', aliases: '黄金', remark: '', enabled: true },
      { materialCode: 'PT950', name: '铂金', aliases: '', remark: '', enabled: true },
    ]);
    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.get('[aria-label="收货来源"]').setValue('CUSTOMER_DELIVERY');
    expect(wrapper.find('[aria-label="搜索客户"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="搜索材质"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="快递公司"]').exists()).toBe(false);

    const customer = wrapper.get('[aria-label="客户"]');
    await customer.trigger('focus');
    expect(wrapper.get('[aria-label="客户选项"]').text()).toContain('恒信珠宝');
    expect(wrapper.get('[aria-label="客户选项"]').text()).toContain('翠玉坊');
    await customer.setValue('翠玉');
    expect(wrapper.get('[aria-label="客户选项"]').text()).toContain('翠玉坊');
    expect(wrapper.get('[aria-label="客户选项"]').text()).not.toContain('恒信珠宝');
    await wrapper.get('[aria-label="客户选项"]').get('[role="option"]').trigger('click');
    expect((customer.element as HTMLInputElement).value).toContain('翠玉坊');

    const material = wrapper.get('[aria-label="材质"]');
    await material.trigger('focus');
    await material.setValue('黄金');
    expect(wrapper.get('[aria-label="材质选项"]').text()).toContain('足金');
    expect(wrapper.get('[aria-label="材质选项"]').text()).not.toContain('铂金');
    await wrapper.get('[aria-label="材质选项"]').get('[role="option"]').trigger('click');
    expect((material.element as HTMLInputElement).value).toContain('AU9999');

    await wrapper.findAll('button').find((button) => button.text() === '增加材质明细')!.trigger('click');
    const line = wrapper.get('[aria-label="明细2材质"]');
    await line.trigger('focus');
    await line.setValue('铂');
    expect(wrapper.get('[aria-label="明细2材质选项"]').text()).toContain('铂金');
    expect(wrapper.get('[aria-label="明细2材质选项"]').text()).not.toContain('足金');
    wrapper.unmount();
  });

  it('加工要求可以点选并维护快捷词', async () => {
    vi.mocked(fetchShortcuts).mockResolvedValue([{ shortcutId: 3, phrase: '镜面抛光', sortOrder: 1 }]);
    vi.mocked(saveShortcut).mockResolvedValue({ shortcutId: 4, phrase: '改圈', sortOrder: 2 });
    vi.mocked(removeShortcut).mockResolvedValue({ shortcutId: 3, phrase: '镜面抛光', sortOrder: 1 });
    const wrapper = mount(IntakePanel, {
      props: { title: '新建来料', intro: '建单' },
      global: { provide: { 'zhubao-auth-session': ref({ role: 'RECEIVER' }) } },
    });
    await flushPromises();
    await wrapper.get('[aria-label="收货来源"]').setValue('CUSTOMER_DELIVERY');
    await wrapper.get('[aria-label="填入快捷词 镜面抛光"]').trigger('click');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('镜面抛光');
    await wrapper.get('[aria-label="填入快捷词 镜面抛光"]').trigger('click');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('');
    await wrapper.get('[aria-label="加工要求"]').setValue('倒模');
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    await wrapper.get('[aria-label="填入快捷词 镜面抛光"]').trigger('click');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('倒模');

    await wrapper.get('[aria-label="新快捷词"]').setValue('改圈');
    await wrapper.findAll('button').find((button) => button.text() === '添加快捷词')!.trigger('click');
    await flushPromises();
    expect(saveShortcut).toHaveBeenCalledWith('改圈');
    expect(wrapper.text()).toContain('已添加快捷词「改圈」');

    expect(wrapper.get('[aria-label="删除快捷词 镜面抛光"]').text()).toBe('×');
    expect(wrapper.text()).not.toContain('移除');
    await wrapper.get('[aria-label="删除快捷词 镜面抛光"]').trigger('click');
    await flushPromises();
    expect(removeShortcut).toHaveBeenCalledWith(3);
    expect(wrapper.find('[aria-label="填入快捷词 镜面抛光"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('模板预览与提交文字一致，下一单不残留来源', async () => {
    const basic = {
      templateId: 1, templateCode: 'TPL-BEAD-BASIC', name: '常规珠型加工',
      steps: ['开粗', '车形（车成珠型）', '抛光'], enabled: true, sortOrder: 1, version: 1,
    };
    vi.mocked(fetchTemplates).mockResolvedValue([basic]);
    vi.mocked(submitMobileIntake).mockResolvedValue({
      alreadyExists: false, motherOrderCode: 'MD-TPL-1', courierNo: 'SF1048329001',
      customerName: '恒信珠宝', processingRequirement: composeRequirement(basic.steps, '孔径 2 毫米'),
      dueDate: '2026-10-20', goodsName: '戒托', quantity: 1, weight: 2.5,
      ledgerCode: 'STK-1', qrContent: 'JG|mother|MD-TPL-1',
    });
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'EXPRESS_NO', code: 'SF1048329001', motherOrderCode: null,
      actions: [{ code: 'INTAKE', allowed: true, blockKind: 'NONE', blockReason: null }],
    }));
    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.get('[aria-label="快递单号"]').setValue('SF1048329001');
    await wrapper.get('[aria-label="识别单号"]').trigger('click');
    await flushPromises();
    await chooseFilterOption(wrapper, '客户', '恒信珠宝');
    await chooseFilterOption(wrapper, '材质', '足金');
    await wrapper.get('[aria-label="本单补充说明"]').setValue('孔径 2 毫米');
    await wrapper.get('[aria-label="加工要求模板"]').setValue('TPL-BEAD-BASIC');
    const preview = (wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value;
    expect(preview).toBe(composeRequirement(basic.steps, '孔径 2 毫米'));
    await wrapper.get('[aria-label="完成期限"]').setValue('2026-10-20');
    await wrapper.get('[aria-label="货品名称"]').setValue('戒托');
    await wrapper.get('[aria-label="数量"]').setValue('1');
    await wrapper.get('[aria-label="重量"]').setValue('2.5');
    await wrapper.findAll('button').find((button) => button.text() === '生成母单')!.trigger('click');
    await flushPromises();
    expect(submitMobileIntake).toHaveBeenCalledWith(expect.objectContaining({
      processingRequirement: preview,
      requirementTemplateCode: 'TPL-BEAD-BASIC',
      requirementTemplateVersion: 1,
    }));

    await wrapper.findAll('button').find((button) => button.text() === '继续收下一件')!.trigger('click');
    vi.mocked(queryScanContext).mockResolvedValue(buildScanContext({
      objectType: 'EXPRESS_NO', code: 'SF2000000001', motherOrderCode: null,
      actions: [{ code: 'INTAKE', allowed: true, blockKind: 'NONE', blockReason: null }],
    }));
    await wrapper.get('[aria-label="快递单号"]').setValue('SF2000000001');
    await wrapper.get('[aria-label="识别单号"]').trigger('click');
    await flushPromises();
    expect((wrapper.get('[aria-label="加工要求模板"]').element as HTMLSelectElement).value).toBe('');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('');
    expect(wrapper.find('[aria-label="已选加工步骤"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('加工要求超过 500 字时不提交', async () => {
    const wrapper = mountPanel();
    await fillNewWaybill(wrapper);
    await wrapper.get('[aria-label="加工要求"]').setValue('长'.repeat(501));
    await wrapper.findAll('button').find((button) => button.text() === '生成母单')!.trigger('click');
    await flushPromises();
    expect(submitMobileIntake).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('加工要求超过 500 字');
    wrapper.unmount();
  });

  it('两段往返保留要求与已选文件，下一步不提交任何业务', async () => {
    const wrapper = mountPanel(true);
    await fillNewWaybill(wrapper);
    expect(wrapper.get('.intake-goods').classes()).not.toContain('intake-stage-hidden');
    expect(wrapper.findAll('button').filter(button => button.text() === '下一步：要求与证据')).toHaveLength(1);
    expect(wrapper.get('.intake-requirement').classes()).toContain('intake-stage-hidden');
    await wrapper.get('.field-dock button').trigger('click');
    expect(wrapper.get('.intake-stage-nav button:last-child').attributes('aria-current')).toBe('step');
    await pickEvidence(wrapper, '留在本草稿.jpg', 'image/jpeg');
    const evidence = wrapper.get('[aria-label="选择证据文件"]').element;
    await wrapper.get('.intake-stage-back').trigger('click');
    expect(wrapper.get('.intake-goods').classes()).not.toContain('intake-stage-hidden');
    expect(wrapper.get('[aria-label="选择证据文件"]').element).toBe(evidence);
    await wrapper.get('.field-dock button').trigger('click');
    expect(wrapper.text()).toContain('留在本草稿.jpg');
    expect(wrapper.get('[aria-label="加工要求"]').element).toHaveProperty('value', '抛光');
    expect(submitMobileIntake).not.toHaveBeenCalled();
    expect(uploadEvidenceFile).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('加工与寄存用途往返保留原要求、文件及各自实例，切换不建母单', async () => {
    const wrapper = mountPanel(true);
    await fillNewWaybill(wrapper);
    await wrapper.get('.field-dock button').trigger('click');
    await pickEvidence(wrapper, '加工原照片.jpg', 'image/jpeg');
    const composer = wrapper.getComponent(RequirementComposer).vm.$;
    const evidence = wrapper.getComponent(EvidenceCapture).vm.$;
    await wrapper.get('[aria-label="收货用途"]').setValue('prep');
    await wrapper.get('[aria-label="寄存原草稿"]').setValue('寄存原请求与明细');
    const prep = wrapper.getComponent({ name: 'PrepStockPanel' }).vm.$;
    expect(composer.isUnmounted).toBe(false);
    expect(evidence.isUnmounted).toBe(false);
    await wrapper.get('[aria-label="收货用途"]').setValue('processing');
    expect(wrapper.get('[aria-label="加工要求"]').element).toHaveProperty('value', '抛光');
    expect(wrapper.text()).toContain('加工原照片.jpg');
    expect(wrapper.getComponent(RequirementComposer).vm.$).toBe(composer);
    expect(prep.isUnmounted).toBe(false);
    await wrapper.get('[aria-label="收货用途"]').setValue('prep');
    expect(wrapper.get('[aria-label="寄存原草稿"]').element).toHaveProperty('value', '寄存原请求与明细');
    expect(submitMobileIntake).not.toHaveBeenCalled();
    expect(uploadEvidenceFile).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('加工请求完成后父级草稿区收起，不销毁另一个用途尚未完成的寄存实例', async () => {
    let finish!: (value: Awaited<ReturnType<typeof submitMobileIntake>>) => void;
    vi.mocked(submitMobileIntake).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const wrapper = mountPanel(true);
    await fillNewWaybill(wrapper);
    await wrapper.get('.field-dock button').trigger('click');
    await wrapper.get('.field-dock button').trigger('click');
    await wrapper.get('[aria-label="收货用途"]').setValue('prep');
    await wrapper.get('[aria-label="寄存原草稿"]').setValue('尚待确认的原寄存');
    const prep = wrapper.getComponent({ name: 'PrepStockPanel' }).vm.$;
    finish({ alreadyExists: false, motherOrderCode: 'MD-DONE', courierNo: 'SF1048329001', customerName: '恒信珠宝', processingRequirement: '抛光', dueDate: '2026-10-20', goodsName: '戒托', quantity: 1, weight: 2.5, ledgerCode: 'LED-DONE', qrContent: '' });
    await flushPromises();
    expect(wrapper.get('[aria-label="已保存母单"]').text()).toContain('MD-DONE');
    expect(prep.isUnmounted).toBe(false);
    expect(wrapper.get('[aria-label="寄存原草稿"]').element).toHaveProperty('value', '尚待确认的原寄存');
    await wrapper.findAll('button').find(button => button.text() === '收起寄存草稿')!.trigger('click');
    await wrapper.findAll('button').find(button => button.text() === '返回寄存草稿')!.trigger('click');
    expect(wrapper.getComponent({ name: 'PrepStockPanel' }).vm.$).toBe(prep);
    expect(submitMobileIntake).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it('超时后往返两段仍用原请求重试，不因为换步骤生成第二笔身份', async () => {
    vi.mocked(submitMobileIntake).mockRejectedValue(new Error('网络超时，请原请求重试'));
    const wrapper = mountPanel(true);
    await fillNewWaybill(wrapper);
    await wrapper.get('.field-dock button').trigger('click');
    await wrapper.get('.field-dock button').trigger('click');
    await flushPromises();
    const first = vi.mocked(submitMobileIntake).mock.calls[0][0];
    await wrapper.get('.intake-stage-back').trigger('click');
    await wrapper.get('.field-dock button').trigger('click');
    await wrapper.get('.field-dock button').trigger('click');
    await flushPromises();
    expect(vi.mocked(submitMobileIntake).mock.calls[1][0]).toEqual(first);
    expect(wrapper.text()).toContain('网络超时');
    wrapper.unmount();
  });

  it('未选择客户不能进入第二段，成功数量保留本次提交单位', async () => {
    const wrapper = mountPanel(true);
    await wrapper.findAll('button').find(button => button.text() === '无运单直接建单')!.trigger('click');
    await wrapper.get('.field-dock button').trigger('click');
    expect(wrapper.text()).toContain('请先选择客户');
    expect(wrapper.get('.intake-stage-nav button:first-child').attributes('aria-current')).toBe('step');
    await fillNewWaybill(wrapper);
    await wrapper.get('[aria-label="单位"]').setValue('颗');
    vi.mocked(submitMobileIntake).mockResolvedValue({ alreadyExists: false, motherOrderCode: 'MD-UNIT', courierNo: 'SF1048329001', customerName: '恒信珠宝', processingRequirement: '抛光', dueDate: '2026-10-20', goodsName: '戒托', quantity: 1, weight: 2.5, ledgerCode: 'LED-UNIT', qrContent: '' });
    await wrapper.get('.field-dock button').trigger('click');
    await wrapper.get('.field-dock button').trigger('click');
    await flushPromises();
    expect(wrapper.get('[aria-label="已保存母单"]').text()).toContain('1 颗 / 2.5 克');
    wrapper.unmount();
  });
});
