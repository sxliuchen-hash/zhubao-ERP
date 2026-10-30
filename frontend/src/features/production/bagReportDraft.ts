/** 整袋报工草稿。按袋分开保存，失败后保留原请求和未上传照片。 */
export interface BagReportDraft {
  bagId: string;
  requestId: string;
  quantity: string;
  photoName: string;
  loss: string;
  pendingResult: boolean;
}

const drafts = new Map<string, BagReportDraft>();

function requestId(): string {
  return `bag-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export function loadBagDraft(bagId: string): BagReportDraft {
  const existing = drafts.get(bagId);
  if (existing) return { ...existing };
  const created: BagReportDraft = {
    bagId, requestId: requestId(), quantity: '', photoName: '', loss: '', pendingResult: false,
  };
  drafts.set(bagId, created);
  return { ...created };
}

export function saveBagDraft(draft: BagReportDraft): void {
  drafts.set(draft.bagId, { ...draft });
}

export function clearBagDraft(bagId: string): void {
  drafts.delete(bagId);
}
