import { flushPromises, mount } from '@vue/test-utils';
import { ref } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MotherSplitPanel from './MotherSplitPanel.vue';
import { fetchMotherLabel, fetchWorkOrderLabel } from '../../services/labelApi';
import { fetchMotherSplit, keepMotherOrder, splitMotherOrder } from '../../services/motherSplitApi';

vi.mock('../../services/motherSplitApi', () => ({
  fetchMotherSplit: vi.fn(),
  keepMotherOrder: vi.fn(),
  splitMotherOrder: vi.fn(),
}));

vi.mock('../../services/labelApi', () => ({
  fetchMotherLabel: vi.fn(),
  fetchWorkOrderLabel: vi.fn(),
}));

const undecided = {
  motherOrderCode: 'MD-1',
  decision: '',
  decisionLabel: '未选择',
  verificationCode: '',
  customerName: '恒信珠宝',
  processingRequirement: '内壁刻字',
  dueDate: '2026-10-20',
  goodsName: '戒托',
  quantityUnit: '件',
  quantity: 10,
  weight: 20,
  remainingQuantity: 10,
  remainingWeight: 20,
  qrContent: 'JG|mother|MD-1',
  children: [],
};

const printer = { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 };

function sticker(code: string, verificationCode: string, type: 'MOTHER' | 'WORK_ORDER') {
  return {
    type,
    typeLabel: type === 'MOTHER' ? '母单' : '子工单',
    primaryCode: code,
    qrContent: type === 'MOTHER' ? `JG|mother|${code}` : `JG|work_order|${code}`,
    fields: [
      { name: '验证码', value: verificationCode },
      { name: '客户', value: '恒信珠宝' },
      { name: '加工要求', value: '内壁刻字' },
      { name: '期限', value: '2026-10-20' },
      { name: '单号', value: code },
    ],
    footerLeft: '',
    footerRight: '',
    printedAt: '2026-10-08 12:00',
    brandTitle: '珠宝来料加工系统',
    templateCode: '',
    printerSetting: printer,
  };
}

