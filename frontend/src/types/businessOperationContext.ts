/** 正式单据与库存维度的操作上下文，展示名称不能作为客户或物料标识。 */
export interface BusinessOperationContext {
  customerId: number | null;
  ownerCustomerId: number | null;
  motherOrderCode: string;
  workOrderCode: string;
  materialCode: string;
  warehouseCode: string;
  ownerType: string;
  stockStatus: string;
  quantity: number;
  weight: number;
}
