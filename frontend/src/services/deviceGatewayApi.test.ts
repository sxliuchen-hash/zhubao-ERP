import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  fetchGatewayDevices,
  fetchGatewaySummary,
  fetchRecentReadings,
  ingestDeviceReading,
} from './deviceGatewayApi';

describe('deviceGatewayApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('ingests a device reading through the gateway endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'ok',
        data: {
          readingCode: 'DR-SCALE-01-20260709100000000',
          deviceCode: 'SCALE-01',
          readingType: 'SCALE_WEIGHT',
          rawPayload: 'ST,GS,+000123.45,g',
          normalizedValue: '123.45',
          normalizedUnit: 'g',
          normalizedCode: '',
          status: 'READY',
          message: '称重读数已稳定',
          businessRefType: '',
          businessRefCode: '',
          capturedAt: '2026-07-09T10:00:00',
          receivedAt: '2026-07-09T10:00:00',
        },
      }),
    } as Response);

    const reading = await ingestDeviceReading({
      deviceCode: 'SCALE-01',
      deviceKey: 'zhubao-device-demo',
      readingType: 'SCALE_WEIGHT',
      rawPayload: 'ST,GS,+000123.45,g',
    });

    expect(reading.status).toBe('READY');
    expect(reading.normalizedValue).toBe('123.45');
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/hardware/gateway/readings');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toMatchObject({
      deviceCode: 'SCALE-01',
      readingType: 'SCALE_WEIGHT',
    });
  });

  it('fetches devices, recent readings and summary', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [
            {
              deviceCode: 'SCALE-01',
              deviceName: '称重台电子秤桥接',
              acceptedTypes: ['SCALE_WEIGHT', 'HEARTBEAT'],
              enabled: true,
              location: '打包称重台',
              fallbackNote: '人工读数并拍照留证',
              recentReadings: 3,
              lastReadingAt: '2026-07-09T10:00:00',
            },
          ],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 0, message: 'ok', data: [] }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            totalDevices: 4,
            enabledDevices: 4,
            recentReadings: 3,
            readyReadings: 1,
            warningReadings: 1,
            blockedReadings: 1,
          },
        }),
      } as Response);

    const devices = await fetchGatewayDevices();
    const readings = await fetchRecentReadings(10);
    const summary = await fetchGatewaySummary();

    expect(devices[0].deviceCode).toBe('SCALE-01');
    expect(readings).toEqual([]);
    expect(summary.totalDevices).toBe(4);
    expect(fetchMock.mock.calls[0][0]).toContain('/api/hardware/gateway/devices');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/hardware/gateway/readings?limit=10');
    expect(fetchMock.mock.calls[2][0]).toContain('/api/hardware/gateway/summary');
  });
});
