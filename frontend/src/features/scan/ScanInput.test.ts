import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { queryScanContext } from '../../services/scanContextApi';
import { hasLiveCamera, readBarcodeFromFile } from './cameraBarcode';
import OrderContextCard from './OrderContextCard.vue';
import ScanInput from './ScanInput.vue';
import { buildScanContext } from './scanContextFixture';

vi.mock('../../services/scanContextApi', async () => {
  const actual = await vi.importActual<typeof import('../../services/scanContextApi')>('../../services/scanContextApi');
  return { ...actual, queryScanContext: vi.fn() };
});

vi.mock('./cameraBarcode', () => ({
  hasLiveCamera: vi.fn(() => false),
  openCameraStream: vi.fn(),
  readLiveBarcode: vi.fn(),
  readBarcodeFromFile: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(queryScanContext).mockReset();
  vi.mocked(hasLiveCamera).mockReturnValue(false);
  vi.mocked(readBarcodeFromFile).mockReset();
});

describe('扫码输入组件', () => {
  it('相机不可用时改为选照片，并且不查询单据', async () => {
    const clicks = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {});
    const wrapper = mount(ScanInput, {
      props: {
        modelValue: '',
        fieldText: '单号',
        inputAriaLabel: '单号输入',
        submitLabel: '识别',
      },
    });
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');
    expect(clicks).toHaveBeenCalled();
    expect(queryScanContext).not.toHaveBeenCalled();
    clicks.mockRestore();
  });

  it('照片只把识别出的原始内容交给外层', async () => {
    vi.mocked(readBarcodeFromFile).mockResolvedValue('JG|outbound|OUT-7');
    const wrapper = mount(ScanInput, {
      props: {
        modelValue: '',
        fieldText: '单号',
        inputAriaLabel: '单号输入',
        submitLabel: '识别',
      },
    });
    const file = new File(['barcode'], 'code.png', { type: 'image/png' });
    await wrapper.get('[aria-label="条码照片"]').trigger('change');
    const input = wrapper.get('[aria-label="条码照片"]').element as HTMLInputElement;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    await wrapper.get('[aria-label="条码照片"]').trigger('change');
    await flushPromises();
    expect(wrapper.emitted('recognize')?.[0]?.[0]).toEqual({ content: 'JG|outbound|OUT-7' });
    expect(queryScanContext).not.toHaveBeenCalled();
  });

  it('手输类型由用户选择，不会按长度当成运单', async () => {
    const wrapper = mount(ScanInput, {
      props: {
        modelValue: 'SF1048329001',
        fieldText: '单号',
        inputAriaLabel: '单号输入',
        submitLabel: '识别',
      },
    });
    await wrapper.get('[aria-label="识别"]').trigger('click');
    expect(wrapper.emitted('recognize')?.[0]?.[0]).toEqual({ content: 'SF1048329001' });
    expect(queryScanContext).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="单号输入"]').setValue('SF1');
    await wrapper.get('[aria-label="单号输入"]').setValue('SF1048329001');
    expect(wrapper.emitted('recognize')).toHaveLength(1);

    await wrapper.get('[aria-label="对象类型"]').setValue('WORK_ORDER');
    expect(wrapper.emitted('draft')).toBeTruthy();
    await wrapper.get('[aria-label="识别"]').trigger('click');
    expect(wrapper.emitted('recognize')?.at(-1)?.[0]).toEqual({
      content: 'SF1048329001',
      objectType: 'WORK_ORDER',
    });
  });
});

describe('扫码上下文卡片', () => {
  it('按服务端身份展示五类对象，空数量不显示成 0', () => {
    const samples = [
      ['MOTHER_ORDER', 'MD-1', '母单'],
      ['WORK_ORDER', 'ZD-1', '子单'],
      ['OUTBOUND_ORDER', 'OUT-1', '出货单'],
      ['REWORK', 'FG-1', '返工单'],
      ['EXPRESS_NO', 'SF1', '快递运单'],
    ] as const;
    for (const [objectType, code, label] of samples) {
      const wrapper = mount(OrderContextCard, {
        props: {
          context: buildScanContext({
            objectType,
            code,
            customerName: `${code}客户`,
            quantity: null,
            weight: null,
          }),
        },
      });
      expect(wrapper.text()).toContain(`${label} ${code}`);
      expect(wrapper.text()).toContain(`${code}客户`);
      expect(wrapper.text()).toContain('未填写');
      expect(wrapper.text()).toContain('暂无过程记录');
      expect(wrapper.find('[aria-label="保存过程记录"]').exists()).toBe(false);
      expect(wrapper.text()).not.toContain('0 件');
    }
  });

  it('已拆母单必须点选，不会把第一张子单当成已选', async () => {
    const wrapper = mount(OrderContextCard, {
      props: {
        context: buildScanContext({
          objectType: 'MOTHER_ORDER',
          code: 'MD-1',
          requiresSelection: true,
          relatedTargets: [
            { objectType: 'WORK_ORDER', code: 'ZD-1', motherOrderCode: 'MD-1', requirement: '甲', quantity: 1, quantityUnit: '件' },
            { objectType: 'WORK_ORDER', code: 'ZD-2', motherOrderCode: 'MD-1', requirement: '乙', quantity: 1, quantityUnit: '件' },
          ],
        }),
      },
    });
    expect(wrapper.text()).toContain('不会自动打开第一张');
    expect(wrapper.find('[aria-pressed="true"]').exists()).toBe(false);
    await wrapper.get('[aria-label="选择ZD-2"]').trigger('click');
    expect(wrapper.emitted('select')?.[0]?.[0]).toMatchObject({ code: 'ZD-2', objectType: 'WORK_ORDER' });
    expect(wrapper.emitted('select')?.[0]?.[0]).not.toMatchObject({ code: 'ZD-1' });
  });

  it('多笔余额只列出维度，不默认第一笔', () => {
    const wrapper = mount(OrderContextCard, {
      props: {
        context: buildScanContext({
          objectType: 'WORK_ORDER',
          code: 'ZD-1',
          balanceSelectionRequired: true,
          balanceCandidates: [
            {
              materialCode: 'AU18', warehouseCode: '原料仓', ownerType: '客户', ownerCustomerId: 1,
              customerId: 1, motherOrderCode: 'MD-1', workOrderCode: 'ZD-1', stockStatus: '在制',
              quantity: 1, weight: 2,
            },
            {
              materialCode: 'AU18', warehouseCode: '成品仓', ownerType: '客户', ownerCustomerId: 1,
              customerId: 1, motherOrderCode: 'MD-1', workOrderCode: 'ZD-1', stockStatus: '成品',
              quantity: 3, weight: 4,
            },
          ],
        }),
      },
    });
    expect(wrapper.text()).toContain('当前没有默认选中');
    expect(wrapper.text()).toContain('原料仓');
    expect(wrapper.text()).toContain('成品仓');
    expect(wrapper.find('button.scan-choice-button').exists()).toBe(false);
  });
});
