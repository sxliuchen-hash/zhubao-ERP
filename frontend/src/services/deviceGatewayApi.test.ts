import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  consumeReading,
  fetchGatewayDevices,
  fetchGatewaySummary,
  fetchRecentConsumptions,
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

  it('consumes a reading and lists recent consumptions', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            consumptionCode: 'RC-DR-SCALE-01-1',
            readingCode: 'DR-SCALE-01-1',
            deviceCode: 'SCALE-01',
            readingType: 'SCALE_WEIGHT',
            intent: 'WEIGH_HANDOVER',
            normalizedValue: '123.45',
            normalizedUnit: 'g',
            normalizedCode: '',
            businessRefType: '子工单',
            businessRefCode: 'ZD-20260706-001',
            operator: '周凯',
            role: 'OPERATOR',
            note: '打包称重',
            consumedAt: '2026-07-09T10:05:00',
          },
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ code: 0, message: 'ok', data: [] }),
      } as Response);

    const consumption = await consumeReading({
      readingCode: 'DR-SCALE-01-1',
      intent: 'WEIGH_HANDOVER',
      businessRefType: '子工单',
      businessRefCode: 'ZD-20260706-001',
      note: '打包称重',
    });
    const recent = await fetchRecentConsumptions(10);

    expect(consumption.intent).toBe('WEIGH_HANDOVER');
    expect(consumption.operator).toBe('周凯');
    expect(recent).toEqual([]);
    const [path, options] = fetchMock.mock.calls[0];
    expect(path).toContain('/api/hardware/gateway/consumptions');
    expect(options?.method).toBe('POST');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/hardware/gateway/consumptions?limit=10');
  });

  it('registers, toggles and rotates gateway devices', async () => {
    const view = {
      deviceCode: 'SCALE-99',
      deviceName: '二号称重台',
      acceptedTypes: ['SCALE_WEIGHT'],
      enabled: true,
      location: '二号台',
      fallbackNote: '人工兜底',
      recentReadings: 0,
      lastReadingAt: '',
    };
    const okResponse = (data: unknown) => ({
      ok: true,
      json: async () => ({ code: 0, message: 'ok', data }),
    } as Response);
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(okResponse(view))
      .mockResolvedValueOnce(okResponse({ ...view, enabled: false }))
      .mockResolvedValueOnce(okResponse(view));

    const { registerGatewayDevice, setGatewayDeviceEnabled, rotateGatewayDeviceKey } = await import(
      './deviceGatewayApi'
    );

    const registered = await registerGatewayDevice({
      deviceCode: 'SCALE-99',
      deviceName: '二号称重台',
      acceptedTypes: ['SCALE_WEIGHT'],
      deviceKey: 'new-key',
    });
    const disabled = await setGatewayDeviceEnabled('SCALE-99', false);
    const rotated = await rotateGatewayDeviceKey('SCALE-99', 'rotated-key');

    expect(registered.deviceCode).toBe('SCALE-99');
    expect(disabled.enabled).toBe(false);
    expect(rotated.deviceCode).toBe('SCALE-99');
    expect(fetchMock.mock.calls[0][0]).toContain('/api/hardware/gateway/devices');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/hardware/gateway/devices/SCALE-99/status');
    expect(fetchMock.mock.calls[2][0]).toContain('/api/hardware/gateway/devices/SCALE-99/rotate-key');
  });
});
