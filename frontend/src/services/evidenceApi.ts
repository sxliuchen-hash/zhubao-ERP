import { apiGet, apiPost, apiPostForm } from './apiClient';

export interface EvidenceLink {
  linkCode: string;
  attachmentCode: string;
  targetType: string;
  targetCode: string;
  stage: string;
  scene: string;
  sceneLabel: string;
  operationType: string;
  operationCode: string;
  processName: string;
  clauseNo: string;
  operatorName: string;
  linkedAt: string;
  fileName: string;
  mediaType: string;
  checksum: string;
  status: string;
  previewUrl: string;
  fileState: string;
}

export interface HistoricalEvidence {
  attachmentCode: string;
  businessCode: string;
  objectType: string;
  fileName: string;
  mediaType: string;
  checksum: string;
  status: string;
  uploadedAt: string;
  label: string;
  sceneCode: string;
  previewUrl: string;
  fileState: string;
}

export interface EvidenceBoard {
  links: EvidenceLink[];
  historical: HistoricalEvidence[];
}

export interface StoredEvidenceFile {
  code: string;
  documentCode: string;
  fileName: string;
  mediaType: string;
  size: number;
  storageLocation: string;
  checksum: string;
  uploadedAt: string;
  previewUrl: string;
}

export interface EvidenceTarget {
  targetType: string;
  targetCode: string;
  stage?: string;
  operationType?: string;
  operationCode?: string;
}

export interface EvidencePageQuery extends EvidenceTarget {
  uploadedFrom?: string;
  uploadedTo?: string;
  mediaCategory?: 'image' | 'video' | 'pdf' | '';
  ownerKeyword?: string;
  afterId?: number;
  limit?: number;
}

export interface EvidencePage<T> {
  items: T[];
  nextCursor: number | null;
  hasMore: boolean;
}

function evidencePageParams(query: EvidencePageQuery, includeOperation: boolean): URLSearchParams {
  const params = new URLSearchParams();
  params.set('targetType', query.targetType);
  params.set('targetCode', query.targetCode);
  if (includeOperation) {
    if (query.stage) params.set('stage', query.stage);
    if (query.operationType) params.set('operationType', query.operationType);
    if (query.operationCode) params.set('operationCode', query.operationCode);
  }
  if (query.uploadedFrom) params.set('uploadedFrom', query.uploadedFrom);
  if (query.uploadedTo) params.set('uploadedTo', query.uploadedTo);
  if (query.mediaCategory) params.set('mediaCategory', query.mediaCategory);
  if (query.ownerKeyword) params.set('ownerKeyword', query.ownerKeyword);
  if (query.afterId !== undefined) params.set('afterId', String(query.afterId));
  if (query.limit !== undefined) params.set('limit', String(query.limit));
  return params;
}

export function fetchLinkedEvidencePage(query: EvidencePageQuery): Promise<EvidencePage<EvidenceLink>> {
  return apiGet<EvidencePage<EvidenceLink>>(
    `/api/documents/evidence-links/page?${evidencePageParams(query, true).toString()}`,
  );
}

export function fetchHistoricalEvidencePage(query: EvidencePageQuery): Promise<EvidencePage<HistoricalEvidence>> {
  return apiGet<EvidencePage<HistoricalEvidence>>(
    `/api/documents/evidence-links/historical/page?${evidencePageParams(query, false).toString()}`,
  );
}

export function fetchEvidenceBoard(query: EvidenceTarget): Promise<EvidenceBoard> {
  const params = new URLSearchParams();
  params.set('targetType', query.targetType);
  params.set('targetCode', query.targetCode);
  if (query.stage) params.set('stage', query.stage);
  if (query.operationType) params.set('operationType', query.operationType);
  if (query.operationCode) params.set('operationCode', query.operationCode);
  return apiGet<EvidenceBoard>(`/api/documents/evidence-links?${params.toString()}`);
}

export function uploadEvidenceFile(input: {
  objectType: string;
  businessCode: string;
  scene: string;
  file: File;
}): Promise<StoredEvidenceFile> {
  const body = new FormData();
  body.set('objectType', input.objectType);
  body.set('businessCode', input.businessCode);
  body.set('scene', input.scene);
  body.set('file', input.file);
  return apiPostForm<StoredEvidenceFile>('/api/documents/evidence-files', body);
}

export function bindEvidence(input: {
  attachmentCode: string;
  targetType: string;
  targetCode: string;
  stage: string;
  scene: string;
  operationType: string;
  operationCode: string;
  processName: string;
  clauseNo: string;
}): Promise<{ linkCode: string; attachmentCode: string; scene: string }> {
  return apiPost('/api/documents/evidence-links', input);
}
