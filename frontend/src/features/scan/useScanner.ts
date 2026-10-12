import { onBeforeUnmount, ref } from 'vue';
import { hasLiveCamera, openCameraStream, readBarcodeFromFile, readLiveBarcode } from './cameraBarcode';

const insecureHint = '当前是 HTTP，手机打不开摄像头。请改用 https:// 加服务器地址打开本页。';

/** 相机和照片只读出原始文本。查询单据由外层决定，离开页面时关掉媒体流。 */
export function useScanner() {
  const scanning = ref(false);
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
    hint.value = '';
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
      const raw = await readLiveBarcode(video, 20000, () => token !== generation);
      if (token !== generation) return null;
      if (!raw) {
        hint.value = '没有读到条码，请改拍照或手输';
        return null;
      }
      return raw;
    } catch {
      if (token !== generation) return null;
      hint.value = '无法打开摄像头，请改拍照或手输';
      return null;
    } finally {
      if (token === generation) {
        stopTracks(current);
        current = null;
        if (videoRef.value) videoRef.value.srcObject = null;
        scanning.value = false;
      }
    }
  }

  async function scanPhoto(file: Blob): Promise<string | null> {
    stop();
    const token = generation;
    hint.value = '';
    scanning.value = true;
    try {
      const raw = await readBarcodeFromFile(file);
      if (token !== generation) return null;
      if (!raw) {
        hint.value = '照片里没有读到条码，请重拍或手输';
        return null;
      }
      return raw;
    } catch {
      if (token !== generation) return null;
      hint.value = '没能识别这张照片，请重拍或手输';
      return null;
    } finally {
      if (token === generation) scanning.value = false;
    }
  }

  return { scanning, hint, videoRef, scanCamera, scanPhoto, stop };
}
