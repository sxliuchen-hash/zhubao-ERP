import { apiGet, apiPost } from './apiClient';

export type DeviceReadingType =
  | 'SCALE_WEIGHT'
  | 'SCAN_EVENT'
  | 'PRINT_ACK'
  | 'VIDEO_CLIP_MARK'
  | 'HEARTBEAT';

export type DeviceReadingStatus = 'READY' | 'WARNING' | 'BLOCKED';

export interface DeviceReading {
  readingCode: string;
  deviceCode: string;
  readingType: DeviceReadingType;
  rawPayload: string;
  normalizedValue: string;
  normalizedUnit: string;
  normalizedCode: string;
  status: DeviceReadingStatus;
  message: string;
  businessRefType: string;
  businessRefCode: string;
  capturedAt: string;
  receivedAt: string;
}

export interface GatewayDeviceView {
  deviceCode: string;
  deviceName: string;
  acceptedTypes: DeviceReadingType[];
  enabled: boolean;
  location: string;
  fallbackNote: string;
  recentReadings: number;
  lastReadingAt: string;
}

export interface GatewayIngestionSummary {
  totalDevices: number;
  enabledDevices: number;
  recentReadings: number;
  readyReadings: number;
  warningReadings: number;
  blockedReadings: number;
}

export interface SubmitDeviceReadingRequest {
  deviceCode: string;
  deviceKey: string;
  readingType: DeviceReadingType;
  rawPayload: string;
  businessRefType?: string;
  businessRefCode?: string;
  capturedAt?: string;
  nonce?: string;
}

export type ReadingConsumptionIntent =
  | 'WEIGH_HANDOVER'
  | 'SCAN_TO_REPORT'
  | 'PRINT_CONFIRM'
  | 'VIDEO_LINK'
  | 'DISMISS';

export interface ReadingConsumption {
  consumptionCode: string;
  readingCode: string;
  deviceCode: string;
  readingType: DeviceReadingType;
  intent: ReadingConsumptionIntent;
  normalizedValue: string;
  normalizedUnit: string;
  normalizedCode: string;
  businessRefType: string;
  businessRefCode: string;
  operator: string;
  role: string;
  note: string;
  ledgerCode: string;
  consumedAt: string;
}

export interface ConsumeReadingRequest {
  readingCode: string;
  intent: ReadingConsumptionIntent;
  businessRefType?: string;
  businessRefCode?: string;
  note?: string;
  postingCustomerId?: number;
  postingOwnerCustomerId?: number;
  postingMotherOrderCode?: string;
  postingWorkOrderCode?: string;
  postingMaterialCode?: string;
  postingFromWarehouseCode?: string;
  postingToWarehouseCode?: string;
  postingQuantity?: number | string;
  postingProcess?: string;
}

export async function ingestDeviceReading(
  request: SubmitDeviceReadingRequest,
): Promise<DeviceReading> {
  return apiPost<SubmitDeviceReadingRequest, DeviceReading>('/api/hardware/gateway/readings', request);
}

export async function fetchRecentReadings(limit = 50): Promise<DeviceReading[]> {
  return apiGet<DeviceReading[]>(`/api/hardware/gateway/readings?limit=${limit}`);
}

export interface RegisterGatewayDeviceRequest {
  deviceCode: string;
  deviceName: string;
  acceptedTypes: DeviceReadingType[];
  deviceKey?: string;
  enabled?: boolean;
  location?: string;
  fallbackNote?: string;
}

export async function fetchGatewayDevices(): Promise<GatewayDeviceView[]> {
  return apiGet<GatewayDeviceView[]>('/api/hardware/gateway/devices');
}

export async function registerGatewayDevice(
  request: RegisterGatewayDeviceRequest,
): Promise<GatewayDeviceView> {
  return apiPost<RegisterGatewayDeviceRequest, GatewayDeviceView>('/api/hardware/gateway/devices', request);
}

export async function setGatewayDeviceEnabled(
  deviceCode: string,
  enabled: boolean,
): Promise<GatewayDeviceView> {
  return apiPost<{ enabled: boolean }, GatewayDeviceView>(
    `/api/hardware/gateway/devices/${encodeURIComponent(deviceCode)}/status`,
    { enabled },
  );
}

export async function rotateGatewayDeviceKey(
  deviceCode: string,
  deviceKey: string,
): Promise<GatewayDeviceView> {
  return apiPost<{ deviceKey: string }, GatewayDeviceView>(
    `/api/hardware/gateway/devices/${encodeURIComponent(deviceCode)}/rotate-key`,
    { deviceKey },
  );
}

export async function fetchGatewaySummary(): Promise<GatewayIngestionSummary> {
  return apiGet<GatewayIngestionSummary>('/api/hardware/gateway/summary');
}

export async function consumeReading(request: ConsumeReadingRequest): Promise<ReadingConsumption> {
  return apiPost<ConsumeReadingRequest, ReadingConsumption>('/api/hardware/gateway/consumptions', request);
}

export async function fetchRecentConsumptions(limit = 50): Promise<ReadingConsumption[]> {
  return apiGet<ReadingConsumption[]>(`/api/hardware/gateway/consumptions?limit=${limit}`);
}
