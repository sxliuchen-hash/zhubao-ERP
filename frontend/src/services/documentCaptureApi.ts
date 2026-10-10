import { apiGet, apiPostForm } from './apiClient';

export interface DocumentCapture {
  code: string;
  businessCode: string;
  scene: string;
  fileName: string;
  mediaType: string;
  checksum: string;
  uploadedAt: string;
  owner: string;
  previewUrl: string;
}

export interface CaptureUploadResult {
  code: string;
  documentCode: string;
  fileName: string;
  mediaType: string;
  checksum: string;
  uploadedAt: string;
  previewUrl: string;
}

export function fetchCaptures(businessCode: string): Promise<DocumentCapture[]> {
  return apiGet<DocumentCapture[]>(
    `/api/documents/captures?businessCode=${encodeURIComponent(businessCode)}`,
  );
}

export function uploadCapture(input: {
  objectType: string;
  businessCode: string;
  scene: string;
  file: File;
}): Promise<CaptureUploadResult> {
  const body = new FormData();
  body.set('objectType', input.objectType);
  body.set('businessCode', input.businessCode);
  body.set('scene', input.scene);
  body.set('file', input.file);
  return apiPostForm<CaptureUploadResult>('/api/documents/captures', body);
}
