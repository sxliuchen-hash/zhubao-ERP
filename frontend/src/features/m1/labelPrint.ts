import { renderQrSvg } from '../../services/qrcode';
import type { LabelPayload } from '../../services/labelApi';
import { customerMarkPattern } from './customerInitials';

/** 屏幕预览相对实物的放大倍数，打印文档仍按 1:1 毫米出纸。 */
export const labelPreviewScale = 1.65;

/** 黑白标签版式。只用黑字、白底和细黑线，不铺色块。 */
export const labelFaceCss = `
.bw-has-storage { position: relative; padding-bottom: calc(var(--storage-height) + 1mm) !important; }
.bw-storage { position: absolute; bottom: .6mm; left: .8mm; right: .8mm; height: var(--storage-height); color: black; background: white; border-top: .2mm solid black; font-size: 6pt; line-height: 1; }
.bw-storage-source { height: 5mm; display: flex; gap: .5mm; align-items: center; white-space: nowrap; }
.bw-storage-mark { width: 4mm; height: 4mm; flex: 0 0 4mm; overflow: visible; }
.bw-storage-note { font-size: 6pt; line-height: 3mm; overflow-wrap: anywhere; }
.bw-label {
  box-sizing: border-box;
  background: #fff;
  color: #000;
  border: 0.3mm solid #000;
  padding: 1.1mm 1.3mm 0.9mm;
  display: grid;
  grid-template-columns: min(var(--bw-qr-width, 18mm), 42%) minmax(0, 1fr);
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
  font-size: var(--bw-title-font, 7pt);
  font-weight: 700;
  line-height: 1.35;
}
.bw-code {
  margin-top: 0.5mm;
  font-size: var(--bw-code-font, 9pt);
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
  font-size: var(--bw-field-font, 6.5pt);
}
.bw-fields div { display: contents; }
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
  font-size: var(--bw-footer-font, 6pt);
}
.bw-foot span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bw-label--compact {
  display: block;
  padding: 0.8mm;
  border-width: 0.2mm;
  overflow: hidden;
}
.bw-label--compact .bw-top {
  margin-bottom: 0.45mm;
  justify-content: flex-start;
  gap: 0.8mm;
  min-width: 0;
}
.bw-label--compact .bw-brand { display: none; }
.bw-label--compact .bw-type {
  padding: 0 0.5mm;
  font-size: var(--bw-title-font, 7.5pt);
  line-height: 1.2;
}
.bw-label--compact .bw-code {
  flex: 1 1 auto;
  margin: 0;
  min-width: 0;
  overflow: hidden;
  font-size: var(--bw-code-font, 8.5pt);
  line-height: 1.1;
  white-space: nowrap;
  word-break: normal;
  text-overflow: ellipsis;
}
.bw-label--compact .bw-qr {
  float: left;
  width: min(var(--bw-qr-width, 17mm), 46%);
  margin: 0 0.8mm 0.35mm 0;
}
.bw-label--compact .bw-fields {
  display: block;
  margin: 0;
  font-size: var(--bw-field-font, 7.5pt);
  line-height: 1.18;
  overflow: visible;
}
.bw-label--compact .bw-fields div {
  display: block;
  margin-bottom: 0.45mm;
}
.bw-label--compact .bw-fields dt {
  display: inline;
  font-weight: 400;
}
.bw-label--compact .bw-fields dd {
  display: inline;
  font-weight: 700;
  overflow: visible;
  white-space: normal;
  overflow-wrap: anywhere;
}
.bw-label--compact .bw-fields dt::after { content: '：'; }
.bw-label--compact .bw-fields div:first-child dd { font-size: var(--bw-emphasis-font, 8.5pt); }
.bw-label--compact .bw-foot { clear: both; font-size: var(--bw-footer-font, 6pt); }
`;

