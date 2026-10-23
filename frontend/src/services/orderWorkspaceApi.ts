import type { OrderWorkspacePage, OrderWorkspaceQuery } from './orderWorkspaceTypes';
import { apiGet } from './apiClient';

/** 只读订单列表。没有返回前，页面不能用样例行冒充查询结果。 */
export function fetchOrderWorkspace(query: OrderWorkspaceQuery = {}): Promise<OrderWorkspacePage> {
  const params = new URLSearchParams();
  if (query.view) params.set('view', query.view);
  if (query.query) params.set('query', query.query);
  if (query.customerId) params.set('customerId', query.customerId);
  if (query.process) params.set('process', query.process);
  if (query.facet) params.set('facet', query.facet);
  if (query.dueBefore) params.set('dueBefore', query.dueBefore);
  if (query.page) params.set('page', String(query.page));
  if (query.size) params.set('size', String(query.size));
  const suffix = params.toString();
  return apiGet(`/api/business/order-workspace${suffix ? `?${suffix}` : ''}`);
}
