import type { LabelPayload } from '../../services/labelApi';
import type { LabelPrintImage } from '../../services/printRoutingTypes';
import { buildLabelMarkup, labelFaceCss } from '../m1/labelPrint';

/** 用现有网页版式生成最终画面，预览与工位共用其 PNG 字节。 */
export async function renderLabelPreviewImage(payload: LabelPayload): Promise<LabelPrintImage> {
  await document.fonts?.ready;
  const { widthMm, heightMm, dpi } = payload.printerSetting;
  const width = Math.round(widthMm * dpi / 25.4);
  const height = Math.round(heightMm * dpi / 25.4);
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('width', String(width));
  svg.setAttribute('height', String(height));
  svg.setAttribute('viewBox', `0 0 ${widthMm * 96 / 25.4} ${heightMm * 96 / 25.4}`);
  const content = document.createElementNS(svg.namespaceURI, 'foreignObject');
  content.setAttribute('width', '100%');
  content.setAttribute('height', '100%');
  const root = document.createElement('div');
  const style = document.createElement('style');
  style.textContent = labelFaceCss;
  root.append(style);
  root.insertAdjacentHTML('beforeend', buildLabelMarkup(payload));
  content.append(root);
  svg.append(content);
  const source = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(new XMLSerializer().serializeToString(svg))}`;
  const image = new Image();
  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () => reject(new Error('标签画面生成失败，请重新读取标签'));
    image.src = source;
  });
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('当前浏览器无法生成标签画面');
  context.fillStyle = '#fff';
  context.fillRect(0, 0, width, height);
  context.drawImage(image, 0, 0, width, height);
  return { snapshotHash: payload.snapshotHash ?? '', pngBase64: canvas.toDataURL('image/png').split(',')[1] };
}
