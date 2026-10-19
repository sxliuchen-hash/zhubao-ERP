import { apiGet, apiPost } from './apiClient';

export interface MaintainedOrder {
  targetType: 'MOTHER' | 'WORK_ORDER' | 'PRINT_JOB' | 'PRINT_TARGET';
  code: string;
  motherOrderCode: string;
  customerName: string;
  materialCode: string;
  goodsName: string;
  requirement: string;
  dueDate: string;
  quantity: string;
  weight: string;
  processes: string;
  copies: number | null;
  stationId: string;
  queueId: string;
  recycled: boolean;
  recycleReason: string;
  adjustments: string[];
  revision: string;
  postedFactsLocked?: boolean;
}

export function searchMaintainedOrders(code: string): Promise<MaintainedOrder[]> {
  return apiGet<MaintainedOrder[]>(`/api/business/order-maintenance?code=${encodeURIComponent(code)}`);
}

export function fetchRecycleBin(): Promise<MaintainedOrder[]> {
  return apiGet<MaintainedOrder[]>('/api/business/order-maintenance/recycle-bin');
}

export function correctMaintainedOrder(order: MaintainedOrder, reason: string, requestId: string): Promise<MaintainedOrder> {
  if (order.targetType === 'MOTHER') {
    return apiPost(`/api/business/order-maintenance/mother-orders/${encodeURIComponent(order.code)}`, {
      customerName: order.customerName,
      materialCode: order.materialCode,
      goodsName: order.goodsName,
      quantity: order.quantity,
      weight: order.weight,
      requirement: order.requirement,
      dueDate: order.dueDate,
      remark: '',
      reason,
      requestId,
      expectedRevision: order.revision,
    });
  }
  if (order.targetType === 'WORK_ORDER') {
    return apiPost(`/api/business/order-maintenance/work-orders/${encodeURIComponent(order.code)}`, {
      customerName: order.customerName,
      materialCode: order.materialCode,
      goodsName: order.goodsName,
      quantity: order.quantity,
      weight: order.weight,
      requirement: order.requirement,
      dueDate: order.dueDate,
      processes: order.processes,
      reason,
      requestId,
      expectedRevision: order.revision,
    });
  }
  const path = order.targetType === 'PRINT_JOB' ? 'print-jobs' : 'print-targets';
  return apiPost(`/api/business/order-maintenance/${path}/${encodeURIComponent(order.code)}`, {
    copies: Number(order.copies),
    stationId: order.stationId,
    queueId: order.queueId,
    reason,
  });
}

export function recycleMaintainedOrder(targetType: string, code: string, reason: string): Promise<void> {
  return apiPost('/api/business/order-maintenance/recycle', { targetType, code, reason });
}

export function restoreMaintainedOrder(targetType: string, code: string): Promise<void> {
  return apiPost('/api/business/order-maintenance/restore', { targetType, code });
}
