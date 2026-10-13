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
  it('打开相机期间停止，会关闭迟到的媒体流且不启动解码', async () => {
    const stop = vi.fn();
    let opened: (value: MediaStream) => void = () => {};
    vi.mocked(openCameraStream).mockImplementation(() => new Promise(resolve => { opened = resolve; }));
    const { wrapper, api } = mountScanner();
    const pending = api.scanCamera();
    expect(api.cameraActive.value).toBe(true);
    expect(api.hint.value).toContain('正在打开摄像头');
    api.stop();
    opened({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    await expect(pending).resolves.toBeNull();
    expect(stop).toHaveBeenCalledOnce();
    expect(readLiveBarcode).not.toHaveBeenCalled();
    expect(api.cameraActive.value).toBe(false);
    expect(api.scanning.value).toBe(false);
    wrapper.unmount();
  });

  it.each([
    ['NotAllowedError', '相机权限未开启'],
    ['NotFoundError', '没有找到可用摄像头'],
    ['NotReadableError', '其他应用占用'],
  ])('相机失败 %s 时提供可执行的中文提示', async (name, expected) => {
    vi.mocked(openCameraStream).mockRejectedValue(Object.assign(new Error('camera'), { name }));
    const { wrapper, api } = mountScanner();
    await expect(api.scanCamera()).resolves.toBeNull();
    expect(api.hint.value).toContain(expected);
    expect(api.hint.value).toContain('手输');
    expect(api.cameraActive.value).toBe(false);
    expect(api.scanning.value).toBe(false);
    wrapper.unmount();
  });

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