describe('MotherSplitPanel', () => {
  beforeEach(() => {
    vi.mocked(fetchMotherSplit).mockResolvedValue(undecided);
    vi.mocked(keepMotherOrder).mockReset();
    vi.mocked(splitMotherOrder).mockReset();
    vi.mocked(fetchMotherLabel).mockReset();
    vi.mocked(fetchWorkOrderLabel).mockReset();
  });

  it('收料场景先选择安排方式，无权限不提供写入入口', async () => {
    const wrapper = mount(MotherSplitPanel, { props: { motherOrderCode: 'MD-1', intakeMode: true } });
    await flushPromises();
    expect(wrapper.text()).toContain('整单生产');
    expect(wrapper.text()).toContain('拆成子单');
    expect(wrapper.text()).toContain('稍后安排');
    expect(wrapper.find('[aria-label="第1张数量"]').exists()).toBe(false);
    await wrapper.findAll('button').find(button => button.text() === '稍后安排')!.trigger('click');
    expect(keepMotherOrder).not.toHaveBeenCalled(); expect(splitMotherOrder).not.toHaveBeenCalled();
    wrapper.unmount();
    const denied = mount(MotherSplitPanel, { props: { motherOrderCode: 'MD-1', intakeMode: true }, global: { provide: { 'zhubao-auth-session': ref({ role: 'RECEIVER', effectivePermissions: [] }) } } });
    await flushPromises();
    expect(denied.text()).not.toContain('整单生产');
    expect(denied.find('.split-form').exists()).toBe(false);
    denied.unmount();
  });

  it('来源分配自动合计并分别填入数量重量剩余，不猜重量', async () => {
    vi.mocked(fetchMotherSplit).mockResolvedValue({ ...undecided, sourceLines: [{ lineNo: 1, materialCode: 'XY', materialName: '星月', goodsName: '珠子', quantity: 10, quantityUnit: '件', weight: 20, remainingQuantity: 10, remainingWeight: 20 }] });
    vi.mocked(splitMotherOrder).mockRejectedValue(new Error('网络超时'));
    const wrapper = mount(MotherSplitPanel, { props: { motherOrderCode: 'MD-1' } });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text() === '按明细生成草稿')!.trigger('click');
    expect((wrapper.get('[aria-label="第1张数量"]').element as HTMLInputElement).readOnly).toBe(true);
    expect((wrapper.get('[aria-label="第1张数量"]').element as HTMLInputElement).value).toBe('10');
    await wrapper.get('[aria-label="第1张分配数量1"]').setValue('0.1');
    await wrapper.get('[aria-label="第1张分配重量1"]').setValue('0.2');
    await wrapper.findAll('button').filter(button => button.text() === '增加来源明细')[1].trigger('click');
    await wrapper.get('[aria-label="第2张来源1"]').setValue(1);
    await wrapper.get('[aria-label="第2张分配数量1"]').setValue('0.2');
    await wrapper.get('[aria-label="第2张分配重量1"]').setValue('0.3');
    await wrapper.findAll('button').filter(button => button.text() === '填入剩余量')[0].trigger('click');
    expect((wrapper.get('[aria-label="第1张数量"]').element as HTMLInputElement).value).toBe('9.8');
    expect((wrapper.get('[aria-label="第1张重量"]').element as HTMLInputElement).value).toBe('19.7');
    await wrapper.get('[aria-label="生成子单并预览贴纸"]').trigger('click'); await flushPromises();
    const original = vi.mocked(splitMotherOrder).mock.calls[0];
    expect(original[1][0]).toEqual(expect.objectContaining({ quantity: 9.8, weight: 19.7 }));
    await wrapper.findAll('button').find(button => button.text() === '用原请求核对生产安排')!.trigger('click'); await flushPromises();
    expect(vi.mocked(splitMotherOrder).mock.calls[1]).toEqual(original);
    wrapper.unmount();
  });

  it('多材质禁止整单，标签失败只补标签不再次拆单', async () => {
    const sources = [1, 2].map(lineNo => ({ lineNo, materialCode: `MAT-${lineNo}`, materialName: `材质${lineNo}`, goodsName: '珠子', quantity: 5, quantityUnit: '件', weight: 10, remainingQuantity: 5, remainingWeight: 10 }));
    vi.mocked(fetchMotherSplit).mockResolvedValue({ ...undecided, sourceLines: sources });
    vi.mocked(splitMotherOrder).mockResolvedValue({ ...undecided, decision: 'SPLIT', remainingQuantity: 0, remainingWeight: 0, children: [{ code: 'WO-1', motherOrderCode: 'MD-1', verificationCode: 'ABC', customerName: '', processingRequirement: '', dueDate: '', goodsName: '', quantity: 10, weight: 20, processes: ['抛光'], qrContent: '' }] });
    vi.mocked(fetchMotherLabel).mockRejectedValue(new Error('母单标签空间不足'));
    vi.mocked(fetchWorkOrderLabel).mockResolvedValue(sticker('WO-1', 'ABC', 'WORK_ORDER'));
    const wrapper = mount(MotherSplitPanel, { props: { motherOrderCode: 'MD-1' } }); await flushPromises();
    expect(wrapper.text()).toContain('多材质母单必须');
    expect(wrapper.text()).not.toContain('不拆，预览');
    await wrapper.findAll('button').find(button => button.text() === '按明细生成草稿')!.trigger('click');
    await wrapper.get('[aria-label="生成子单并预览贴纸"]').trigger('click'); await flushPromises();
    expect(wrapper.text()).toContain('子单已生成'); expect(wrapper.text()).toContain('标签待处理');
    await wrapper.findAll('button').find(button => button.text() === '只重试标签')!.trigger('click'); await flushPromises();
    expect(splitMotherOrder).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('不拆后预览一张带验证码的母单贴纸', async () => {
    vi.mocked(keepMotherOrder).mockResolvedValue({
      ...undecided,
      decision: 'KEEP',
      decisionLabel: '不拆',
      verificationCode: 'A3K7',
    });
    vi.mocked(fetchMotherLabel).mockResolvedValue(sticker('MD-1', 'A3K7', 'MOTHER'));
    const wrapper = mount(MotherSplitPanel, { props: { motherOrderCode: 'MD-1' } });
    await flushPromises();

    expect(wrapper.text()).toContain('不拆，预览流转贴纸');
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    expect(keepMotherOrder).toHaveBeenCalledWith('MD-1', expect.stringMatching(/^keep-[A-Za-z0-9._:-]{3,59}$/));
    expect(wrapper.text()).toContain('A3K7');
    expect(wrapper.text()).toContain('恒信珠宝');
    expect(wrapper.text()).toContain('内壁刻字');
    expect(wrapper.text()).toContain('2026-10-20');
    expect(wrapper.text()).toContain('JG|mother|MD-1');
  });

  it('数量超过剩余时不提交拆分', async () => {
    const wrapper = mount(MotherSplitPanel, { props: { motherOrderCode: 'MD-1' } });
    await flushPromises();
    await wrapper.get('[aria-label="第1张数量"]').setValue('6');
    await wrapper.get('[aria-label="第1张重量"]').setValue('8');
    await wrapper.get('[aria-label="第2张数量"]').setValue('6');
    await wrapper.get('[aria-label="第2张重量"]').setValue('8');
    await wrapper.get('[aria-label="生成子单并预览贴纸"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('数量合计不得超过母单剩余');
    expect(splitMotherOrder).not.toHaveBeenCalled();
  });

  it('拆成两张后预览两张验证码不同的子单贴纸', async () => {
    vi.mocked(splitMotherOrder).mockResolvedValue({
      ...undecided,
      decision: 'SPLIT',
      decisionLabel: '已拆成子单',
      remainingQuantity: 0,
      remainingWeight: 0,
      children: [
        {
          code: 'ZD-1',
          motherOrderCode: 'MD-1',
          verificationCode: 'A3K7',
          customerName: '恒信珠宝',
          processingRequirement: '内壁刻字',
          dueDate: '2026-10-20',
          goodsName: '戒托',
          quantity: 4,
          weight: 8,
          processes: ['雕刻', '抛光'],
          qrContent: 'JG|work_order|ZD-1',
        },
        {
          code: 'ZD-2',
          motherOrderCode: 'MD-1',
          verificationCode: 'B8N4',
          customerName: '恒信珠宝',
          processingRequirement: '内壁刻字',
          dueDate: '2026-10-20',
          goodsName: '戒托',
          quantity: 6,
          weight: 12,
          processes: ['雕刻', '镶嵌', '抛光'],
          qrContent: 'JG|work_order|ZD-2',
        },
      ],
    });
    vi.mocked(fetchMotherLabel).mockResolvedValue(sticker('MD-1', '', 'MOTHER'));
    vi.mocked(fetchWorkOrderLabel).mockImplementation(async (code: string) => (
      code === 'ZD-1' ? sticker('ZD-1', 'A3K7', 'WORK_ORDER') : sticker('ZD-2', 'B8N4', 'WORK_ORDER')
    ));
    const wrapper = mount(MotherSplitPanel, { props: { motherOrderCode: 'MD-1' } });
    await flushPromises();
    await wrapper.get('[aria-label="第1张数量"]').setValue('4');
    await wrapper.get('[aria-label="第1张重量"]').setValue('8');
    await wrapper.get('[aria-label="第2张数量"]').setValue('6');
    await wrapper.get('[aria-label="第2张重量"]').setValue('12');
    await wrapper.get('[aria-label="第1张镶嵌"]').setValue(false);
    await wrapper.get('[aria-label="生成子单并预览贴纸"]').trigger('click');
    await flushPromises();

    expect(splitMotherOrder).toHaveBeenCalledWith('MD-1', [
      { quantity: 4, weight: 8, processes: ['雕刻', '抛光'] },
      { quantity: 6, weight: 12, processes: ['雕刻', '镶嵌', '抛光'] },
    ], expect.stringMatching(/^split-[A-Za-z0-9._:-]{3,58}$/));
    expect(fetchMotherLabel).toHaveBeenCalledWith('MD-1');
    expect(wrapper.text()).toContain('A3K7');
    expect(wrapper.text()).toContain('B8N4');
    expect(wrapper.text()).toContain('JG|mother|MD-1');
    expect(wrapper.text()).toContain('JG|work_order|ZD-1');
    expect(wrapper.text()).toContain('JG|work_order|ZD-2');
  });

  it('失败后重试沿用同一请求编号', async () => {
    vi.mocked(keepMotherOrder)
      .mockRejectedValueOnce(new Error('超时'))
      .mockResolvedValueOnce({
        ...undecided,
        decision: 'KEEP',
        decisionLabel: '不拆',
        verificationCode: 'A3K7',
      });
    vi.mocked(fetchMotherLabel).mockResolvedValue(sticker('MD-1', 'A3K7', 'MOTHER'));
    const wrapper = mount(MotherSplitPanel, { props: { motherOrderCode: 'MD-1' } });
    await flushPromises();

    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('超时');
    await wrapper.findAll('button').find(button => button.text() === '用原请求核对生产安排')!.trigger('click');
    await flushPromises();

    const ids = vi.mocked(keepMotherOrder).mock.calls.map((call) => call[1]);
    expect(ids).toHaveLength(2);
    expect(ids[0]).toBe(ids[1]);
    expect(ids[0]).toMatch(/^[A-Za-z0-9._:-]{8,64}$/);
  });
});
