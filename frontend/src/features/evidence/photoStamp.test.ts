import { describe, expect, it, vi } from 'vitest';
import { formatPhotoStamp, stampPhotoTimestamp } from './photoStamp';

describe('照片时间戳', () => {
  it('格式为本地年月日时分秒', () => {
    expect(formatPhotoStamp(new Date(2026, 9, 20, 12, 13, 5))).toBe('2026-10-20 12:13:05');
  });

  it('把时间画在照片上并导出 JPEG', async () => {
    const texts: string[] = [];
    const ctx = {
      drawImage: vi.fn(),
      fillRect: vi.fn(),
      fillText: vi.fn((text: string) => texts.push(text)),
      measureText: () => ({ width: 180 }),
      font: '',
      fillStyle: '',
      textBaseline: '',
    };
    vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ width: 800, height: 600, close() {} })));
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) => {
      callback?.(new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' }));
    });

    const stamped = await stampPhotoTimestamp(
      new File([new Uint8Array([9])], '拆包.png', { type: 'image/png' }),
      new Date(2026, 9, 20, 12, 13, 5),
    );

    expect(stamped.type).toBe('image/jpeg');
    expect(stamped.name).toBe('拆包.jpg');
    expect(texts).toEqual(['2026-10-20 12:13:05']);
    expect(ctx.fillRect).toHaveBeenCalled();
    expect(ctx.drawImage).toHaveBeenCalled();
  });

  it('视频和无法解码的照片保持原文件', async () => {
    const video = new File([new Uint8Array([1])], '现场.mp4', { type: 'video/mp4' });
    expect(await stampPhotoTimestamp(video)).toBe(video);

    vi.stubGlobal('createImageBitmap', vi.fn(async () => { throw new Error('无法解码'); }));
    const photo = new File([new Uint8Array([2])], '坏.jpg', { type: 'image/jpeg' });
    expect(await stampPhotoTimestamp(photo)).toBe(photo);
  });
});
