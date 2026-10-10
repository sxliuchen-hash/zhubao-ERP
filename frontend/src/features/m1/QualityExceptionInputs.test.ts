import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import QualityWorkspace from './QualityWorkspace.vue';
import { fetchQualityWorkspace } from '../../services/businessWorkspaceApi';
import { dispatchRework, freezeQualityStock } from '../../services/businessOperationApi';

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchQualityWorkspace: vi.fn() }));
vi.mock('../../services/businessOperationApi', () => ({
  approveLoss: vi.fn(), dispatchRework: vi.fn(), freezeQualityStock: vi.fn(),
  releaseQualityStock: vi.fn(), reportRework: vi.fn(),
}));
vi.mock('../../services/qualityCheckApi', () => ({
  lookupQualityCheck: vi.fn(), openQualityRework: vi.fn(), submitQualityCheck: vi.fn(),
}));

const qualityCase = {
  code: 'QC-NEW', workOrder: 'ZD-NEW', process: '抛光', inspector: '', defect: '未判定', action: '待处理', status: '待质检',
  operationContext: {
    customerId: 57, ownerCustomerId: 57, motherOrderCode: 'MD-NEW', workOrderCode: 'ZD-NEW',
    materialCode: 'MAT-REAL', warehouseCode: 'SEMI', ownerType: 'CUSTOMER', stockStatus: 'SEMI_FINISHED',
    quantity: 2, weight: 12.5,
  },
};

beforeEach(() => vi.clearAllMocks());

describe('待检异常处理', () => {
  it('未判定不能冻结或返工，填写真实缺陷和原因后可以提交', async () => {
    vi.mocked(fetchQualityWorkspace).mockResolvedValue({ metrics: [], cases: [qualityCase], lossApprovals: [], dataSource: 'LIVE' });
    vi.mocked(freezeQualityStock).mockResolvedValue({ ledgerCode: 'STK-F', qualityCaseCode: 'QC-NEW' } as never);
    vi.mocked(dispatchRework).mockResolvedValue({ ledgerCode: 'STK-R', reworkOrderCode: 'FG-NEW' } as never);
    const wrapper = mount(QualityWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('未判定不能作为缺陷');
    expect(wrapper.text()).toContain('请填写返工原因');
    const freeze = wrapper.findAll('button').find((button) => button.text() === '冻结异常库存')!;
    const rework = wrapper.findAll('button').find((button) => button.text() === '生成返工工单')!;
    expect((freeze.element as HTMLButtonElement).disabled).toBe(true);
    expect((rework.element as HTMLButtonElement).disabled).toBe(true);

    await wrapper.get('[aria-label="缺陷说明"]').setValue('表面划伤');
    await wrapper.get('[aria-label="返工原因"]').setValue('需重新抛光');
    await wrapper.get('[aria-label="返工单号"]').setValue('FG-NEW');
    expect((freeze.element as HTMLButtonElement).disabled).toBe(false);
    await freeze.trigger('click');
    await flushPromises();
    expect(freezeQualityStock).toHaveBeenCalledWith(expect.objectContaining({ code: 'QC-NEW', defect: '未判定' }), expect.objectContaining({
      defect: '表面划伤', reason: '需重新抛光', reworkOrderCode: 'FG-NEW', quantity: 2, weight: '12.5',
    }));

    await wrapper.get('[aria-label="缺陷说明"]').setValue('表面划伤');
    await wrapper.get('[aria-label="返工原因"]').setValue('需重新抛光');
    await wrapper.get('[aria-label="返工单号"]').setValue('FG-NEW');
    await wrapper.findAll('button').find((button) => button.text() === '生成返工工单')!.trigger('click');
    await flushPromises();
    expect(dispatchRework).toHaveBeenCalledWith(expect.objectContaining({ code: 'QC-NEW' }), expect.objectContaining({
      reason: '需重新抛光', reworkOrderCode: 'FG-NEW', quantity: 2, weight: '12.5',
    }));
  });
});
