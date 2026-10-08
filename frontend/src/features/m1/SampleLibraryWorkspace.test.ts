import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SampleLibraryWorkspace from './SampleLibraryWorkspace.vue';
import {
  correctRegisterImportRow,
  confirmRegisterImport,
  fetchRegisterImportBatch,
  fetchRegisterImportBatches,
  fetchRegisterImportPostingPlan,
  postRegisterImportBatch,
  previewRegisterImport,
} from '../../services/registerImportApi';
import { fetchMachineOutputsByImportBatch } from '../../services/machineOutputApi';
import { fetchMotherOrder, fetchOutboundOrder } from '../../services/businessDocumentApi';

vi.mock('../../services/registerImportApi', () => ({
  correctRegisterImportRow: vi.fn(),
  confirmRegisterImport: vi.fn(),
  fetchRegisterImportBatch: vi.fn(),
  fetchRegisterImportBatches: vi.fn(),
  fetchRegisterImportPostingPlan: vi.fn(),
  postRegisterImportBatch: vi.fn(),
  previewRegisterImport: vi.fn(),
}));
vi.mock('../../services/machineOutputApi', () => ({
  fetchMachineOutputsByImportBatch: vi.fn(),
}));
vi.mock('../../services/businessDocumentApi', () => ({
  fetchMotherOrder: vi.fn(),
  fetchOutboundOrder: vi.fn(),
}));

