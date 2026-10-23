import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { armMobileScan, cancelMobileScan } from './mobileScanBridge';
import { queryScanContext } from '../../services/scanContextApi';
import { hasLiveCamera, openCameraStream, readBarcodeFromFile, readLiveBarcode } from './cameraBarcode';
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
  vi.mocked(openCameraStream).mockReset();
  vi.mocked(readLiveBarcode).mockReset();
  HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
});

describe('扫码输入组件', () => {
  const inputProps = { modelValue: '', fieldText: '单号', inputAriaLabel: '单号输入', submitLabel: '识别' };

  it('摄像头是首个操作，默认自动判断且扫码后无需再点识别', async () => {
    vi.mocked(hasLiveCamera).mockReturnValue(true);
    const stop = vi.fn();
    vi.mocked(openCameraStream).mockResolvedValue({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    vi.mocked(readLiveBarcode).mockResolvedValue('JG|mother|MD-1');
    const wrapper = mount(ScanInput, { props: inputProps });
    expect(wrapper.findAll('button')[0].text()).toBe('相机扫码');
    expect(wrapper.get('details').attributes('open')).toBeUndefined();
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');
    await flushPromises();
    expect(wrapper.emitted('recognize')).toEqual([[{ content: 'JG|mother|MD-1' }]]);
    expect(wrapper.emitted('update:modelValue')).toEqual([['JG|mother|MD-1']]);
    expect(stop).toHaveBeenCalled();
    expect(wrapper.get('[aria-label="扫码取景"]').isVisible()).toBe(false);
    wrapper.unmount();
  });

  it('取景时可以停止，停止后不再处理迟到的条码', async () => {
    vi.mocked(hasLiveCamera).mockReturnValue(true);
    const stop = vi.fn();
    vi.mocked(openCameraStream).mockResolvedValue({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    let finish: (value: string) => void = () => {};
    vi.mocked(readLiveBarcode).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const wrapper = mount(ScanInput, { props: inputProps });
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[aria-label="扫码取景"]').isVisible()).toBe(true);
    expect(wrapper.get('[role="status"]').text()).toContain('识别后自动继续');
    await wrapper.get('[aria-label="停止扫码"]').trigger('click');
    expect(stop).toHaveBeenCalled();
    expect(wrapper.get('[aria-label="单号输入"]').attributes('disabled')).toBeUndefined();
    finish('JG|mother|LATE');
    await flushPromises();
    expect(wrapper.emitted('recognize')).toBeUndefined();
    expect(wrapper.get('[role="status"]').text()).toContain('已停止扫码');
    wrapper.unmount();
  });

  it('取景时可切换二维码和条形码框，摄像头保持开启', async () => {
    vi.mocked(hasLiveCamera).mockReturnValue(true);
    const stop = vi.fn();
    vi.mocked(openCameraStream).mockResolvedValue({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    vi.mocked(readLiveBarcode).mockImplementation(() => new Promise(() => {}));
    const wrapper = mount(ScanInput, { props: inputProps });
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('.scan-target').classes()).toContain('scan-target--qr');
    expect(wrapper.get('[aria-label="扫码取景"]').text()).toContain('完整二维码');
    await wrapper.get('.scan-guide-switch button:nth-child(2)').trigger('click');
    expect(wrapper.get('.scan-target').classes()).toContain('scan-target--barcode');
    expect(wrapper.get('.scan-guide-switch button:nth-child(2)').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[aria-label="扫码取景"]').text()).toContain('完整条形码');
    expect(openCameraStream).toHaveBeenCalledOnce();
    expect(stop).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="停止扫码"]').trigger('click');
    wrapper.unmount();
  });

  it('底栏点过扫码后，当前页的扫码框挂上就打开相机', async () => {
    vi.mocked(hasLiveCamera).mockReturnValue(true);
    const stop = vi.fn();
    vi.mocked(openCameraStream).mockResolvedValue({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    vi.mocked(readLiveBarcode).mockImplementation(() => new Promise(() => {}));
    armMobileScan();
    const wrapper = mount(ScanInput, { props: { ...inputProps, bindMobileScan: true } });
    await flushPromises();
    expect(wrapper.get('[aria-label="扫码取景"]').isVisible()).toBe(true);
    wrapper.unmount();
    cancelMobileScan();
  });

  it('拍照与相册入口分开，选图识别时不显示空取景框', async () => {
    const wrapper = mount(ScanInput, { props: inputProps });
    const photo = wrapper.get('[aria-label="条码照片"]');
    const album = wrapper.get('[aria-label="相册条码照片"]');
    const albumClick = vi.spyOn(album.element as HTMLInputElement, 'click').mockImplementation(() => {});
    expect(photo.attributes('capture')).toBe('environment');
    expect(album.attributes('capture')).toBeUndefined();
    await wrapper.get('[aria-label="从相册选图"]').trigger('click');
    expect(albumClick).toHaveBeenCalledOnce();
    vi.mocked(readBarcodeFromFile).mockImplementation(() => new Promise(() => {}));
    Object.defineProperty(album.element, 'files', { configurable: true, value: [new File(['code'], 'code.png')] });
    await album.trigger('change');
    expect(wrapper.get('[role="status"]').text()).toContain('正在识别照片');
    expect(wrapper.get('[aria-label="扫码取景"]').isVisible()).toBe(false);
    wrapper.unmount();
  });

  it('查询中禁止再次打开相机，空单号在本地提示', async () => {
    const wrapper = mount(ScanInput, { props: inputProps });
    await wrapper.get('[aria-label="识别"]').trigger('click');
    expect(wrapper.emitted('recognize')).toBeUndefined();
    expect(wrapper.get('[role="status"]').text()).toContain('填写单号');
    await wrapper.setProps({ busy: true });
    await wrapper.get('[aria-label="打开摄像头"]').trigger('click');
    expect(openCameraStream).not.toHaveBeenCalled();
    expect(wrapper.get('[aria-label="拍照识别"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[aria-label="从相册选图"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[aria-label="单号输入"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('[aria-label="单号输入"]').setValue('MD-NEW');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['MD-NEW']);
    wrapper.unmount();
  });

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

  it('发货准备记录显示阶段和补充标记，不显示已发货', () => {
    const context = buildScanContext({ objectType: 'WORK_ORDER', code: 'ZD-1', motherOrderCode: 'MD-1' });
    context.progress.progressRecords = [{
      code: 'PG-1',
      targetType: 'WORK_ORDER',
      targetCode: 'ZD-1',
      motherOrderCode: 'MD-1',
      stage: 'SHIPPING',
      process: null,
      clauseNo: null,
      note: '正在打包',
      operatorId: 'wh-s4',
      operatorName: '库管员 何琳',
      operatorRole: 'WAREHOUSE_KEEPER',
      supplementary: true,
      recordedAt: '2026-10-12 11:00:00',
      requestId: 'req-ship-0002',
    }];
    const wrapper = mount(OrderContextCard, { props: { context } });
    expect(wrapper.text()).toContain('发货准备');
    expect(wrapper.text()).toContain('补充记录');
    expect(wrapper.text()).toContain('正在打包');
    expect(wrapper.text()).toContain('库管员 何琳');
    expect(wrapper.text()).not.toContain('已发货');
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
