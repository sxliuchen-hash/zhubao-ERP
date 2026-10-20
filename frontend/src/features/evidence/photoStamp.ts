/** 把拍摄时间印在照片上，方便之后直接看图核对。视频不处理。 */

export function formatPhotoStamp(at: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())} ${pad(at.getHours())}:${pad(at.getMinutes())}:${pad(at.getSeconds())}`;
}

export async function stampPhotoTimestamp(file: File, at = new Date()): Promise<File> {
  if (!file.type.toLowerCase().startsWith('image/')) return file;
  if (typeof document === 'undefined' || typeof createImageBitmap !== 'function') return file;
  let bitmap: ImageBitmap | null = null;
  try {
    try {
      bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      bitmap = await createImageBitmap(file);
    }
    const canvas = document.createElement('canvas');
    canvas.width = bitmap.width;
    canvas.height = bitmap.height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.drawImage(bitmap, 0, 0);
    const text = formatPhotoStamp(at);
    const fontSize = Math.max(18, Math.round(Math.min(bitmap.width, bitmap.height) / 24));
    const pad = Math.round(fontSize * 0.45);
    ctx.font = `600 ${fontSize}px "Microsoft YaHei", sans-serif`;
    ctx.textBaseline = 'bottom';
    const textWidth = ctx.measureText(text).width;
    const boxWidth = textWidth + pad * 2;
    const boxHeight = fontSize + pad * 2;
    const x = pad;
    const y = bitmap.height - pad - boxHeight;
    ctx.fillStyle = 'rgba(0, 0, 0, 0.55)';
    ctx.fillRect(x, y, boxWidth, boxHeight);
    ctx.fillStyle = '#ffffff';
    ctx.fillText(text, x + pad, y + boxHeight - pad);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92));
    if (!blob) return file;
    const base = file.name.replace(/\.[^.]+$/, '') || 'photo';
    return new File([blob], `${base}.jpg`, { type: 'image/jpeg', lastModified: at.getTime() });
  } catch {
    return file;
  } finally {
    bitmap?.close();
  }
}
