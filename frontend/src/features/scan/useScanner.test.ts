import { defineComponent } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { hasLiveCamera, openCameraStream, readBarcodeFromFile, readLiveBarcode } from './cameraBarcode';
import { useScanner } from './useScanner';

vi.mock('./cameraBarcode', () => ({
  hasLiveCamera: vi.fn(() => true),
  openCameraStream: vi.fn(),
  readLiveBarcode: vi.fn(),
  readBarcodeFromFile: vi.fn(),
}));

function mountScanner() {
  let api: ReturnType<typeof useScanner> | null = null;
  const wrapper = mount(defineComponent({
    setup() {
      api = useScanner();
      return { videoRef: api.videoRef, scanning: api.scanning };
    },
    template: '<video ref="videoRef" />',
  }));
  return { wrapper, get api() { return api!; } };
}

beforeEach(() => {
  vi.mocked(hasLiveCamera).mockReturnValue(true);
  vi.mocked(openCameraStream).mockReset();
  vi.mocked(readLiveBarcode).mockReset();
  vi.mocked(readBarcodeFromFile).mockReset();
  HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
});

describe('扫码媒体', () => {
  it('相机会话在切页或卸载时停止，迟到的识别结果不再返回', async () => {
    const stop = vi.fn();
    vi.mocked(openCameraStream).mockResolvedValue({
      getTracks: () => [{ stop }],
    } as unknown as MediaStream);
    let finish: (value: string) => void = () => {};
    vi.mocked(readLiveBarcode).mockImplementation(() => new Promise((resolve) => {
      finish = resolve;
    }));
    const { wrapper, api } = mountScanner();
    const pending = api.scanCamera();
    await flushPromises();
    wrapper.unmount();
    expect(stop).toHaveBeenCalled();
    finish('JG|work_order|LATE');
    await expect(pending).resolves.toBeNull();
  });

  it('照片识别只返回原始文本', async () => {
    vi.mocked(readBarcodeFromFile).mockResolvedValue('JG|rework|FG-9');
    const { api } = mountScanner();
    await expect(api.scanPhoto(new Blob(['photo']))).resolves.toBe('JG|rework|FG-9');
    expect(readBarcodeFromFile).toHaveBeenCalledTimes(1);
  });

  it('非安全页面不打开摄像头', async () => {
    const secure = window.isSecureContext;
    Object.defineProperty(window, 'isSecureContext', { configurable: true, value: false });
    const { api } = mountScanner();
    await expect(api.scanCamera()).resolves.toBeNull();
    expect(openCameraStream).not.toHaveBeenCalled();
    expect(api.hint.value).toContain('请改用 https://');
    Object.defineProperty(window, 'isSecureContext', { configurable: true, value: secure });
  });
});
