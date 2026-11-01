import { expect, it } from 'vitest';
import { shippingBagMarkup, shippingFaceCss } from './shippingBagMarkup';
import { renderQrSvg } from '../../services/qrcode';
import type { LabelPayload } from '../../services/labelApi';
it('40×30发货贴纸编码可打开的真实网址，转义客户文本且保持固定字号', () => {
  const token = 'a'.repeat(64);
  const payload: LabelPayload = { type: 'SHIPPING_BAG', typeLabel: '发货袋', primaryCode: 'FH-REAL-1', qrContent: `/d/${token}?bag=1`, fields: [{ name: '客户', value: '<客户甲>' }, { name: '数量', value: '480颗' }, { name: '重量', value: '9.4克' }, { name: '要求', value: '抛光穿串后逐个核对孔径' }], footerLeft: '', footerRight: '', printedAt: '', brandTitle: '', templateCode: '', printerSetting: { widthMm: 40, heightMm: 30, dpi: 203, printerName: '', copies: 1 } };
  const markup = shippingBagMarkup(payload);
  expect(markup).toContain(renderQrSvg(`${window.location.origin}/d/${token}?bag=1`));
  expect(markup).toContain('&lt;客户甲&gt;'); expect(markup).toContain('480颗'); expect(markup).toContain('扫码详见');
  expect(shippingFaceCss).toContain('width:40mm;height:30mm'); expect(shippingFaceCss).toContain('font-size:6.5pt');
  expect(() => shippingBagMarkup({ ...payload, type: 'MOTHER' })).toThrow('这里只打印40×30发货袋贴纸');
});
