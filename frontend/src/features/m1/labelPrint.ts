import { renderQrSvg } from '../../services/qrcode';
import type { LabelPayload } from '../../services/labelApi';

/** 屏幕预览相对实物的放大倍数，打印文档仍按 1:1 毫米出纸。 */
export const labelPreviewScale = 1.65;

/** 黑白标签版式。只用黑字、白底和细黑线，不铺色块。 */
export const labelFaceCss = `
.bw-label {
  box-sizing: border-box;
  background: #fff;
  color: #000;
  border: 0.3mm solid #000;
  padding: 1.1mm 1.3mm 0.9mm;
  display: grid;
  grid-template-columns: min(18mm, 42%) minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr) auto;
  column-gap: 1.2mm;
  overflow: hidden;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  line-height: 1.15;
}
.bw-qr { grid-row: 1; grid-column: 1; align-self: center; min-width: 0; }
.bw-qr svg { width: 100%; height: auto; display: block; }
.bw-main { grid-row: 1; grid-column: 2; min-width: 0; overflow: hidden; }
.bw-top { display: flex; align-items: center; justify-content: space-between; gap: 1mm; }
.bw-brand {
  min-width: 0;
  overflow: hidden;
  font-size: 6pt;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bw-type {
  flex: none;
  border: 0.25mm solid #000;
  padding: 0 0.7mm;
  font-size: 7pt;
  font-weight: 700;
  line-height: 1.35;
}
.bw-code {
  margin-top: 0.5mm;
  font-size: 9pt;
  font-weight: 700;
  letter-spacing: 0;
  word-break: break-all;
}
.bw-fields {
  margin: 0.5mm 0 0;
  display: grid;
  grid-template-columns: 8mm minmax(0, 1fr);
  column-gap: 0.6mm;
  row-gap: 0.15mm;
  font-size: 6.5pt;
}
.bw-fields dt, .bw-fields dd { margin: 0; min-width: 0; }
.bw-fields dd {
  font-weight: 700;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bw-foot {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  gap: 1mm;
  border-top: 0.2mm solid #000;
  margin-top: 0.5mm;
  padding-top: 0.35mm;
  font-size: 6pt;
}
.bw-foot span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
`;

/** 生成一张黑白标签的 HTML，供预览和打印共用。 */
export function buildLabelMarkup(payload: LabelPayload): string {
  const width = positiveMm(payload.printerSetting.widthMm, 60);
  const height = positiveMm(payload.printerSetting.heightMm, 40);
  const fields = payload.fields
    .filter((field) => field.name.trim() !== '')
    .map(
      (field) =>
        `<dt>${escapeHtml(field.name)}</dt><dd>${escapeHtml(field.value)}</dd>`,
    )
    .join('');
  const footerLeft = payload.footerLeft.trim();
  const footerRight = payload.footerRight.trim();
  const footer =
    footerLeft || footerRight
      ? `<div class="bw-foot"><span>${escapeHtml(footerLeft)}</span><span>${escapeHtml(footerRight)}</span></div>`
      : '';

  return [
    `<article class="bw-label" style="width:${width}mm;height:${height}mm">`,
    `<div class="bw-qr">${renderQrSvg(payload.qrContent)}</div>`,
    '<div class="bw-main">',
    '<div class="bw-top">',
    `<span class="bw-brand">${escapeHtml(payload.brandTitle)}</span>`,
    `<span class="bw-type">${escapeHtml(payload.typeLabel)}</span>`,
    '</div>',
    `<div class="bw-code">${escapeHtml(payload.primaryCode)}</div>`,
    fields ? `<dl class="bw-fields">${fields}</dl>` : '',
    '</div>',
    footer,
    '</article>',
  ].join('');
}

/** 只含标签的打印文档，按纸张毫米尺寸分页，份数各占一页。 */
export function buildLabelPrintDocument(payload: LabelPayload): string {
  const width = positiveMm(payload.printerSetting.widthMm, 60);
  const height = positiveMm(payload.printerSetting.heightMm, 40);
  const copies = clampCopies(payload.printerSetting.copies);
  const sheet = `<div class="bw-sheet">${buildLabelMarkup(payload)}</div>`;
  return [
    '<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"/>',
    '<title>黑白标签</title><style>',
    `@page { size: ${width}mm ${height}mm; margin: 0; }`,
    'html,body{margin:0;padding:0;background:#fff;color:#000;}',
    labelFaceCss,
    '.bw-sheet{page-break-after:always;}',
    '.bw-sheet:last-child{page-break-after:auto;}',
    '</style></head><body>',
    sheet.repeat(copies),
    '</body></html>',
  ].join('');
}

/** 在隐藏框架里只打印标签，避免把侧栏和表单一起送去打印机。 */
export function openLabelPrint(payload: LabelPayload): void {
  const frame = document.createElement('iframe');
  frame.title = '标签打印';
  frame.setAttribute('aria-hidden', 'true');
  frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;';
  document.body.appendChild(frame);
  const doc = frame.contentDocument;
  const win = frame.contentWindow;
  if (!doc || !win) {
    frame.remove();
    return;
  }
  doc.open();
  doc.write(buildLabelPrintDocument(payload));
  doc.close();
  const removeFrame = () => window.setTimeout(() => frame.remove(), 400);
  win.addEventListener('afterprint', removeFrame);
  win.focus();
  window.setTimeout(() => win.print(), 60);
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function positiveMm(value: number, fallback: number): number {
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

function clampCopies(value: number): number {
  if (!Number.isFinite(value) || value < 1) return 1;
  return Math.min(Math.floor(value), 20);
}
