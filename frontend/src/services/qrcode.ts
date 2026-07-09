import { Ecc, QrCode } from '../vendor/qrcodegen';

// 将文本编码为二维码，并返回内联 SVG 字符串。
// 采用内置的 Nayuki QR 生成库（离线可用，不依赖外部 CDN）。
export function renderQrSvg(text: string, options?: { border?: number; scale?: number }): string {
  const border = options?.border ?? 2;
  const qr = QrCode.encodeText(text, Ecc.MEDIUM);
  const dimension = qr.size + border * 2;

  let path = '';
  for (let y = 0; y < qr.size; y++) {
    for (let x = 0; x < qr.size; x++) {
      if (qr.getModule(x, y)) {
        path += `${path === '' ? '' : ' '}M${x + border},${y + border}h1v1h-1z`;
      }
    }
  }

  return [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${dimension} ${dimension}" `,
    'stroke="none" shape-rendering="crispEdges" role="img" aria-label="二维码">',
    `<rect width="100%" height="100%" fill="#ffffff"/>`,
    `<path d="${path}" fill="#000000"/>`,
    '</svg>',
  ].join('');
}
