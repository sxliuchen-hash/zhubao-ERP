import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ConfigurationWorkspace from './ConfigurationWorkspace.vue';
import { changeOwnPassword, fetchAuthSession, type AuthSession } from '../../services/authApi';
import { fetchStoredContent, retireStoredContent, type StoredContentInventory } from '../../services/documentManagementApi';

vi.mock('../../services/authApi', () => ({
  changeOwnPassword: vi.fn(),
  fetchAuthSession: vi.fn(),
}));
vi.mock('../../services/documentManagementApi', () => ({
  fetchStoredContent: vi.fn(),
  retireStoredContent: vi.fn(),
}));

const session: AuthSession = {
  token: 'session-token', username: 'worker01', operator: '操作员', role: 'OPERATOR', roleLabel: '操作工',
  dataScope: 'SELF', dataScopeLabel: '本人任务', expiresAt: '2026-10-14', demoAccount: false,
  passwordChangedAt: '2026-10-01', passwordExpiresAt: '2027-01-01', passwordExpired: false,
  passwordChangeRequired: false, customerScope: ['CUST-001'], menuCodes: [], menuTree: [],
};
const inventory: StoredContentInventory = {
  provider: '本地存储', rootPath: '/data/evidence', fileCount: 1, usedBytes: 1024, usedLabel: '1 KB',
  diskTotalBytes: 10240, diskFreeBytes: 9216, diskUsedPercent: 10,
  items: [{
    code: 'FILE-001', fileName: '来料照片.jpg', businessType: '来料', businessCode: 'IN-001',
    owner: '操作员', storageLocation: '本地', mediaType: 'image/jpeg', status: '可用',
    uploadedAt: '2026-10-13', sizeBytes: 1024, sizeLabel: '1 KB',
  }],
};

describe('ConfigurationWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchAuthSession).mockResolvedValue(session);
    vi.mocked(fetchStoredContent).mockResolvedValue(inventory);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('展示当前登录会话、自助改密和存储内容', async () => {
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();

    expect(wrapper.text()).toContain('个人与存储配置');
    expect(wrapper.text()).toContain('登录会话');
    expect(wrapper.text()).toContain('自助改密');
    expect(wrapper.text()).toContain('操作员 / 操作工');
    expect(wrapper.text()).toContain('本人任务');
    expect(wrapper.text()).toContain('CUST-001');
    expect(wrapper.text()).toContain('存储内容');
    expect(wrapper.text()).toContain('来料照片.jpg');
  });

  it('未登录时禁用自助改密', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValue(null);
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();

    expect(wrapper.get('.auth-password-strip button').attributes('disabled')).toBeDefined();
    expect(wrapper.findAll('.auth-password-strip input').every(input => input.attributes('disabled') !== undefined)).toBe(true);
    await wrapper.get('.auth-password-strip').trigger('submit');
    await flushPromises();
    expect(changeOwnPassword).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请先登录后再自助改密');
  });

  it('两次新密码不一致时不提交改密', async () => {
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();
    const inputs = wrapper.findAll('.auth-password-strip input');
    await inputs[1].setValue('NewPassword2026');
    await inputs[2].setValue('OtherPassword2026');
    await wrapper.get('.auth-password-strip').trigger('submit');
    await flushPromises();

    expect(changeOwnPassword).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('两次新密码不一致');
  });

  it('自助改密仅提交本人密码并清空表单', async () => {
    vi.mocked(changeOwnPassword).mockResolvedValue({ ...session, passwordExpiresAt: '2027-02-01' });
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();
    const inputs = wrapper.findAll('.auth-password-strip input');
    await inputs[0].setValue('CurrentPassword2026');
    await inputs[1].setValue('NewPassword2026');
    await inputs[2].setValue('NewPassword2026');
    await wrapper.get('.auth-password-strip').trigger('submit');
    await flushPromises();

    expect(changeOwnPassword).toHaveBeenCalledWith({ currentPassword: 'CurrentPassword2026', newPassword: 'NewPassword2026' });
    expect(inputs.every(input => (input.element as HTMLInputElement).value === '')).toBe(true);
    expect(wrapper.text()).toContain('自助改密成功，密码到期 2027-02-01');
  });

  it('取消下线保留存储内容，确认后调用下线并刷新', async () => {
    const confirmation = vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();
    await wrapper.get('tbody .table-action').trigger('click');
    expect(retireStoredContent).not.toHaveBeenCalled();

    confirmation.mockReturnValue(true);
    vi.mocked(retireStoredContent).mockResolvedValue({ ...inventory.items[0], status: '已下线' });
    vi.mocked(fetchStoredContent).mockResolvedValue({ ...inventory, items: [{ ...inventory.items[0], status: '已下线' }] });
    await wrapper.get('tbody .table-action').trigger('click');
    await flushPromises();

    expect(retireStoredContent).toHaveBeenCalledWith('FILE-001');
    expect(fetchStoredContent).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('已下线 来料照片.jpg');
    expect(wrapper.get('tbody .table-action').attributes('disabled')).toBeDefined();
  });

  it('存储加载失败时展示错误，刷新后恢复', async () => {
    vi.mocked(fetchStoredContent).mockRejectedValueOnce(new Error('存储服务暂不可用'));
    const wrapper = mount(ConfigurationWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('存储服务暂不可用');
    await wrapper.get('[aria-label="存储内容管理"] .secondary-button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).not.toContain('存储服务暂不可用');
    expect(wrapper.text()).toContain('来料照片.jpg');
  });
});