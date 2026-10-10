import type {
  AfterSalesReviewRequest, CustomerReturnRequest, LossApprovalRequest, OutboundShipmentRequest,
  ProductionReportRequest, QualityFreezeRequest, QualityReleaseRequest, ReworkDispatchRequest, ReworkReportRequest,
} from '../../services/businessOperationApi';

type OperationRequest = AfterSalesReviewRequest & CustomerReturnRequest & LossApprovalRequest & OutboundShipmentRequest &
  ProductionReportRequest & QualityFreezeRequest & QualityReleaseRequest & ReworkDispatchRequest & ReworkReportRequest;

/** 空白输入不覆盖服务端单据值；数量由表单显式转换，金额与重量保留十进制文本。 */
export function operationDraft(values: Record<string, string>): Partial<OperationRequest> {
  const result: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(values)) {
    if (!value.trim()) continue;
    result[key] = ['quantity', 'acceptedQuantity'].includes(key) ? Number(value) : value.trim();
  }
  return result as Partial<OperationRequest>;
}
