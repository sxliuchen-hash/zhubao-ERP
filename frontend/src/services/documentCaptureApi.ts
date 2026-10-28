import { apiGet} from './apiClient';

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

export function fetchCaptures(businessCode: string): Promise<DocumentCapture[]> {
  return apiGet<DocumentCapture[]>(
    `/api/documents/captures?businessCode=${encodeURIComponent(businessCode)}`,
  );
}
