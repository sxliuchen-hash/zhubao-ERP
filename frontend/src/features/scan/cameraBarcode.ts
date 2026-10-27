import {
  BarcodeFormat,
  BinaryBitmap,
  DecodeHintType,
  HybridBinarizer,
  MultiFormatOneDReader,
  QRCodeReader,
  RGBLuminanceSource,
  type Reader,
} from '@zxing/library';

export interface Raster {
  data: Uint8ClampedArray;
  width: number;
  height: number;
}

interface NativeDetector {
  detect: (source: CanvasImageSource) => Promise<Array<{ rawValue?: string }>>;
}

type NativeDetectorFactory = new (options?: { formats?: string[] }) => NativeDetector;

let nativeDetector: NativeDetector | null | undefined;
let nativeBroken = false;
let qrReader: QRCodeReader | null = null;

const liveFormats = ['qr_code', 'code_128', 'code_39', 'itf', 'ean_13', 'pdf417', 'data_matrix'];

interface CameraCapabilities {
  focusMode?: string[];
  exposureMode?: string[];
  whiteBalanceMode?: string[];
  pointsOfInterest?: boolean;
}

export function rearCameraConstraints(): MediaStreamConstraints {
  return {
    audio: false,
    video: {
      facingMode: { ideal: 'environment' },
      width: { ideal: 1280 },
      height: { ideal: 720 },
    },
  };
}

function readCapabilities(track: MediaStreamTrack): CameraCapabilities {
  const read = track.getCapabilities as undefined | (() => CameraCapabilities);
  if (!read) return {};
  try {
    return read.call(track) ?? {};
  } catch {
    return {};
  }
}

function clampUnit(value: number) {
  if (!Number.isFinite(value)) return 0.5;
  return Math.min(1, Math.max(0, value));
}

export async function enableContinuousFocus(stream: MediaStream) {
  const track = stream.getVideoTracks?.()?.[0];
  if (!track?.applyConstraints) return;
  const caps = readCapabilities(track);
  const advanced: Record<string, unknown> = {};
  if (caps.focusMode?.includes('continuous')) advanced.focusMode = 'continuous';
  if (caps.exposureMode?.includes('continuous')) advanced.exposureMode = 'continuous';
  if (caps.whiteBalanceMode?.includes('continuous')) advanced.whiteBalanceMode = 'continuous';
  if (caps.pointsOfInterest) advanced.pointsOfInterest = [{ x: 0.5, y: 0.5 }];
  if (!Object.keys(advanced).length) return;
  try {
    await track.applyConstraints({ advanced: [advanced] } as MediaTrackConstraints);
  } catch {
    // 浏览器不接受连续对焦时继续使用默认画面
  }
}

export async function nudgeCameraFocus(track: MediaStreamTrack, x = 0.5, y = 0.5) {
  if (!track.applyConstraints) return;
  const caps = readCapabilities(track);
  const point = { x: clampUnit(x), y: clampUnit(y) };
  const continuous = caps.focusMode?.includes('continuous') ?? false;
  try {
    if (caps.focusMode?.includes('single-shot')) {
      const shot: Record<string, unknown> = { focusMode: 'single-shot' };
      if (caps.pointsOfInterest) shot.pointsOfInterest = [point];
      await track.applyConstraints({ advanced: [shot] } as MediaTrackConstraints);
    }
    if (continuous) {
      const next: Record<string, unknown> = { focusMode: 'continuous' };
      if (caps.pointsOfInterest) next.pointsOfInterest = [point];
      await track.applyConstraints({ advanced: [next] } as MediaTrackConstraints);
    }
  } catch {
    // 轻点重新对焦失败时保持当前画面
  }
}

export function sampleScanRegion(width: number, height: number, maxEdge = 640) {
  const crop = 0.82;
  const sw = Math.max(1, Math.round(width * crop));
  const sh = Math.max(1, Math.round(height * crop));
  const longest = Math.max(sw, sh);
  const scale = Math.min(1, maxEdge / longest);
  return {
    sx: Math.max(0, Math.round((width - sw) / 2)),
    sy: Math.max(0, Math.round((height - sh) / 2)),
    sw,
    sh,
    width: Math.max(1, Math.round(sw * scale)),
    height: Math.max(1, Math.round(sh * scale)),
  };
}

export function resetBarcodeReaderForTests() {
  nativeDetector = undefined;
  nativeBroken = false;
  qrReader = null;
}

export function hasLiveCamera() {
  return typeof navigator.mediaDevices?.getUserMedia === 'function';
}

export async function openCameraStream() {
  const media = navigator.mediaDevices;
  let stream: MediaStream;
  try {
    stream = await media.getUserMedia(rearCameraConstraints());
  } catch (error) {
    const name = error instanceof Error ? error.name : '';
    if (name !== 'OverconstrainedError' && name !== 'NotFoundError') {
      throw error;
    }
    stream = await media.getUserMedia({ audio: false, video: true });
  }
  await enableContinuousFocus(stream);
  return stream;
}

