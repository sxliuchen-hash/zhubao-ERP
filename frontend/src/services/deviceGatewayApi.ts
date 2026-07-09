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
}

export async function ingestDeviceReading(
  request: SubmitDeviceReadingRequest,
): Promise<DeviceReading> {
  return apiPost<SubmitDeviceReadingRequest, DeviceReading>('/api/hardware/gateway/readings', request);
}

export async function fetchRecentReadings(limit = 50): Promise<DeviceReading[]> {
  return apiGet<DeviceReading[]>(`/api/hardware/gateway/readings?limit=${limit}`);
}

export async function fetchReadingsByDevice(deviceCode: string, limit = 50): Promise<DeviceReading[]> {
  return apiGet<DeviceReading[]>(
    `/api/hardware/gateway/devices/${encodeURIComponent(deviceCode)}/readings?limit=${limit}`,
  );
}

export async function fetchGatewayDevices(): Promise<GatewayDeviceView[]> {
  return apiGet<GatewayDeviceView[]>('/api/hardware/gateway/devices');
}

export async function fetchGatewaySummary(): Promise<GatewayIngestionSummary> {
  return apiGet<GatewayIngestionSummary>('/api/hardware/gateway/summary');
}
