import { describe, expect, it } from 'vitest';
import { renderQrSvg } from './qrcode';

describe('renderQrSvg', () => {
  it('将文本编码为内联 SVG 二维码', () => {
    const svg = renderQrSvg('JG|mother|MO20260709001');

    expect(svg.startsWith('<svg')).toBe(true);
    expect(svg).toContain('viewBox');
    expect(svg).toContain('<path');
    expect(svg).toContain('</svg>');
  });

  it('不同内容生成不同二维码路径', () => {
    const a = renderQrSvg('JG|mother|A');
    const b = renderQrSvg('JG|outbound|B');

    expect(a).not.toEqual(b);
  });
});
