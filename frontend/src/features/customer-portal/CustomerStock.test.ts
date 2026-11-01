import { flushPromises, mount } from '@vue/test-utils';
import entrySource from '../../customer-main.ts?raw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CustomerStockPage from './CustomerStockPage.vue';
import { bucketMeaning, filterLines, weightText, type CustomerStockLine } from './customerStock';
import { downloadEvidence, loadEvidence, loadOwnedStock, loadStatement, loginCustomer } from './customerPortalApi';

vi.mock('./customerPortalApi', () => ({
  loginCustomer: vi.fn(),
  loadOwnedStock: vi.fn(),
  loadEvidence: vi.fn(),
  downloadEvidence: vi.fn(),
  loadStatement: vi.fn(),
}));

const photo = {
  grantId: 'CEG1',
  attachmentCode: 'ATT-PHOTO',
  fileName: 'bead.jpg',
  mediaType: 'image/jpeg',
  kind: 'IMAGE',
  downloadTicket: 'ticket-1',
};

const lines: CustomerStockLine[] = [
  { materialCode: '牙骨', unit: '颗', bucket: 'IN_FACTORY', quantity: '245', weightRead: 'UNKNOWN', knownWeight: null, reconciliationPending: false },
  { materialCode: '牙骨', unit: '颗', bucket: 'OUTSOURCING', quantity: '45', weightRead: 'UNKNOWN', knownWeight: null, reconciliationPending: false },
  { materialCode: '紫金鼠', unit: '串', bucket: 'CONSIGNMENT', quantity: '3', weightRead: 'UNKNOWN', knownWeight: null, reconciliationPending: false },
];

describe('客户手机库存', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('独立入口不加载内部后台', () => {
    const entry = entrySource;
    expect(entry).toContain('CustomerStockPage.vue');
    expect(entry).not.toContain('App.vue');
    expect(entry).not.toContain('business-workspaces.css');
    expect(entry).not.toContain('styles.css');
  });

  it('凭据未核定不能进入库存，外协和未称都有明确含义', async () => {
    expect(weightText(lines[0])).toBe('未称');
    expect(weightText(lines[0])).not.toBe('0');
    expect(bucketMeaning('OUTSOURCING')).toContain('不重复计入本厂');
    expect(bucketMeaning('IN_FACTORY')).toContain('不重复计入');
    expect(bucketMeaning('SHIPPED')).toContain('不算当前在厂');
    expect(filterLines(lines, '串').map((line) => line.materialCode)).toEqual(['紫金鼠']);

    vi.mocked(loginCustomer).mockRejectedValue(new Error('客户凭据交付方式尚未核定，不能登录'));
    const blocked = mount(CustomerStockPage);
    await blocked.get('input[autocomplete="username"]').setValue('portal-a');
    await blocked.get('input[autocomplete="current-password"]').setValue('Secret123!');
    await blocked.get('form').trigger('submit');
    await flushPromises();
    expect(blocked.text()).toContain('凭据交付方式尚未核定，不能进入库存');
    expect(blocked.text()).not.toContain('暂无自有库存');
    expect(loadOwnedStock).not.toHaveBeenCalled();
    blocked.unmount();

    vi.mocked(loginCustomer).mockResolvedValue({ token: 'portal-token' } as never);
    vi.mocked(loadOwnedStock).mockResolvedValue({
      customerId: 1,
      lines,
      sources: [{ kind: 'SAMPLING', state: 'NOT_IMPLEMENTED', note: '打样库存适配尚未实现', withheld: false }],
    });
    vi.mocked(loadEvidence).mockResolvedValue({ customerId: 1, visibility: 'UNDECIDED', items: [] });
    const page = mount(CustomerStockPage);
    await page.get('form').trigger('submit');
    await flushPromises();
    expect(page.text()).toContain('按需刷新，不是实时推送');
    expect(page.text()).toContain('本次读取时间');
    expect(page.text()).toContain('牙骨 颗 本厂 245 未称');
    expect(page.text()).toContain('打样库存适配尚未实现');
    expect(page.text()).toContain('照片可见范围尚未核定');
    await page.get('input[placeholder="材质或单位"]').setValue('紫金鼠');
    expect(page.text()).not.toContain('牙骨');
    await page.get('input[placeholder="材质或单位"]').setValue('');
    const factory = page.findAll('li button').find((button) => button.text().includes('外协'));
    await factory?.trigger('click');
    expect(page.text()).toContain('外协是其中项，不重复计入本厂');
    page.unmount();

    vi.mocked(loadEvidence).mockResolvedValue({ customerId: 1, visibility: 'UNDECIDED', items: [photo] });
    vi.mocked(downloadEvidence).mockResolvedValue({ fileName: 'bead.jpg' });
    const withPhoto = mount(CustomerStockPage);
    await withPhoto.get('form').trigger('submit');
    await flushPromises();
    await withPhoto.get('button[type="button"]').trigger('click');
    const download = withPhoto.findAll('button').find((button) => button.text() === '下载获准文件');
    await download?.trigger('click');
    await flushPromises();
    expect(downloadEvidence).toHaveBeenCalledWith(photo);
    expect(withPhoto.text()).toContain('已下载 bead.jpg');
    await withPhoto.get('button[type="button"]').trigger('click');
    const statementButton = withPhoto.findAll('button').find((button) => button.text() === '读取对账');
    await statementButton?.trigger('click');
    expect(withPhoto.text()).toContain('请指定截止日');
    expect(loadStatement).not.toHaveBeenCalled();
    vi.mocked(loadStatement).mockResolvedValue({
      customerId: 1,
      cutoff: '2026-10-03T00:00:00Z',
      receivedQuantity: '1000',
      quantities: [{ materialCode: '牙骨', sourceCode: 'MD-1', unit: '颗', receivedQuantity: '1000', shippedQuantity: '300', lossQuantity: '10' }],
      shippedQuantity: '300',
      lossQuantity: '10',
      lossWeight: '未称',
      factoryAndOutside: '截止日的在厂与在外尚未核定',
      bills: '账单可见范围尚未核定',
      afterCutoff: [],
      pending: [],
      exportText: '收料,1000\n出厂,300\n损耗,10',
    });
    await withPhoto.get('input[placeholder="2026-10-03T00:00:00Z"]').setValue('2026-10-03T00:00:00Z');
    await statementButton?.trigger('click');
    await flushPromises();
    expect(withPhoto.text()).toContain('收料 1000');
    expect(withPhoto.text()).toContain('收料,1000');
    expect(withPhoto.text()).toContain('账单可见范围尚未核定');
    expect(withPhoto.text()).not.toContain('工资');
    vi.mocked(loadStatement).mockResolvedValue({
      customerId: 1, cutoff: '2026-10-03T00:00:00Z', receivedQuantity: null, shippedQuantity: null, lossQuantity: null,
      quantities: [
        { materialCode: '牙骨', sourceCode: 'MD-1', unit: '颗', receivedQuantity: '100', shippedQuantity: '30', lossQuantity: '2' },
        { materialCode: '足金', sourceCode: 'MD-2', unit: '克', receivedQuantity: '0.5', shippedQuantity: '0.2', lossQuantity: '0' },
      ],
      lossWeight: '未称', factoryAndOutside: '待核', bills: '待核', afterCutoff: [], pending: [], exportText: '按单位分别核对',
    });
    await statementButton?.trigger('click');
    await flushPromises();
    expect(withPhoto.text()).toContain('不能合并相加');
    expect(withPhoto.text()).toContain('收料 100颗');
    expect(withPhoto.text()).toContain('收料 0.5克');
    withPhoto.unmount();
  });
});
