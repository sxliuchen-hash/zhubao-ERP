import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchHardwareWorkspace, mapDevice, mapPocRecord } from './hardwareIntegrationApi';

describe('hardwareIntegrationApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('maps backend hardware status and POC result tone', () => {
    expect(mapDevice({
      code: 'SCAN-PC-01',
      name: 'PC 扫码枪',
      scene: '后台收货',
      protocol: 'USB HID',
      status: 'ONLINE',
      fallback: '手工录入',
    })).toMatchObject({ status: 'online' });

    expect(mapPocRecord({
      code: 'POC-SCAN-001',
      deviceCode: 'PWA-CAM-01',
      scene: '弱光扫码',
      testWindow: '2026-07-06 15:20-15:45',
      result: '阻断',
      evidence: '弱光失败样本',
      blocker: '需要补 ZXing 兜底',
      owner: '车间组长',
      nextAction: '复测',
    })).toMatchObject({ tone: 'red' });
  });

  it('fetches hardware devices, tasks, poc records and summary together', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [{
            code: 'CAM-UNPACK-01',
            name: '拆包台摄像头',
            scene: '拆包视频',
            protocol: 'NVR',
            status: 'DEGRADED',
            fallback: '人工上传 MP4',
          }],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [{
            id: 'INT-VID-001',
            title: '拆包视频截取任务',
            target: 'CAM-UNPACK-01 / MD-001',
            health: 62,
            lastRun: '17:08 截取失败',
            nextAction: '人工上传',
            owner: '库管员',
          }],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [{
            deviceCode: 'SCALE-01',
            deviceName: '电子秤',
            phase: '一期',
            primaryAdapter: '人工读数录入',
            fallbackAdapter: '称重照片',
            evidenceRequirement: '称重照片、重量值',
            nextIntegrationAction: '确认串口/TCP 采集样本',
            riskLevel: '高',
            tone: 'red',
          }],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [{
            deviceCode: 'SCALE-01',
            deviceName: '电子秤',
            protocolType: 'RS232 / USB / TCP reserved + manual reading',
            sampleStatus: '接口待确认',
            requiredParameters: ['interfaceType', 'baudRate', 'dataBits'],
            samplePayload: 'ST,GS,+000123.45,g',
            evidenceCode: 'HW-PROTO-SCALE-001',
            captureMethod: '人工读数照片 + 串口/TCP 报文样本占位',
            nextReviewAction: '确认接口类型和波特率',
            riskLevel: '高',
            tone: 'red',
          }],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [{
            code: 'POC-VID-001',
            deviceCode: 'CAM-UNPACK-01',
            scene: '拆包台视频截取',
            testWindow: '2026-07-06 08:40-09:10',
            result: '有条件通过',
            evidence: 'NVR 可定位',
            blocker: 'NTP 校时',
            owner: '实施顾问',
            nextAction: '复测',
          }],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: [{
            pocCode: 'POC-VID-001',
            scene: '拆包台视频截取',
            result: '有条件通过',
            documentCode: 'DOC-HW-POC-001',
            attachmentCount: 1,
            missingEvidence: 0,
            attachmentCodes: ['ATT-HW-001'],
            latestUploadedAt: '2026-07-06 17:08',
            status: '已关联证据',
            nextAction: '继续复测',
            tone: 'amber',
          }],
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: {
            totalDevices: 1,
            fallbackRequired: 1,
            averageHealth: 62,
            pocRecords: 1,
            pocBlocked: 0,
          },
        }),
      } as Response);

    const model = await fetchHardwareWorkspace();

    expect(model.devices[0].status).toBe('degraded');
    expect(model.tasks[0].id).toBe('INT-VID-001');
    expect(model.adapterPlans[0].deviceCode).toBe('SCALE-01');
    expect(model.protocolSamples[0].deviceCode).toBe('SCALE-01');
    expect(model.protocolSamples[0].samplePayload).toContain('ST,GS');
    expect(model.pocRecords[0].tone).toBe('amber');
    expect(model.pocEvidence[0].attachmentCodes).toContain('ATT-HW-001');
    expect(model.summary.averageHealth).toBe(62);
    expect(fetchMock).toHaveBeenCalledTimes(7);
  });
});
