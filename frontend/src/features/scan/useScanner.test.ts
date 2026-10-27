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
  nudgeCameraFocus: vi.fn(),
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

  it('拒绝相机权限后可再次扫码，并在识别完成后关闭新媒体流', async () => {
    const denied = Object.assign(new Error('denied'), { name: 'NotAllowedError' });
    const stop = vi.fn();
    vi.mocked(openCameraStream)
      .mockRejectedValueOnce(denied)
      .mockResolvedValueOnce({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    vi.mocked(readLiveBarcode).mockResolvedValue('JG|mother|MD-RETRY');
    const { wrapper, api } = mountScanner();

    await expect(api.scanCamera()).resolves.toBeNull();
    expect(api.hint.value).toContain('重新扫码');
    expect(api.scanning.value).toBe(false);
    await expect(api.scanCamera()).resolves.toBe('JG|mother|MD-RETRY');
    expect(openCameraStream).toHaveBeenCalledTimes(2);
    expect(readLiveBarcode).toHaveBeenCalledOnce();
    expect(stop).toHaveBeenCalledOnce();
    expect(api.cameraActive.value).toBe(false);
    wrapper.unmount();
  });

  it('停止旧取景后重进，相机流独立且旧识别结果不会覆盖新结果', async () => {
    const oldStop = vi.fn();
    const newStop = vi.fn();
    vi.mocked(openCameraStream)
      .mockResolvedValueOnce({ getTracks: () => [{ stop: oldStop }] } as unknown as MediaStream)
      .mockResolvedValueOnce({ getTracks: () => [{ stop: newStop }] } as unknown as MediaStream);
    let finishOld: (value: string) => void = () => {};
    vi.mocked(readLiveBarcode)
      .mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve; }))
      .mockResolvedValueOnce('JG|mother|MD-NEW');
    const { wrapper, api } = mountScanner();

    const oldScan = api.scanCamera();
    await flushPromises();
    api.stop();
    expect(oldStop).toHaveBeenCalledOnce();
    await expect(api.scanCamera()).resolves.toBe('JG|mother|MD-NEW');
    finishOld('JG|mother|MD-OLD');
    await expect(oldScan).resolves.toBeNull();
    expect(newStop).toHaveBeenCalledOnce();
    expect(api.hint.value).toContain('已读到条码');
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