describe('SampleLibraryWorkspace', () => {
  beforeEach(() => {
    vi.mocked(fetchRegisterImportBatches).mockRejectedValue(new Error('API 未配置，当前使用本地评审样例'));
    vi.mocked(fetchRegisterImportBatch).mockRejectedValue(new Error('API 未配置，当前使用本地评审样例'));
    vi.mocked(fetchRegisterImportPostingPlan).mockRejectedValue(new Error('API 未配置，当前使用本地评审样例'));
    vi.mocked(correctRegisterImportRow).mockRejectedValue(new Error('API 未配置，当前使用本地评审样例'));
    vi.mocked(fetchMachineOutputsByImportBatch).mockResolvedValue([]);
    vi.mocked(fetchMotherOrder).mockRejectedValue(new Error('未查询结构化母单'));
    vi.mocked(fetchOutboundOrder).mockRejectedValue(new Error('未查询结构化出库单'));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders the sample library as an acceptance-data workbench', () => {
    const wrapper = mount(SampleLibraryWorkspace);

    expect(wrapper.text()).toContain('登记导入');
    expect(wrapper.text()).toContain('登记表导入');
    expect(wrapper.text()).toContain('登记表确认批次复核');
    expect(wrapper.text()).toContain('入账映射计划');
    expect(wrapper.text()).not.toContain('样例矩阵');
    expect(wrapper.text()).not.toContain('开源调研命中');
    expect(wrapper.text()).not.toContain('上线待确认门禁');

    expect(wrapper.findAll('.register-template-row')).toHaveLength(4);
    expect(wrapper.findAll('.register-review-batch-table tbody tr')).toHaveLength(2);
    expect(wrapper.text()).toContain('REG-OUTBOUND');
    expect(wrapper.text()).toContain('paperExpressText');
    expect(wrapper.text()).toContain('sourceRegisterLineNo');
    expect(wrapper.text()).toContain('operatorSignatureName');
    expect(wrapper.text()).toContain('IMP-REG-INBOUND-20260707193000');
    expect(wrapper.text()).toContain('待业务单据入账映射');
    expect(wrapper.text()).toContain('motherOrderCode 缺失');
    expect(wrapper.text()).toContain('登记表上传预检');
    expect(wrapper.text()).toContain('上传预检');
  });

  it('loads recent confirmed register batches from the backend and selects batch detail', async () => {
    vi.mocked(fetchRegisterImportBatches).mockResolvedValueOnce([{
      importBatchNo: 'IMP-REG-POLISHING-20260707200000',
      templateCode: 'REG-POLISHING',
      templateName: '工作项目记录单（日）',
      sourceFileName: '抛光登记表.xlsx',
      targetCommand: 'ReportProductionCommand',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      status: 'CONFIRMED',
      confirmedBy: '复核员 何琳',
      confirmedAt: '2026-07-07T20:00:00',
      confirmRemark: '后端批次',
      nextAction: '待映射为报工/交单审核',
      rows: [{
        importBatchNo: 'IMP-REG-POLISHING-20260707200000',
        sourceLineNo: 12,
        sourceRegisterSequence: '2',
        status: 'READY',
        normalizedFields: {
          workItemName: '精抛',
          handoffAuditStatus: '已交单',
        },
        messages: [],
      }],
    }]);
    vi.mocked(fetchRegisterImportBatch).mockResolvedValueOnce({
      importBatchNo: 'IMP-REG-POLISHING-20260707200000',
      templateCode: 'REG-POLISHING',
      templateName: '工作项目记录单（日）',
      sourceFileName: '抛光登记表.xlsx',
      targetCommand: 'ReportProductionCommand',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      status: 'CONFIRMED',
      confirmedBy: '复核员 何琳',
      confirmedAt: '2026-07-07T20:00:00',
      confirmRemark: '后端批次详情',
      nextAction: '待映射为报工/交单审核',
      rows: [{
        importBatchNo: 'IMP-REG-POLISHING-20260707200000',
        sourceLineNo: 12,
        sourceRegisterSequence: '2',
        status: 'READY',
        normalizedFields: {
          operator: '员工 A',
          workItemName: '精抛',
          handoffAuditStatus: '已交单',
        },
        messages: [],
      }],
    });
    vi.mocked(fetchRegisterImportPostingPlan).mockResolvedValue({
      importBatchNo: 'IMP-REG-POLISHING-20260707200000',
      templateCode: 'REG-POLISHING',
      templateName: '工作项目记录单（日）',
      targetCommand: 'ReportProductionCommand',
      status: 'BLOCKED',
      totalRows: 1,
      readyDrafts: 0,
      warningDrafts: 0,
      blockedDrafts: 1,
      nextAction: '先补齐业务主数据',
      drafts: [{
        importBatchNo: 'IMP-REG-POLISHING-20260707200000',
        sourceLineNo: 12,
        sourceRegisterSequence: '2',
        targetCommand: 'ReportProductionCommand',
        status: 'BLOCKED',
        mappedFields: {
          workItemName: '精抛',
          handoffAuditStatus: '已交单',
        },
        missingFields: [{
          field: 'workOrderCode',
          level: 'BLOCK',
          message: 'workOrderCode 缺失，不能生成正式业务入账命令',
          fallback: '在导入复核页补齐或匹配主数据后重试',
        }],
        warningMessages: [],
      }],
    });

    const wrapper = mount(SampleLibraryWorkspace);
    await flushPromises();
    await wrapper.find('.register-review-batch-table tbody tr').trigger('click');
    await flushPromises();

    expect(fetchRegisterImportBatches).toHaveBeenCalledWith(10);
    expect(fetchRegisterImportBatch).toHaveBeenCalledWith('IMP-REG-POLISHING-20260707200000');
    expect(fetchRegisterImportPostingPlan).toHaveBeenCalledWith('IMP-REG-POLISHING-20260707200000');
    expect(wrapper.text()).toContain('后端结构化批次');
    expect(wrapper.text()).toContain('后端入账映射计划');
    expect(wrapper.text()).toContain('IMP-REG-POLISHING-20260707200000');
    expect(wrapper.text()).toContain('后端批次详情');
    expect(wrapper.text()).toContain('精抛');
    expect(wrapper.text()).toContain('workOrderCode 缺失');
  });

  it('submits a register review correction and refreshes the posting plan', async () => {
    const blockedBatch = {
      importBatchNo: 'IMP-REG-OUTBOUND-20260707210000',
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      sourceFileName: '出货登记表.xlsx',
      targetCommand: 'ShipFinishedStockCommand',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      status: 'CONFIRMED' as const,
      confirmedBy: '复核员 何琳',
      confirmedAt: '2026-07-07T21:00:00',
      confirmRemark: '后端待补录批次',
      nextAction: '待补齐业务主数据',
      rows: [{
        importBatchNo: 'IMP-REG-OUTBOUND-20260707210000',
        sourceLineNo: 6,
        sourceRegisterSequence: '1',
        status: 'READY' as const,
        normalizedFields: {
          sourceRegisterType: '交货情况登记表',
          sourceRegisterLineNo: '6',
          deliveryDate: '2026-07-07',
          quantity: '120',
          quantityUnit: '件',
          logisticsNo: 'SF-OUT-20260707-022',
        },
        messages: [],
      }],
    };
    const blockedPlan = {
      importBatchNo: blockedBatch.importBatchNo,
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      targetCommand: 'ShipFinishedStockCommand',
      status: 'BLOCKED' as const,
      totalRows: 1,
      readyDrafts: 0,
      warningDrafts: 0,
      blockedDrafts: 1,
      nextAction: '先补齐业务主数据',
      drafts: [{
        importBatchNo: blockedBatch.importBatchNo,
        sourceLineNo: 6,
        sourceRegisterSequence: '1',
        targetCommand: 'ShipFinishedStockCommand',
        status: 'BLOCKED' as const,
        mappedFields: {
          logisticsNo: 'SF-OUT-20260707-022',
        },
        missingFields: [{
          field: 'shipmentCode',
          level: 'BLOCK' as const,
          message: 'shipmentCode 缺失，不能生成正式业务入账命令',
          fallback: '在导入复核页补齐或匹配主数据后重试',
        }],
        warningMessages: [],
      }],
    };
    const correctedBatch = {
      ...blockedBatch,
      nextAction: '已完成登记表复核补录：样例库复核员；重新生成入账映射计划后再决定是否显式入账',
      rows: blockedBatch.rows.map((row) => ({
        ...row,
        normalizedFields: {
          ...row.normalizedFields,
          shipmentCode: 'OUT-REG-001',
        },
      })),
    };
    const readyPlan = {
      ...blockedPlan,
      status: 'READY' as const,
      readyDrafts: 1,
      blockedDrafts: 0,
      nextAction: '可生成出库单和成品出库命令，入账时必须校验客户成品库存',
      drafts: blockedPlan.drafts.map((draft) => ({
        ...draft,
        status: 'READY' as const,
        mappedFields: {
          ...draft.mappedFields,
          shipmentCode: 'OUT-REG-001',
        },
        missingFields: [],
      })),
    };

    vi.mocked(fetchRegisterImportBatches).mockResolvedValueOnce([blockedBatch]);
    vi.mocked(fetchRegisterImportPostingPlan)
      .mockResolvedValueOnce(blockedPlan)
      .mockResolvedValueOnce(readyPlan);
    vi.mocked(correctRegisterImportRow).mockResolvedValueOnce(correctedBatch);

    const wrapper = mount(SampleLibraryWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('复核补录');
    expect(wrapper.text()).toContain('shipmentCode 缺失');

    await wrapper.find('.register-correction-panel input').setValue('OUT-REG-001');
    await wrapper.find('form.register-correction-panel').trigger('submit');
    await flushPromises();

    expect(correctRegisterImportRow).toHaveBeenCalledWith(blockedBatch.importBatchNo, 6, {
      fields: {
        shipmentCode: 'OUT-REG-001',
      },
      operator: '样例库复核员',
      correctionRemark: '样例库登记表复核补录',
    });
    expect(fetchRegisterImportPostingPlan).toHaveBeenLastCalledWith(blockedBatch.importBatchNo);
    expect(wrapper.text()).toContain('已补录第 6 行 shipmentCode');
    expect(wrapper.text()).toContain('OUT-REG-001');
    expect(wrapper.text()).toContain('READY');
  });

  it('runs a register import preview and renders normalized rows', async () => {
    vi.mocked(previewRegisterImport).mockResolvedValueOnce({
      importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      sourceFileName: '出货登记表.xlsx',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      nextAction: '可进入人工复核或确认导入',
      rows: [{
        sourceLineNo: 3,
        sourceRegisterSequence: '1',
        status: 'READY',
        normalizedFields: {
          paperExpressText: '顺丰 SF-OUT-20260706-022',
          logisticsNo: 'SF-OUT-20260706-022',
        },
        messages: [],
      }],
    });

    const wrapper = mount(SampleLibraryWorkspace);
    await wrapper.find('select').setValue('REG-OUTBOUND');
    const fileInput = wrapper.find('input[type="file"]');
    const file = new File(['xlsx'], '出货登记表.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    Object.defineProperty(fileInput.element, 'files', { value: [file] });
    await fileInput.trigger('change');
    await wrapper.find('form.register-preview-console').trigger('submit');
    await flushPromises();

    expect(previewRegisterImport).toHaveBeenCalledWith(expect.objectContaining({
      templateCode: 'REG-OUTBOUND',
      file,
      fileName: '出货登记表.xlsx',
    }));
    expect(wrapper.text()).toContain('IMP-REG-OUTBOUND-20260707190000');
    expect(wrapper.text()).toContain('SF-OUT-20260706-022');
    expect(wrapper.findAll('.register-preview-table tbody tr')).toHaveLength(1);
  });

  it('confirms an executable register preview as a structured batch', async () => {
    vi.mocked(previewRegisterImport).mockResolvedValueOnce({
      importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      sourceFileName: '出货登记表.xlsx',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      nextAction: '可进入人工复核或确认导入',
      rows: [{
        sourceLineNo: 3,
        sourceRegisterSequence: '1',
        status: 'READY',
        normalizedFields: {
          sourceRegisterLineNo: '3',
          paperExpressText: '顺丰 SF-OUT-20260706-022',
          logisticsNo: 'SF-OUT-20260706-022',
        },
        messages: [],
      }],
    });
    vi.mocked(confirmRegisterImport).mockResolvedValueOnce({
      importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      sourceFileName: '出货登记表.xlsx',
      targetCommand: 'ShipFinishedStockCommand',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      status: 'CONFIRMED',
      confirmedBy: '样例库复核员',
      confirmedAt: '2026-07-07T19:10:00',
      confirmRemark: '样例库确认导入仅保存结构化批次',
      nextAction: '已落结构化导入批次，待业务单据入账映射',
      rows: [{
        importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
        sourceLineNo: 3,
        sourceRegisterSequence: '1',
        status: 'READY',
        normalizedFields: {
          sourceRegisterLineNo: '3',
          logisticsNo: 'SF-OUT-20260706-022',
        },
        messages: [],
      }],
    });

    const wrapper = mount(SampleLibraryWorkspace);
    await wrapper.find('select').setValue('REG-OUTBOUND');
    const fileInput = wrapper.find('input[type="file"]');
    const file = new File(['xlsx'], '出货登记表.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    Object.defineProperty(fileInput.element, 'files', { value: [file] });
    await fileInput.trigger('change');
    await wrapper.find('form.register-preview-console').trigger('submit');
    await flushPromises();
    await wrapper.findAll('.register-preview-actions button')[1].trigger('click');
    await flushPromises();

    expect(confirmRegisterImport).toHaveBeenCalledWith(expect.objectContaining({
      operator: '样例库复核员',
      preview: expect.objectContaining({
        importBatchNo: 'IMP-REG-OUTBOUND-20260707190000',
      }),
    }));
    expect(wrapper.text()).toContain('CONFIRMED / 样例库复核员');
    expect(wrapper.text()).toContain('ShipFinishedStockCommand');
    expect(wrapper.text()).toContain('业务单据入账映射');
  });

  it('executes an explicit register batch posting command and renders ledger result', async () => {
    const readyBatch = {
      importBatchNo: 'IMP-REG-INBOUND-20260707203000',
      templateCode: 'REG-INBOUND',
      templateName: '签收入库登记表',
      sourceFileName: '入库登记表.xlsx',
      targetCommand: 'ConfirmInboundCommand',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      status: 'CONFIRMED' as const,
      confirmedBy: '复核员 何琳',
      confirmedAt: '2026-07-07T20:30:00',
      confirmRemark: '后端可入账批次',
      nextAction: '待映射为母单/签收入库业务命令',
      rows: [{
        importBatchNo: 'IMP-REG-INBOUND-20260707203000',
        sourceLineNo: 4,
        sourceRegisterSequence: '1',
        status: 'READY' as const,
        normalizedFields: {
          sourceRegisterType: '签收入库登记表',
          sourceRegisterLineNo: '4',
          motherOrderCode: 'MO-REG-001',
          customerId: '1001',
          materialCode: 'MAT-GOLD-001',
          warehouseCode: 'RAW-CUSTOMER',
          inboundDate: '2026-07-07',
          expressCompany: '顺丰',
          courierNo: 'SF-IN-20260707-001',
          goodsName: '客户来料配件',
          quantity: '12',
          quantityUnit: '件',
          weight: '18.50',
          operator: '复核员 何琳',
        },
        messages: [],
      }],
    };
    const readyPlan = {
      importBatchNo: readyBatch.importBatchNo,
      templateCode: 'REG-INBOUND',
      templateName: '签收入库登记表',
      targetCommand: 'ConfirmInboundCommand',
      status: 'READY' as const,
      totalRows: 1,
      readyDrafts: 1,
      warningDrafts: 0,
      blockedDrafts: 0,
      nextAction: '可进入业务命令生成',
      drafts: [{
        importBatchNo: readyBatch.importBatchNo,
        sourceLineNo: 4,
        sourceRegisterSequence: '1',
        targetCommand: 'ConfirmInboundCommand',
        status: 'READY' as const,
        mappedFields: {
          motherOrderCode: 'MO-REG-001',
          customerId: '1001',
          materialCode: 'MAT-GOLD-001',
          quantity: '12',
          weight: '18.50',
        },
        missingFields: [],
        warningMessages: [],
      }],
    };
    const postedBatch = {
      ...readyBatch,
      status: 'POSTED' as const,
      nextAction: '已执行显式业务入账命令，重复执行将被拒绝',
      rows: readyBatch.rows.map((row) => ({ ...row, status: 'POSTED' as const })),
    };
    const postedPlan = {
      ...readyPlan,
      status: 'POSTED' as const,
      readyDrafts: 0,
      nextAction: '该登记表批次已完成显式入账，重复执行会被拒绝',
      drafts: readyPlan.drafts.map((draft) => ({ ...draft, status: 'POSTED' as const })),
    };

    vi.mocked(fetchRegisterImportBatches).mockResolvedValueOnce([readyBatch]);
    vi.mocked(fetchRegisterImportPostingPlan)
      .mockResolvedValueOnce(readyPlan)
      .mockResolvedValueOnce(postedPlan);
    vi.mocked(fetchRegisterImportBatch).mockResolvedValueOnce(postedBatch);
    vi.mocked(postRegisterImportBatch).mockResolvedValueOnce({
      importBatchNo: readyBatch.importBatchNo,
      templateCode: 'REG-INBOUND',
      templateName: '签收入库登记表',
      status: 'POSTED',
      totalDrafts: 1,
      postedDrafts: 1,
      skippedDrafts: 0,
      blockedDrafts: 0,
      warningDrafts: 0,
      operator: '样例库复核员',
      postedAt: '2026-07-07T20:35:00',
      nextAction: '已通过后端业务服务完成入账；库存变化已进入库存台账',
      items: [{
        sourceLineNo: 4,
        sourceRegisterSequence: '1',
        targetCommand: 'ConfirmInboundCommand',
        status: 'POSTED',
        businessCode: 'MO-REG-001',
        ledgerCode: 'LEDGER-REG-001',
        message: '已执行 ConfirmInboundCommand',
      }],
    });
    vi.mocked(fetchMotherOrder).mockResolvedValueOnce({
      code: 'MO-REG-001',
      customerId: 1001,
      ownerCustomerId: 1001,
      materialCode: 'MAT-GOLD-001',
      inboundDate: '2026-07-07',
      expressCompany: '顺丰',
      courierNo: 'SF-IN-20260707-001',
      senderName: '张先生',
      receiverName: '何琳',
      unpackSigner: '何琳',
      goodsName: '客户来料配件',
      ownerType: 'CUSTOMER',
      customerOwned: true,
      warehouseCode: 'RAW-CUSTOMER',
      quantity: 12,
      quantityUnit: '件',
      weight: 18.5,
      stockStatus: 'RAW_WAITING',
      ledgerCode: 'LEDGER-REG-001',
      sourceRegisterType: '签收入库登记表',
      sourceRegisterLineNo: 4,
      sourceRegisterSequence: '1',
      operator: '库管员 何琳',
      evidenceCode: 'SF-IN-20260707-001',
      remark: '登记表批次入账',
      status: 'INBOUND_CONFIRMED',
      createdAt: '2026-07-07T20:35:00',
    });
    vi.mocked(fetchMachineOutputsByImportBatch).mockResolvedValueOnce([{
      code: 'MOR-REG-ENG-001',
      workDate: '2026-07-07',
      customerId: 1001,
      customerNameSnapshot: '恒信珠宝',
      productCategory: '戒托',
      machineNoSnapshot: 'ENG-03',
      workOrderCode: 'WO-REG-ENG-001',
      workReportCode: 'WO-REG-ENG-001',
      outputQuantity: 12,
      quantityUnit: '件',
      operatorSignatureName: '周凯',
      sourceRegisterType: '雕刻机统计单（日）',
      sourceLineNo: 6,
      machineLineNo: '3',
      sourceSequenceNo: '1',
      importBatchNo: readyBatch.importBatchNo,
      ledgerCode: 'LEDGER-MACHINE-001',
      status: 'SUBMITTED',
      remark: '登记表导入',
      createdBy: '车间组长 李华',
      createdAt: '2026-07-07T20:35:00',
    }]);

    const wrapper = mount(SampleLibraryWorkspace);
    await flushPromises();
    const executeButton = wrapper.findAll('button').find((button) => button.text().includes('执行入账'));
    expect(executeButton?.attributes('disabled')).toBeUndefined();
    await executeButton?.trigger('click');
    await flushPromises();

    expect(postRegisterImportBatch).toHaveBeenCalledWith(readyBatch.importBatchNo, {
      allowWarnings: false,
      operator: '样例库复核员',
      postingRemark: '样例库登记表批次显式入账',
    });
    expect(fetchRegisterImportBatch).toHaveBeenCalledWith(readyBatch.importBatchNo);
    expect(fetchMotherOrder).toHaveBeenCalledWith('MO-REG-001');
    expect(fetchOutboundOrder).not.toHaveBeenCalled();
    expect(fetchMachineOutputsByImportBatch).toHaveBeenCalledWith(readyBatch.importBatchNo);
    expect(wrapper.text()).toContain('POSTED / 样例库复核员');
    expect(wrapper.text()).toContain('MO-REG-001');
    expect(wrapper.text()).toContain('LEDGER-REG-001');
    expect(wrapper.text()).toContain('业务单据快照');
    expect(wrapper.text()).toContain('结构化母单 / MO-REG-001');
    expect(wrapper.text()).toContain('INBOUND_CONFIRMED / 1001 / MAT-GOLD-001');
    expect(wrapper.text()).toContain('设备产量');
    expect(wrapper.text()).toContain('MOR-REG-ENG-001 / ENG-03');
    expect(wrapper.text()).toContain('周凯 / LEDGER-MACHINE-001');
    expect(wrapper.text()).toContain('已入业务单据');
  });

  it('renders a structured outbound order after explicit outbound register posting', async () => {
    const readyBatch = {
      importBatchNo: 'IMP-REG-OUTBOUND-20260707204500',
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      sourceFileName: '出货登记表.xlsx',
      targetCommand: 'ShipFinishedStockCommand',
      totalRows: 1,
      readyRows: 1,
      warningRows: 0,
      blockedRows: 0,
      executable: true,
      status: 'CONFIRMED' as const,
      confirmedBy: '复核员 何琳',
      confirmedAt: '2026-07-07T20:45:00',
      confirmRemark: '后端可入账出货批次',
      nextAction: '待映射为出库业务命令',
      rows: [{
        importBatchNo: 'IMP-REG-OUTBOUND-20260707204500',
        sourceLineNo: 5,
        sourceRegisterSequence: '2',
        status: 'READY' as const,
        normalizedFields: {
          sourceRegisterType: '交货情况登记表',
          sourceRegisterLineNo: '5',
          outboundOrderCode: 'OUT-REG-001',
          motherOrderCode: 'MO-REG-001',
          workOrderCode: 'WO-REG-001',
          customerId: '1001',
          materialCode: 'MAT-GOLD-001',
          fromWarehouseCode: 'FINISHED-CUSTOMER',
          toWarehouseCode: 'SHIPPED-CUSTOMER',
          deliveryDate: '2026-07-07',
          deliveryCategory: '成品',
          quantity: '6',
          quantityUnit: '件',
          weight: '9.20',
          expressCompany: '顺丰',
          logisticsNo: 'SF-OUT-20260707-001',
          paperExpressText: '顺丰 SF-OUT-20260707-001',
          handler: '何琳',
          operator: '复核员 何琳',
        },
        messages: [],
      }],
    };
    const readyPlan = {
      importBatchNo: readyBatch.importBatchNo,
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      targetCommand: 'ShipFinishedStockCommand',
      status: 'READY' as const,
      totalRows: 1,
      readyDrafts: 1,
      warningDrafts: 0,
      blockedDrafts: 0,
      nextAction: '可进入业务命令生成',
      drafts: [{
        importBatchNo: readyBatch.importBatchNo,
        sourceLineNo: 5,
        sourceRegisterSequence: '2',
        targetCommand: 'ShipFinishedStockCommand',
        status: 'READY' as const,
        mappedFields: {
          outboundOrderCode: 'OUT-REG-001',
          motherOrderCode: 'MO-REG-001',
          workOrderCode: 'WO-REG-001',
          logisticsNo: 'SF-OUT-20260707-001',
          quantity: '6',
          weight: '9.20',
        },
        missingFields: [],
        warningMessages: [],
      }],
    };
    const postedBatch = {
      ...readyBatch,
      status: 'POSTED' as const,
      nextAction: '已执行显式出库入账命令，重复执行将被拒绝',
      rows: readyBatch.rows.map((row) => ({ ...row, status: 'POSTED' as const })),
    };
    const postedPlan = {
      ...readyPlan,
      status: 'POSTED' as const,
      readyDrafts: 0,
      nextAction: '该出货登记表批次已完成显式入账，重复执行会被拒绝',
      drafts: readyPlan.drafts.map((draft) => ({ ...draft, status: 'POSTED' as const })),
    };

    vi.mocked(fetchRegisterImportBatches).mockResolvedValueOnce([readyBatch]);
    vi.mocked(fetchRegisterImportPostingPlan)
      .mockResolvedValueOnce(readyPlan)
      .mockResolvedValueOnce(postedPlan);
    vi.mocked(fetchRegisterImportBatch).mockResolvedValueOnce(postedBatch);
    vi.mocked(postRegisterImportBatch).mockResolvedValueOnce({
      importBatchNo: readyBatch.importBatchNo,
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      status: 'POSTED',
      totalDrafts: 1,
      postedDrafts: 1,
      skippedDrafts: 0,
      blockedDrafts: 0,
      warningDrafts: 0,
      operator: '样例库复核员',
      postedAt: '2026-07-07T20:50:00',
      nextAction: '已通过后端业务服务完成出库入账；库存变化已进入库存台账',
      items: [{
        sourceLineNo: 5,
        sourceRegisterSequence: '2',
        targetCommand: 'ShipFinishedStockCommand',
        status: 'POSTED',
        businessCode: 'OUT-REG-001',
        ledgerCode: 'LEDGER-OUT-REG-001',
        message: '已执行 ShipFinishedStockCommand',
      }],
    });
    vi.mocked(fetchOutboundOrder).mockResolvedValueOnce({
      code: 'OUT-REG-001',
      motherOrderCode: 'MO-REG-001',
      workOrderCode: 'WO-REG-001',
      customerId: 1001,
      ownerCustomerId: 1001,
      materialCode: 'MAT-GOLD-001',
      fromWarehouseCode: 'FINISHED-CUSTOMER',
      toWarehouseCode: 'SHIPPED-CUSTOMER',
      deliveryDate: '2026-07-07',
      deliveryCategory: '成品',
      quantity: 6,
      quantityUnit: '件',
      weight: 9.2,
      expressCompany: '顺丰',
      logisticsNo: 'SF-OUT-20260707-001',
      paperExpressText: '顺丰 SF-OUT-20260707-001',
      handler: '何琳',
      sourceRegisterType: '交货情况登记表',
      sourceRegisterLineNo: 5,
      sourceRegisterSequence: '2',
      operator: '库管员 何琳',
      evidenceCode: 'SF-OUT-20260707-001',
      remark: '登记表出货批次入账',
      status: 'SHIPPED',
      ledgerCode: 'LEDGER-OUT-REG-001',
      createdAt: '2026-07-07T20:50:00',
    });

    const wrapper = mount(SampleLibraryWorkspace);
    await flushPromises();
    const executeButton = wrapper.findAll('button').find((button) => button.text().includes('执行入账'));
    expect(executeButton?.attributes('disabled')).toBeUndefined();
    await executeButton?.trigger('click');
    await flushPromises();

    expect(postRegisterImportBatch).toHaveBeenCalledWith(readyBatch.importBatchNo, {
      allowWarnings: false,
      operator: '样例库复核员',
      postingRemark: '样例库登记表批次显式入账',
    });
    expect(fetchRegisterImportBatch).toHaveBeenCalledWith(readyBatch.importBatchNo);
    expect(fetchOutboundOrder).toHaveBeenCalledWith('OUT-REG-001');
    expect(fetchMotherOrder).not.toHaveBeenCalled();
    expect(fetchMachineOutputsByImportBatch).toHaveBeenCalledWith(readyBatch.importBatchNo);
    expect(wrapper.text()).toContain('POSTED / 样例库复核员');
    expect(wrapper.text()).toContain('OUT-REG-001');
    expect(wrapper.text()).toContain('LEDGER-OUT-REG-001');
    expect(wrapper.text()).toContain('业务单据快照');
    expect(wrapper.text()).toContain('结构化出库单 / OUT-REG-001');
    expect(wrapper.text()).toContain('SHIPPED / MO-REG-001 / WO-REG-001');
    expect(wrapper.text()).toContain('6件 / 9.2g / SF-OUT-20260707-001');
    expect(wrapper.text()).toContain('交货情况登记表 / 第 5 行');
    expect(wrapper.text()).toContain('已入业务单据');
  });

  it('downloads blocked preview messages as CSV', async () => {
    vi.mocked(previewRegisterImport).mockResolvedValueOnce({
      importBatchNo: 'IMP-REG-OUTBOUND-20260707193000',
      templateCode: 'REG-OUTBOUND',
      templateName: '交货情况登记表',
      sourceFileName: '出货登记表.xlsx',
      totalRows: 1,
      readyRows: 0,
      warningRows: 0,
      blockedRows: 1,
      executable: false,
      nextAction: '先处理阻断行，再确认导入',
      rows: [{
        sourceLineNo: 3,
        sourceRegisterSequence: '1',
        status: 'BLOCKED',
        normalizedFields: {
          customerNameSnapshot: '雅辰饰品',
          paperExpressText: '',
        },
        messages: [{
          ruleCode: 'OUT-LOGISTICS',
          level: 'BLOCK',
          field: 'logisticsNo',
          message: '物流单号不能为空',
          fallback: '从纸质快递栏拆分或手工补录',
        }],
      }],
    });
    const createObjectURL = vi.fn(() => 'blob:register-issues');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    const wrapper = mount(SampleLibraryWorkspace);
    const fileInput = wrapper.find('input[type="file"]');
    const file = new File(['xlsx'], '出货登记表.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    Object.defineProperty(fileInput.element, 'files', { value: [file] });
    await fileInput.trigger('change');
    await wrapper.find('form.register-preview-console').trigger('submit');
    await flushPromises();
    await wrapper.find('.register-preview-actions button').trigger('click');

    expect(wrapper.text()).toContain('OUT-LOGISTICS');
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:register-issues');
  });
});
