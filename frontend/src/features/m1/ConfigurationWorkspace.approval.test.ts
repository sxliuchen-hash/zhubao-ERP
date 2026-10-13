import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import ConfigurationWorkspace from './ConfigurationWorkspace.vue';
import { fetchAuthAccounts, fetchAuthAccountPreApprovalApplications, fetchAuthAccountPreApprovalReadiness } from '../../services/authApi';
import { fetchApprovalTasks } from '../../services/approvalApi';

vi.mock('../../services/authApi', () => ({
  fetchAuthSession: vi.fn().mockResolvedValue(null), changeOwnPassword: vi.fn(),
  fetchAuthAccounts: vi.fn(), fetchAuthAccountPreApprovalApplications: vi.fn(), fetchAuthAccountPreApprovalReadiness: vi.fn(),
}));
vi.mock('../../services/approvalApi', () => ({ fetchApprovalTasks: vi.fn() }));
vi.mock('../../services/documentManagementApi', () => ({ fetchStoredContent: vi.fn().mockResolvedValue(null), retireStoredContent: vi.fn() }));

describe('配置页与用户管理分离', () => {
  it('不展示或加载用户管理、工序授权和账号审批', async () => {
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();

    expect(wrapper.find('[aria-label="用户管理"]').exists()).toBe(false);
    expect(wrapper.find('.account-admin-form').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('查找账号');
    expect(wrapper.text()).not.toContain('可做工序');
    expect(wrapper.text()).not.toContain('客户授权');
    expect(wrapper.text()).not.toContain('账号申请与审批');
    expect(fetchAuthAccounts).not.toHaveBeenCalled();
    expect(fetchAuthAccountPreApprovalApplications).not.toHaveBeenCalled();
    expect(fetchAuthAccountPreApprovalReadiness).not.toHaveBeenCalled();
    expect(fetchApprovalTasks).not.toHaveBeenCalled();
  });
});