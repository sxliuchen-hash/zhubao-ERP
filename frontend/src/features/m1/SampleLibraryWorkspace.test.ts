import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import SampleLibraryWorkspace from './SampleLibraryWorkspace.vue';
import { previewRegisterImport } from '../../services/registerImportApi';

vi.mock('../../services/registerImportApi', () => ({
  previewRegisterImport: vi.fn(),
}));

describe('SampleLibraryWorkspace', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders the sample library as an acceptance-data workbench', () => {
    const wrapper = mount(SampleLibraryWorkspace);

    expect(wrapper.text()).toContain('样例验收指挥台');
    expect(wrapper.text()).toContain('M3.71 参考表字段补齐');
    expect(wrapper.text()).toContain('样例矩阵');
    expect(wrapper.text()).toContain('当前主样例');
    expect(wrapper.text()).toContain('开源调研命中');
    expect(wrapper.text()).toContain('现场登记表导入准备度');
    expect(wrapper.text()).toContain('真实验收数据集');
    expect(wrapper.text()).toContain('现场数据缺口');

    expect(wrapper.findAll('.sample-row')).toHaveLength(4);
    expect(wrapper.findAll('.reference-card')).toHaveLength(3);
    expect(wrapper.findAll('.register-template-row')).toHaveLength(4);
    expect(wrapper.findAll('.fixture-row')).toHaveLength(2);
    expect(wrapper.findAll('.gate-card')).toHaveLength(6);
    expect(wrapper.findAll('.field-gap-list article').length).toBeGreaterThan(0);

    expect(wrapper.text()).toContain('ERPNext Job Card');
    expect(wrapper.text()).toContain('Odoo Quality Checks');
    expect(wrapper.text()).toContain('InvenTree Build Orders');
    expect(wrapper.text()).toContain('REG-OUTBOUND');
    expect(wrapper.text()).toContain('paperExpressText');
    expect(wrapper.text()).toContain('登记表上传预检');
    expect(wrapper.text()).toContain('上传预检');
    expect(wrapper.text()).toContain('REAL-FLOW-20260706-001');
    expect(wrapper.text()).toContain('BusinessOperationRealFlowTest');
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
