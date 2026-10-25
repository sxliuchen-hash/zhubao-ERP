export type EvidenceFileState = 'LOCAL_PENDING' | 'UPLOADING' | 'FAILED' | 'UPLOADED_PENDING_LINK' | 'LINKED';
export interface EvidenceDraftFile {
  id: number; draftKey: string; file: Blob; name: string; lastModified: number; capturedAt: number;
  processing: boolean; state: EvidenceFileState; message: string; attachmentCode: string;
  snap: { targetType: string; targetCode: string; stage: string; scene: string; operationType: string; operationCode: string; processName: string; clauseNo: string };
}
