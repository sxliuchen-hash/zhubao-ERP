import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import QualityWorkspace from './QualityWorkspace.vue';
import { fetchQualityWorkspace } from '../../services/businessWorkspaceApi';

vi.mock('../../services/businessWorkspaceApi', () => ({ fetchQualityWorkspace: vi.fn() }));
vi.mock('../../services/businessOperationApi', () => ({
  approveLoss: vi.fn(),
}));
vi.mock('../../services/qualityCheckApi', () => ({
  lookupQualityCheck: vi.fn(), openQualityRework: vi.fn(), submitQualityCheck: vi.fn(),
  submitQualityRecheck: vi.fn(), releaseQualityRound: vi.fn(), reportQualityRework: vi.fn(),
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

describe('质检旧入口', () => {
  it('不再在质检池里直接冻结或派发返工', async () => {
    vi.mocked(fetchQualityWorkspace).mockResolvedValue({ metrics: [], cases: [qualityCase], lossApprovals: [], dataSource: 'LIVE' });
    const wrapper = mount(QualityWorkspace);
    await flushPromises();
    expect(wrapper.text()).not.toContain('冻结异常库存');
    expect(wrapper.text()).not.toContain('生成返工工单');
    expect(wrapper.text()).not.toContain('复检放行入库');
    expect(wrapper.text()).toContain('提交损耗申请');
    expect(wrapper.find('[aria-label="缺陷说明"]').exists()).toBe(false);
  });
});
