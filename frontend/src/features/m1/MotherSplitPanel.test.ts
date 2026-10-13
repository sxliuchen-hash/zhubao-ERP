import { flushPromises, mount } from '@vue/test-utils';
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
    expect(wrapper.text()).toContain('A3K7');
    expect(wrapper.text()).toContain('B8N4');
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
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    const ids = vi.mocked(keepMotherOrder).mock.calls.map((call) => call[1]);
    expect(ids).toHaveLength(2);
    expect(ids[0]).toBe(ids[1]);
    expect(ids[0]).toMatch(/^[A-Za-z0-9._:-]{8,64}$/);
  });
});
