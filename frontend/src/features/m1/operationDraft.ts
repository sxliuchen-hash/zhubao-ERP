import type {
  AfterSalesReviewRequest, CustomerReturnRequest, LossApprovalRequest, OutboundShipmentRequest,
  ProductionReportRequest, QualityFreezeRequest, QualityReleaseRequest, ReworkDispatchRequest, ReworkReportRequest,
} from '../../services/businessOperationApi';

type OperationRequest = AfterSalesReviewRequest & CustomerReturnRequest & LossApprovalRequest & OutboundShipmentRequest &
  ProductionReportRequest & QualityFreezeRequest & QualityReleaseRequest & ReworkDispatchRequest & ReworkReportRequest;

/** 空白输入不进入请求。数量和重量留空不会在这里补成整批，由确认逻辑拒绝或提交输入框中的可见值。 */
export function operationDraft(values: Record<string, string>): Partial<OperationRequest> {
  const result: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(values)) {
    if (!value.trim()) continue;
    result[key] = ['quantity', 'acceptedQuantity'].includes(key) ? Number(value) : value.trim();
  }
  return result as Partial<OperationRequest>;
}