export async function readLiveBarcode(
  video: HTMLVideoElement,
  timeoutMs = 20000,
  shouldStop: () => boolean = () => false,
) {
  const started = Date.now();
  const native = takeNativeDetector();
  // 没有系统条码识别时，先让相机对焦，再做软件解码，避免一上来卡住预览。
  if (!native) await delay(150);
  if (shouldStop()) return null;
  while (Date.now() - started < timeoutMs && !shouldStop()) {
    const value = await detectFrame(video);
    if (value) {
      return value;
    }
    await delay(native && !nativeBroken ? 60 : 30);
  }
  return null;
}

export async function readBarcodeFromFile(file: Blob) {
  const raster = await rasterFromBlob(file);
  if (!raster) {
    return null;
  }
  return decodeRaster(raster, true);
}

export function decodeRaster(raster: Raster, tryHarder = false) {
  const qr = qrReader ?? (qrReader = new QRCodeReader());
  const qrText = readQuiet(qr, bitmapFor(raster));
  if (qrText) {
    return qrText;
  }
  const hints = new Map<DecodeHintType, unknown>();
  hints.set(DecodeHintType.TRY_HARDER, tryHarder);
  hints.set(DecodeHintType.POSSIBLE_FORMATS, [
    BarcodeFormat.CODE_128,
    BarcodeFormat.CODE_39,
    BarcodeFormat.ITF,
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.CODABAR,
  ]);
  return readQuiet(new MultiFormatOneDReader(hints), bitmapFor(raster));
}

function readQuiet(reader: Reader, bitmap: BinaryBitmap) {
  try {
    return reader.decode(bitmap)?.getText() ?? null;
  } catch {
    return null;
  } finally {
    reader.reset();
  }
}

function bitmapFor(raster: Raster) {
  const luminance = rgbaToLuminance(raster.data, raster.width, raster.height);
  return new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(luminance, raster.width, raster.height)));
}

async function detectFrame(video: HTMLVideoElement) {
  const native = takeNativeDetector();
  if (native && !nativeBroken) {
    try {
      const codes = await native.detect(video);
      return codes.find((code) => code.rawValue)?.rawValue ?? null;
    } catch {
      nativeBroken = true;
    }
  }
  const raster = rasterFromVideo(video);
  if (!raster) {
    return null;
  }
  return decodeRaster(raster);
}

function takeNativeDetector() {
  if (nativeDetector !== undefined) {
    return nativeDetector;
  }
  const factory = (globalThis as { BarcodeDetector?: NativeDetectorFactory }).BarcodeDetector;
  if (!factory) {
    nativeDetector = null;
    return null;
  }
  try {
    nativeDetector = new factory({ formats: liveFormats });
    return nativeDetector;
  } catch {
    nativeDetector = null;
    return null;
  }
}

function rasterFromVideo(video: HTMLVideoElement): Raster | null {
  if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || video.videoWidth === 0) {
    return null;
  }
  const region = sampleScanRegion(video.videoWidth, video.videoHeight);
  return drawToRaster(video, region.width, region.height, region);
}

function rasterFromBlob(file: Blob) {
  return new Promise<Raster | null>((resolve) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      const longest = Math.max(image.naturalWidth, image.naturalHeight);
      const scale = longest > 1600 ? 1600 / longest : 1;
      const raster = drawToRaster(
        image,
        Math.max(1, Math.round(image.naturalWidth * scale)),
        Math.max(1, Math.round(image.naturalHeight * scale)),
      );
      URL.revokeObjectURL(url);
      resolve(raster);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(null);
    };
    image.src = url;
  });
}

function drawToRaster(
  source: CanvasImageSource,
  width: number,
  height: number,
  crop?: { sx: number; sy: number; sw: number; sh: number },
): Raster | null {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) {
    return null;
  }
  if (crop) context.drawImage(source, crop.sx, crop.sy, crop.sw, crop.sh, 0, 0, width, height);
  else context.drawImage(source, 0, 0, width, height);
  const image = context.getImageData(0, 0, width, height);
  return { data: image.data, width: image.width, height: image.height };
}

function rgbaToLuminance(data: Uint8ClampedArray, width: number, height: number) {
  const luminance = new Uint8ClampedArray(width * height);
  const pixels = Math.min(luminance.length, Math.floor(data.length / 4));
  for (let index = 0; index < pixels; index += 1) {
    const offset = index * 4;
    luminance[index] = (data[offset] + data[offset + 1] * 2 + data[offset + 2]) / 4;
  }
  return luminance;
}

function delay(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
