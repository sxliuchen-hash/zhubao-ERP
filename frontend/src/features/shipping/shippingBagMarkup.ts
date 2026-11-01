import { renderQrSvg } from '../../services/qrcode';
import type { LabelPayload } from '../../services/labelApi';
const escape = (text: string) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
export const shippingFaceCss = `.shipping-face{width:40mm;height:30mm;padding:1mm;overflow:hidden;border:.2mm solid black;background:white;color:black;font-family:SimSun;display:grid;grid-template-rows:4mm 18mm 4mm;gap:.5mm}.shipping-face header{font-size:7pt;font-weight:bold;white-space:nowrap}.shipping-face section{display:grid;grid-template-columns:17mm 1fr;gap:1mm;min-width:0}.shipping-face svg{width:17mm;height:17mm}.shipping-face dl{margin:0;display:grid;grid-template-columns:5mm 1fr;font-size:6.5pt;line-height:3.5mm;min-width:0}.shipping-face dt,.shipping-face dd{margin:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.shipping-face footer{font-size:6.5pt;line-height:3.5mm;white-space:nowrap;overflow:hidden}`;
/** 发货袋使用独立40×30版面，固定字号；长文字仅取摘要，完整确认内容由同一码读取。 */
export function shippingBagMarkup(payload: LabelPayload): string {
  if (payload.type !== 'SHIPPING_BAG' || payload.printerSetting.widthMm !== 40 || payload.printerSetting.heightMm !== 30) throw new Error('这里只打印40×30发货袋贴纸');
  const value = (name: string) => payload.fields.find(field => field.name === name)?.value ?? '未记录';
  const rows = ['客户', '数量', '重量', '材质', '规格'].map(name => `<dt>${name}</dt><dd>${escape(value(name))}</dd>`).join('');
  const requirement = value('要求'); const brief = requirement.length > 8 ? `${requirement.slice(0, 8)}…扫码详见` : requirement;
  return `<article class="shipping-face"><header>发 ${escape(payload.primaryCode)}</header><section><div>${renderQrSvg(new URL(payload.qrContent, window.location.origin).toString())}</div><dl>${rows}</dl></section><footer>要求 ${escape(brief)}</footer></article>`;
}
