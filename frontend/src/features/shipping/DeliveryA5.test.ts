import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import DeliveryA5Sheet from './DeliveryA5Sheet.vue';
import { buildA5Pages } from './deliveryA5';
import type { A5BagLine } from './deliveryA5';

function line(lineNo: number, unit: string, quantity: string, weight: string | null): A5BagLine {
  return {
    lineNo,
    bagCode: `FH-20261030-0001-${lineNo}`,
    material: '牙骨',
    spec: '8mm',
    quantity,
    unit,
    weight,
    requirement: '孔径1.2',
    sourceCode: 'MD-20261030-1001',
  };
}

describe('A5发货清单', () => {
  it('小数与大数量按单位精确合计，非法数量拒绝打印', () => {
    const pages = buildA5Pages({ deliveryId: 'FH-1', customerName: '客户', logistics: '', feeAmount: null,
      lines: [line(1, '克', '0.1', null), line(2, '克', '0.20', null), line(3, '颗', '9007199254740993.001', null), line(4, '颗', '0.009', null)] });
    expect(pages[0].pageTotals).toEqual([{ unit: '克', quantity: '0.3' }, { unit: '颗', quantity: '9007199254740993.01' }]);
    expect(() => buildA5Pages({ deliveryId: 'FH-1', customerName: '客户', logistics: '', feeAmount: null, lines: [line(1, '颗', '未知', null)] }))
      .toThrow('发货数量格式无效');
  });
  it('按单位合计并续页，未定价不补0，不进入标签队列', async () => {
    const lines = [
      line(1, '颗', '300', null),
      line(2, '克', '1.5', '1.5'),
      ...Array.from({ length: 7 }, (_, index) => line(index + 3, '颗', '1', '1')),
    ];
    const pages = buildA5Pages({
      deliveryId: 'FH-20261030-0001',
      customerName: '客户甲',
      logistics: '顺丰',
      lines,
      feeAmount: null,
    });
    expect(pages).toHaveLength(2);
    expect(pages[0].pageNo).toBe(1);
    expect(pages[0].pageCount).toBe(2);
    expect(pages[0].deliveryId).toBe('FH-20261030-0001');
    expect(pages[1].deliveryId).toBe('FH-20261030-0001');
    expect(pages[0].grandTotals).toBeNull();
    expect(pages[1].grandTotals).toEqual(expect.arrayContaining([
      { unit: '颗', quantity: '307' },
      { unit: '克', quantity: '1.5' },
    ]));
    expect(pages[0].rows[0].weight).toBeNull();
    expect(pages.every(page => page.feeText === '未定价')).toBe(true);

    const wrapper = mount(DeliveryA5Sheet, {
      props: {
        deliveryId: 'FH-20261030-0001',
        customerName: '客户甲',
        logistics: '顺丰',
        lines,
        feeAmount: null,
        officePrinters: ['办公室A5'],
      },
    });
    expect(wrapper.html()).toContain('width:148mm;height:210mm');
    expect(wrapper.html()).not.toContain('40mm');
    expect(wrapper.text()).toContain('未称');
    expect(wrapper.text()).toContain('未定价');
    expect(wrapper.text()).not.toContain('0元');
    expect(wrapper.text()).not.toContain('工资');
    await wrapper.get('button').trigger('click');
    expect(wrapper.emitted('print')?.[0]).toEqual(['办公室A5']);
    wrapper.unmount();
  });
});
