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
  try {
    return await media.getUserMedia({
      audio: false,
      video: { facingMode: { ideal: 'environment' } },
    });
  } catch {
    return media.getUserMedia({ audio: false, video: true });
  }
}

export async function readLiveBarcode(
  video: HTMLVideoElement,
  timeoutMs = 20000,
  shouldStop: () => boolean = () => false,
) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs && !shouldStop()) {
    const value = await detectFrame(video);
    if (value) {
      return value;
    }
    await delay(280);
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
  const longest = Math.max(video.videoWidth, video.videoHeight);
  const scale = Math.min(1, 960 / longest);
  return drawToRaster(video, Math.max(1, Math.round(video.videoWidth * scale)), Math.max(1, Math.round(video.videoHeight * scale)));
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

function drawToRaster(source: CanvasImageSource, width: number, height: number): Raster | null {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) {
    return null;
  }
  context.drawImage(source, 0, 0, width, height);
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
