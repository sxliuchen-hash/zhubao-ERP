import { defineComponent } from 'vue';
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import StatusBadge from '../components/StatusBadge.vue';
import { buildLabelPrintDocument } from '../features/m1/labelPrint';
import type { LabelPayload } from '../services/labelApi';
import { formatAmount } from './formatAmount';

describe('金额展示', () => {
  it('千分位、两位小数、负数和极大值保持可读，单号不参与金额格式', () => {
    expect(formatAmount(1234.5)).toBe('1,234.50 元');
    expect(formatAmount(-88.1, '元')).toBe('-88.10 元');
    expect(formatAmount(1234567890123.4)).toBe('1,234,567,890,123.40 元');
    expect(formatAmount(Number.NaN)).toBe('—');
    expect(formatAmount(0, '')).toBe('0.00');
    const orderCode = 'CC-20260709-001-JL';
    expect(orderCode).toBe('CC-20260709-001-JL');
    expect(formatAmount(10)).not.toContain(orderCode);
  });
});

describe('按钮与状态', () => {
  const Fixture = defineComponent({
    components: { StatusBadge },
    data: () => ({ saved: 0, busy: false }),
    template: `
      <div>
        <button class="primary-button" type="button" :disabled="busy" :aria-busy="busy" @click="saved += 1">保存</button>
        <button class="danger-button" type="button" @click="saved = -1">删除</button>
        <button class="secondary-button" type="button">返回</button>
        <StatusBadge tone="green" label="合格" />
        <StatusBadge tone="blue" label="处理中" />
        <StatusBadge tone="amber" label="待办" />
        <StatusBadge tone="red" label="逾期" />
        <StatusBadge tone="gray" label="草稿" />
      </div>
    `,
  });

  it('禁用和保存中的主按钮不提交，危险操作与主操作不是同一个按钮', async () => {
    const wrapper = mount(Fixture);
    const primary = wrapper.get('button.primary-button');
    const danger = wrapper.get('button.danger-button');

    expect(primary.text()).toBe('保存');
    expect(danger.text()).toBe('删除');
    expect(primary.classes()).not.toEqual(danger.classes());

    (primary.element as HTMLButtonElement).click();
    expect(wrapper.vm.saved).toBe(1);

    await wrapper.setData({ busy: true });
    expect(primary.attributes('disabled')).toBeDefined();
    expect(primary.attributes('aria-busy')).toBe('true');
    (primary.element as HTMLButtonElement).click();
    expect(wrapper.vm.saved).toBe(1);
  });

  it('状态徽章用不同语气显示文字，不共用一个无文字色块', () => {
    const wrapper = mount(Fixture);
    const badges = wrapper.findAll('.status-badge');
    const labels = badges.map((badge) => badge.text());
    const tones = badges.map((badge) => badge.classes().find((name) => name.startsWith('tone-')));

    expect(labels).toEqual(['合格', '处理中', '待办', '逾期', '草稿']);
    expect(new Set(tones).size).toBe(labels.length);
  });
});

describe('标签打印不受应用皮肤影响', () => {
  const payload: LabelPayload = {
    type: 'MOTHER',
    typeLabel: '母单',
    primaryCode: 'CC-20260709-001-JL',
    qrContent: 'JG|mother|CC-20260709-001-JL',
    fields: [{ name: '客户', value: '恒信珠宝' }],
    footerLeft: '',
    footerRight: '',
    printedAt: '（本地样例）',
    brandTitle: '珠宝来料加工系统',
    templateCode: '',
    printerSetting: { widthMm: 60, heightMm: 40, dpi: 203, printerName: '', copies: 1 },
  };

  it('打印文档只有黑白，不带主操作蓝和品牌暖金', () => {
    const html = buildLabelPrintDocument(payload);
    expect(html).toContain('background: #fff');
    expect(html).toContain('color: #000');
    expect(html.toLowerCase()).not.toContain('#315fd1');
    expect(html.toLowerCase()).not.toContain('#ae8a52');
    expect(html.toLowerCase()).not.toContain('#17263c');
  });
});
