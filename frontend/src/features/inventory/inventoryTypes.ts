export type OwnerType = 'CUSTOMER' | 'COMPANY';
export type StockStatus = 'RAW_WAITING' | 'WIP' | 'FINISHED' | 'FROZEN' | 'SHIPPED';

export interface StockDimension {
  ownerType: OwnerType;
  ownerCustomerId: number;
  customerId: number;
  motherOrderCode: string;
  workOrderCode?: string;
  materialCode: string;
  warehouseCode: string;
  stockStatus: StockStatus;
}

export interface StockTransferCommand {
  documentType: string;
  documentCode: string;
  from?: StockDimension;
  to: StockDimension;
  quantity: string;
  weight: string;
  remark?: string;
}

export function createInboundDemoCommand(): StockTransferCommand {
  return {
    documentType: 'inbound',
    documentCode: 'CC-20260706-001-JL',
    to: {
      ownerType: 'CUSTOMER',
      ownerCustomerId: 1001,
      customerId: 1001,
      motherOrderCode: 'CC-20260706-001-JL',
      materialCode: 'MAT-GOLD',
      warehouseCode: 'RAW',
      stockStatus: 'RAW_WAITING',
    },
    quantity: '10',
    weight: '35.200',
    remark: '客户来料入库',
  };
}