/** 生成一张黑白标签的 HTML，供预览和打印共用。 */
export function buildLabelMarkup(payload: LabelPayload): string {
  const width = positiveMm(payload.printerSetting.widthMm, 40);
  const height = positiveMm(payload.printerSetting.heightMm, 30);
  const sources = (payload.storageSources ?? []).filter(source => source.marks.length || source.instruction.trim());
  const chars = Math.max(1, Math.floor((width - 3) / 2.2));
  const bandHeight = sources.reduce((sum, source) => sum + 5 + (source.instruction.trim() ? Math.ceil(source.instruction.length / chars) * 3 : 0), 0);
  if (bandHeight && (width < 35 || bandHeight + (payload.layout?.qrWidthMm ?? 17) + 7 > height)) throw new Error('保存标识与来源说明空间不足，请调整标签尺寸或按来源明细打印');
  const storage = sources.length ? `<div class="bw-storage">${sources.map(source => `<div class="bw-storage-source"><span>${escapeHtml(source.sourceLabel)}</span>${source.marks.map(mark => {
    if (!['冷', '冻', '真', '光', '潮'].includes(mark)) throw new Error('保存标识无效');
    return `<svg class="bw-storage-mark" viewBox="0 0 24 24" aria-label="${mark}"><circle cx="12" cy="12" r="10.5" fill="white" stroke="black" stroke-width="1.5"/><text x="12" y="12" text-anchor="middle" dominant-baseline="central" font-size="16" font-weight="bold">${mark}</text></svg>`;
  }).join('')}</div>${source.instruction ? `<div class="bw-storage-note">${escapeHtml(source.instruction)}</div>` : ''}`).join('')}</div>` : '';
  const mode = payload.layout?.mode;
  const compact = mode === 'COMPACT' || (mode !== 'STANDARD' && width <= 40 && height <= 30);
  const availableFields = payload.fields.filter((field) => field.name.trim() !== '' && field.value.trim() !== '');
  const visibleFields = compact && !payload.templateCode
    ? availableFields.filter((field) => field.name.trim() !== '单号').slice(0, 4)
    : availableFields;
  const fields = visibleFields
    .map(
      (field) =>
        `<div><dt>${escapeHtml(compactFieldName(field.name, compact))}</dt><dd>${escapeHtml(field.value)}</dd></div>`,
    )
    .join('');
  const footerLeft = payload.footerLeft.trim();
  const footerRight = payload.footerRight.trim();
  const hasCustomerMark = customerMarkPattern.test(footerLeft) || customerMarkPattern.test(footerRight);
  const footer =
    (footerLeft || footerRight) && (!compact || !!payload.templateCode || hasCustomerMark)
      ? `<div class="bw-foot"><span>${escapeHtml(footerLeft)}</span><span>${escapeHtml(footerRight)}</span></div>`
      : '';

  const type = `<span class="bw-type">${escapeHtml(payload.typeLabel)}</span>`;
  const code = `<span class="bw-code">${escapeHtml(payload.primaryCode)}</span>`;
  const heading = compact
      ? `<div class="bw-top">${type}${code}</div>`
      : `<div class="bw-top"><span class="bw-brand">${escapeHtml(payload.brandTitle)}</span>${type}</div><div class="bw-code">${escapeHtml(payload.primaryCode)}</div>`;
  const qr = `<div class="bw-qr">${renderQrSvg(payload.qrContent)}</div>`;
  const fieldList = fields ? `<dl class="bw-fields">${fields}</dl>` : '';
  const articleStyle = `width:${width}mm;height:${height}mm;--storage-height:${bandHeight}mm;${layoutStyle(payload, compact)}`;
  if (compact) {
    return [
      `<article class="bw-label bw-label--compact${storage ? ' bw-has-storage' : ''}" style="${articleStyle}">`,
      heading,
      qr,
      fieldList,
      footer,
      storage,
      '</article>',
    ].join('');
  }
  return [
    `<article class="bw-label${storage ? ' bw-has-storage' : ''}" style="${articleStyle}">`,
    qr,
    '<div class="bw-main">',
    heading,
    fieldList,
    '</div>',
    footer,
    storage,
    '</article>',
  ].join('');
}

function layoutStyle(payload: LabelPayload, compact: boolean): string {
  const layout = payload.layout;
  if (!layout) return '';
  const values: Array<[string, number | null | undefined, number, number]> = [
    ['--bw-qr-width', layout.qrWidthMm, 5, 100],
    ['--bw-code-font', layout.codeFontPt, 4, 36],
    ['--bw-field-font', layout.fieldFontPt, 4, 36],
    ['--bw-emphasis-font', layout.emphasisFontPt, 4, 36],
    ['--bw-title-font', layout.titleFontPt, 4, 36],
    ['--bw-footer-font', layout.footerFontPt, 4, 36],
  ];
  const result = values.flatMap(([name, value, min, max]) =>
    typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max
      ? [`${name}:${value}${name === '--bw-qr-width' ? 'mm' : 'pt'};`]
      : [],
  );
  if (!compact && !result.some((item) => item.startsWith('--bw-qr-width:'))) {
    result.push('--bw-qr-width:18mm;');
  }
  return result.join('');
}

/** 只含标签的打印文档，按纸张毫米尺寸分页，份数各占一页。 */
export function buildLabelPrintDocument(payload: LabelPayload): string {
  const width = positiveMm(payload.printerSetting.widthMm, 40);
  const height = positiveMm(payload.printerSetting.heightMm, 30);
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

function compactFieldName(name: string, compact: boolean): string {
  if (!compact) return name;
  switch (name) {
    case '验证码': return '验码';
    case '加工要求': return '要求';
    case '期限': return '交期';
    default: return name;
  }
}

function positiveMm(value: number, fallback: number): number {
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

function clampCopies(value: number): number {
  if (!Number.isFinite(value) || value < 1) return 1;
  return Math.min(Math.floor(value), 20);
}
