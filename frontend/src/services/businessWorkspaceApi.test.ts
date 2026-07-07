import { describe, expect, it } from 'vitest';
import {
  mapInboundWorkspace,
  mapInventoryWorkspace,
  mapMobileWorkspace,
  mapProductionWorkspace,
} from './businessWorkspaceApi';

describe('businessWorkspaceApi mappers', () => {
  it('maps inbound workspace DTO fields to UI model fields', () => {
    const model = mapInboundWorkspace({
      checkpoints: [{ label: '视频完整率', value: '92%', detail: '1 个截取任务失败', tone: 'amber' }],
      orders: [
        {
          code: 'MD-001',
          customer: '恒信珠宝',
          material: '18K 金戒托',
          inboundDate: '07-06',
          expressCompany: '顺丰',
          courierNo: 'SF001',
          senderName: '张先生',
          receiverName: '何琳',
          unpackSigner: '何琳',
          goodsName: '戒托来料',
          quantity: 12,
          weight: '18.20 g',
          status: '待拆包',
          videoStatus: '待开始',
          ownerType: '客户所有',
          remark: '拆箱签字待复核',
        },
      ],
      risks: [],
    });

    expect(model.checkpoints[0].status).toBe('1 个截取任务失败');
    expect(model.orders[0]).toMatchObject({
      qty: 12,
      video: '待开始',
      owner: '客户所有',
      expressCompany: '顺丰',
      unpackSigner: '何琳',
      goodsName: '戒托来料',
    });
  });

  it('maps production quantities and handoff node names', () => {
    const model = mapProductionWorkspace({
      stationLoads: [{ label: '抛光', value: '6 张 / 78%', detail: '等待称重', tone: 'amber' }],
      workOrders: [
        {
          code: 'ZD-001',
          customer: '恒信珠宝',
          material: '戒托',
          process: '抛光',
          processCategory: '人工',
          productCategory: '戒托',
          machineNo: 'POL-01',
          workItemName: '精抛',
          startedAt: '13:40',
          finishedAt: '14:30',
          completionStatus: '部分完成',
          handoffAuditStatus: '待交单审核',
          assignee: '李华',
          plannedQuantity: 80,
          finishedQuantity: 56,
          status: '生产中',
          progress: 70,
        },
      ],
      handoffs: [
        {
          code: 'HO-001',
          fromNode: '粗抛 / 李华',
          toNode: '精抛 / 陈敏',
          inputWeight: '121.15 g',
          outputWeight: '119.92 g',
          lossRate: '1.02%',
          status: '可交接',
          tone: 'green',
        },
      ],
    });

    expect(model.stationLoads[0]).toMatchObject({ station: '抛光', queue: '6 张', capacity: '78%' });
    expect(model.workOrders[0]).toMatchObject({
      plannedQty: 80,
      finishedQty: 56,
      machineNo: 'POL-01',
      workItemName: '精抛',
      handoffAuditStatus: '待交单审核',
    });
    expect(model.handoffs[0]).toMatchObject({ from: '粗抛 / 李华', to: '精抛 / 陈敏' });
  });

  it('maps inventory ledger and evidence contracts', () => {
    const model = mapInventoryWorkspace({
      balances: [
        {
          owner: '恒信珠宝',
          batch: 'MD-001',
          material: '戒托',
          location: '原料库',
          status: '待加工',
          quantity: 10,
          weight: '15.00 g',
          frozen: '0',
        },
      ],
      ledgerEntries: [
        {
          time: '09:05',
          code: 'LEDGER-001',
          movement: '来料入库',
          beforeBalance: '0',
          afterBalance: '10',
          sourceDocument: 'MD-001',
          operator: '何琳',
        },
      ],
      timeline: [],
      evidence: [
        {
          code: 'EV-001',
          title: '视频证据',
          detail: 'INT-VID-001',
          owner: '库管员',
          action: '1 个截取失败',
          tone: 'red',
        },
      ],
    });

    expect(model.balances[0].qty).toBe(10);
    expect(model.ledgerEntries[0]).toMatchObject({ bizCode: 'LEDGER-001', source: 'MD-001' });
    expect(model.evidence[0]).toMatchObject({ label: '视频证据', status: '1 个截取失败' });
  });

  it('maps mobile capabilities and offline drafts', () => {
    const model = mapMobileWorkspace({
      tasks: [],
      capabilities: [{ label: '离线草稿', value: '设计中', detail: '弱网暂存', tone: 'amber' }],
      offlineDrafts: [
        {
          code: 'DRAFT-QC-003',
          title: '缺陷拍照',
          detail: '照片已缓存',
          owner: '周婷',
          action: '15:06',
          tone: 'red',
        },
      ],
    });

    expect(model.capabilities[0]).toMatchObject({ status: '设计中', detail: '弱网暂存' });
    expect(model.offlineDrafts[0]).toMatchObject({ scene: '缺陷拍照', savedAt: '15:06' });
  });
});
