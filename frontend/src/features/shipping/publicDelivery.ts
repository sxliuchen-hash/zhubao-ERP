export interface PublicDeliveryViewModel {
  status: '公开' | '已停用' | '无法识别';
  bagCodes: string[];
  photoIds: string[];
  internalHref: string | null;
  financeHref: string | null;
}

/** 厂内 JG 码继续按原码处理。公开交付只认 64 位访问标识。 */
export function parseDeliveryScan(raw: string): { kind: 'factory'; raw: string } | { kind: 'public'; accessId: string } | { kind: 'unknown' } {
  const text = raw.trim();
  if (text.startsWith('JG|')) return { kind: 'factory', raw: text };
  const embedded = text.match(/\/d\/([0-9a-f]{64})(?=$|[?#/])/i);
  if (embedded) return { kind: 'public', accessId: embedded[1].toLowerCase() };
  if (/^[0-9a-f]{64}$/i.test(text)) return { kind: 'public', accessId: text.toLowerCase() };
  return { kind: 'unknown' };
}

/** 设备类型和网络位置不授予内部权限。内部链接不携带会话。 */
export function presentPublicDelivery(input: {
  disabled: boolean;
  bagCodes: string[];
  photoIds: string[];
  deliveryId: string;
  loggedIn: boolean;
  roleAllowsFinance: boolean;
  device: string;
  network: string;
}): PublicDeliveryViewModel {
  if (input.disabled) {
    return { status: '已停用', bagCodes: [], photoIds: [], internalHref: null, financeHref: null };
  }
  const internal = input.loggedIn ? `/orders?delivery=${encodeURIComponent(input.deliveryId)}` : null;
  const finance = input.loggedIn && input.roleAllowsFinance
    ? `/finance?delivery=${encodeURIComponent(input.deliveryId)}` : null;
  return {
    status: '公开',
    bagCodes: input.bagCodes,
    photoIds: input.photoIds,
    internalHref: internal,
    financeHref: finance,
  };
}
