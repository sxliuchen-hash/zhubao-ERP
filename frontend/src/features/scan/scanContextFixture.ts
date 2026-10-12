import type {
  ScanBalanceCandidate,
  ScanContextAction,
  ScanContextResponse,
  ScanObjectType,
  ScanRelatedTarget,
} from '../../services/scanContextApi';

/** 测试里代替服务端组一份上下文。页面代码不能调用它来猜类型。 */
export function buildScanContext(input: {
  objectType?: ScanObjectType | null;
  code?: string | null;
  motherOrderCode?: string | null;
  workOrderCode?: string | null;
  reworkOrderCode?: string | null;
  outboundOrderCode?: string | null;
  customerName?: string | null;
  goodsName?: string | null;
  requirement?: string | null;
  dueDate?: string | null;
  quantity?: number | null;
  weight?: number | null;
  requiresSelection?: boolean;
  relatedTargets?: ScanRelatedTarget[];
  actions?: ScanContextAction[];
  balanceSelectionRequired?: boolean;
  balanceCandidates?: ScanBalanceCandidate[];
}): ScanContextResponse {
  const objectType = input.objectType ?? null;
  const code = input.code ?? null;
  return {
    target: {
      objectType,
      code,
      motherOrderCode: input.motherOrderCode ?? (objectType === 'MOTHER_ORDER' ? code : null),
      workOrderCode: input.workOrderCode ?? (objectType === 'WORK_ORDER' ? code : null),
      reworkOrderCode: input.reworkOrderCode ?? (objectType === 'REWORK' ? code : null),
      outboundOrderCode: input.outboundOrderCode ?? (objectType === 'OUTBOUND_ORDER' ? code : null),
      reference: objectType && code ? `${objectType}:${code}` : null,
    },
    order: {
      customerId: null,
      customerName: input.customerName ?? null,
      goodsName: input.goodsName ?? null,
      materialCode: null,
      quantity: input.quantity ?? null,
      quantityUnit: input.quantity == null ? null : '件',
      weight: input.weight ?? null,
      weightUnit: input.weight == null ? null : '克',
      requirement: input.requirement ?? null,
      dueDate: input.dueDate ?? null,
    },
    requirements: {
      motherRequirement: null,
      motherSource: null,
      workOrderRequirement: null,
      workOrderSource: null,
      stationRequirements: [],
    },
    progress: {
      handoffs: [],
      quality: null,
      reinspection: { available: false, reason: '复检轮次尚未实施' },
      stock: [],
      shipments: [],
      progressRecords: [],
    },
    relatedTargets: input.relatedTargets ?? [],
    requiresSelection: input.requiresSelection ?? false,
    actions: input.actions ?? [],
    operationContext: {
      formalOperations: [],
      balanceCandidates: input.balanceCandidates ?? [],
      balanceSelectionRequired: input.balanceSelectionRequired ?? false,
      selectedBalance: null,
      factSnapshot: null,
    },
  };
}

const stickerTypes: Record<string, ScanObjectType> = {
  mother: 'MOTHER_ORDER',
  MO: 'MOTHER_ORDER',
  work_order: 'WORK_ORDER',
  WO: 'WORK_ORDER',
  outbound: 'OUTBOUND_ORDER',
  OUT: 'OUTBOUND_ORDER',
  rework: 'REWORK',
  EXP: 'EXPRESS_NO',
};

/** 旧测试用的服务端替身：只在测试里按贴纸格式回规范身份。 */
export function contextForQuery(request: { content: string; objectType?: ScanObjectType }): ScanContextResponse {
  const content = request.content.trim();
  const parts = content.split('|');
  if (content.startsWith('JG|') && parts.length === 3 && stickerTypes[parts[1]]) {
    return buildScanContext({ objectType: stickerTypes[parts[1]], code: parts[2] });
  }
  if (request.objectType) return buildScanContext({ objectType: request.objectType, code: content });
  return buildScanContext({ objectType: 'WORK_ORDER', code: content });
}
