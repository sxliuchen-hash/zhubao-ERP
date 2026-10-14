import { afterEach, describe, expect, it, vi } from 'vitest';
import { Ecc, QrCode } from '../../vendor/qrcodegen';
import { decodeRaster, openCameraStream, resetBarcodeReaderForTests, type Raster } from './cameraBarcode';

afterEach(() => {
  resetBarcodeReaderForTests();
  delete (globalThis as { BarcodeDetector?: unknown }).BarcodeDetector;
  vi.unstubAllGlobals();
});

describe('decodeRaster', () => {
  it('reads a courier number from a qr image without the browser barcode api', () => {
    const text = decodeRaster(paintQr('SF1048329001'), true);

    expect(text).toBe('SF1048329001');
  });

  it('reads a system sticker qr', () => {
    const text = decodeRaster(paintQr('JG|mother|MD-20261008-001'), true);

    expect(text).toBe('JG|mother|MD-20261008-001');
  });

  it('returns null when the picture has no code', () => {
    const data = new Uint8ClampedArray(80 * 80 * 4);
    data.fill(255);

    expect(decodeRaster({ data, width: 80, height: 80 })).toBeNull();
  });
});

describe('openCameraStream', () => {
  it('相机权限被拒绝时只请求一次，并将原错误交给界面', async () => {
    const denied = Object.assign(new Error('denied'), { name: 'NotAllowedError' });
    const getUserMedia = vi.fn().mockRejectedValue(denied);
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } });

    await expect(openCameraStream()).rejects.toBe(denied);
    expect(getUserMedia).toHaveBeenCalledOnce();
    expect(getUserMedia).toHaveBeenCalledWith({ audio: false, video: { facingMode: { ideal: 'environment' } } });
  });

  it('后置摄像头约束不兼容时回退到默认摄像头', async () => {
    const stream = {} as MediaStream;
    const unavailable = Object.assign(new Error('unavailable'), { name: 'OverconstrainedError' });
    const getUserMedia = vi.fn().mockRejectedValueOnce(unavailable).mockResolvedValueOnce(stream);
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } });

    await expect(openCameraStream()).resolves.toBe(stream);
    expect(getUserMedia).toHaveBeenCalledTimes(2);
    expect(getUserMedia).toHaveBeenLastCalledWith({ audio: false, video: true });
  });

  it('相机被占用时不重复请求', async () => {
    const busy = Object.assign(new Error('busy'), { name: 'NotReadableError' });
    const getUserMedia = vi.fn().mockRejectedValue(busy);
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } });

    await expect(openCameraStream()).rejects.toBe(busy);
    expect(getUserMedia).toHaveBeenCalledOnce();
  });
});

function paintQr(text: string): Raster {
  const qr = QrCode.encodeText(text, Ecc.MEDIUM);
  const border = 4;
  const scale = 8;
  const size = (qr.size + border * 2) * scale;
  const data = new Uint8ClampedArray(size * size * 4);
  data.fill(255);
  for (let y = 0; y < qr.size; y += 1) {
    for (let x = 0; x < qr.size; x += 1) {
      if (!qr.getModule(x, y)) {
        continue;
      }
      for (let dy = 0; dy < scale; dy += 1) {
        for (let dx = 0; dx < scale; dx += 1) {
          const px = (x + border) * scale + dx;
          const py = (y + border) * scale + dy;
          const offset = (py * size + px) * 4;
          data[offset] = 0;
          data[offset + 1] = 0;
          data[offset + 2] = 0;
          data[offset + 3] = 255;
        }
      }
    }
  }
  return { data, width: size, height: size };
}
