/**
 * 2026-10-14 从 frontend/src/services/deviceGatewayApi.ts 移出。
 * 设备页只调用 fetchRecentReadings。后端 GET /api/hardware/gateway/devices/{deviceCode}/readings 仍注册。
 * 恢复时把函数接回现行 deviceGatewayApi.ts，并使用该文件已有的 apiGet 与 DeviceReading。
 */

export async function fetchReadingsByDevice(deviceCode: string, limit = 50): Promise<DeviceReading[]> {
  return apiGet<DeviceReading[]>(
    `/api/hardware/gateway/devices/${encodeURIComponent(deviceCode)}/readings?limit=${limit}`,
  );
}
