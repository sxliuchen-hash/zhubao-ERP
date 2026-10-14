import { onBeforeUnmount, ref } from 'vue';
import { hasLiveCamera, openCameraStream, readBarcodeFromFile, readLiveBarcode } from './cameraBarcode';

const insecureHint = '当前是 HTTP，手机打不开摄像头。请改用 https:// 加服务器地址打开本页。';

/** 相机和照片只读出原始文本。查询单据由外层决定，离开页面时关掉媒体流。 */
export function useScanner() {
  const scanning = ref(false);
  const cameraActive = ref(false);
  const hint = ref('');
  const videoRef = ref<HTMLVideoElement | null>(null);
  let generation = 0;
  let current: MediaStream | null = null;

  function stopTracks(stream: MediaStream | null) {
    stream?.getTracks().forEach((track) => track.stop());
  }

  function stop() {
    generation += 1;
    stopTracks(current);
    current = null;
    if (videoRef.value) videoRef.value.srcObject = null;
    scanning.value = false;
    cameraActive.value = false;
  }

  onBeforeUnmount(stop);

  async function scanCamera(): Promise<string | null> {
    if (!hasLiveCamera()) return null;
    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      hint.value = insecureHint;
      return null;
    }
    const token = ++generation;
    stopTracks(current);
    current = null;
    scanning.value = true;
    cameraActive.value = true;
    hint.value = '正在打开摄像头，请允许使用相机';
    try {
      const opened = await openCameraStream();
      if (token !== generation) {
        stopTracks(opened);
        return null;
      }
      current = opened;
      const video = videoRef.value;
      if (!video) {
        hint.value = '摄像头预览没有准备好，请改用拍照或手输';
        return null;
      }
      video.srcObject = opened;
      await video.play();
      if (token !== generation) return null;
      hint.value = '对准二维码或条码，保持清晰，识别后自动继续';
      const raw = await readLiveBarcode(video, 20000, () => token !== generation);
      if (token !== generation) return null;
      if (!raw) {
        hint.value = '暂未读到条码。请靠近并避开反光，重新扫码；也可以拍照、选图或手输';
        return null;
      }
      hint.value = '已读到条码，正在识别单号';
      return raw;
    } catch (error) {
      if (token !== generation) return null;
      const name = error instanceof Error ? error.name : '';
      hint.value = name === 'NotAllowedError'
        ? '相机权限未开启，请在手机或浏览器设置中允许相机，然后重新扫码；也可拍照、选图或手输'
        : name === 'NotFoundError'
          ? '没有找到可用摄像头，请改用选图或手输'
          : name === 'NotReadableError'
            ? '摄像头可能正被其他应用占用，请关闭后重试，或改用拍照、选图、手输'
            : '无法打开摄像头，请检查相机权限，或改用拍照、选图、手输';
      return null;
    } finally {
      if (token === generation) {
        stopTracks(current);
        current = null;
        if (videoRef.value) videoRef.value.srcObject = null;
        scanning.value = false;
        cameraActive.value = false;
      }
    }
  }

  async function scanPhoto(file: Blob): Promise<string | null> {
    stop();
    const token = generation;
    hint.value = '正在识别照片中的二维码或条码';
    scanning.value = true;
    try {
      const raw = await readBarcodeFromFile(file);
      if (token !== generation) return null;
      if (!raw) {
        hint.value = '照片里没有读到条码，请重拍或手输';
        return null;
      }
      hint.value = '已读到条码，正在识别单号';
      return raw;
    } catch {
      if (token !== generation) return null;
      hint.value = '没能识别这张照片，请重拍或手输';
      return null;
    } finally {
      if (token === generation) scanning.value = false;
    }
  }

  return { scanning, cameraActive, hint, videoRef, scanCamera, scanPhoto, stop };
}
