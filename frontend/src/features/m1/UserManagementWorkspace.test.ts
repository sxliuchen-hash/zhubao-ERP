import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import UserManagementWorkspace from './UserManagementWorkspace.vue';
import {
  deleteAuthAccount, fetchAuthAccountOptions, fetchAuthAccounts, fetchAuthSession,
  logoutAuthSession, unlockAuthAccount, updateAuthAccountStatus, upsertAuthAccount,
  type AuthSession, type AuthUserAccountSummary,
} from '../../services/authApi';

vi.mock('../../services/authApi', () => ({
  deleteAuthAccount: vi.fn(), fetchAuthAccountOptions: vi.fn(), fetchAuthAccounts: vi.fn(), fetchAuthSession: vi.fn(),
  logoutAuthSession: vi.fn(), unlockAuthAccount: vi.fn(), updateAuthAccountStatus: vi.fn(), upsertAuthAccount: vi.fn(),
}));

const worker = {
  username: 'worker', operator: '张三', role: 'OPERATOR', roleLabel: '操作工', dataScope: 'SELF', dataScopeLabel: '本人任务',
  customerScope: [], allowedProcesses: ['雕刻'], enabled: true, demoAccount: false, locked: true,
  passwordChangedAt: '', passwordExpiresAt: '', passwordExpired: false, passwordChangeRequired: false,
  failedLoginCount: 3, lockedUntil: '',
} satisfies AuthUserAccountSummary;
const admin = { ...worker, username: 'admin', operator: '管理员', role: 'ADMIN', roleLabel: '管理员', locked: false };

async function openPage() {
  const wrapper = mount(UserManagementWorkspace);
  await flushPromises();
  return wrapper;
}

describe('用户管理', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(fetchAuthSession).mockResolvedValue({ username: 'admin', role: 'ADMIN' } as AuthSession);
    vi.mocked(fetchAuthAccounts).mockResolvedValue([admin, worker]);
    vi.mocked(fetchAuthAccountOptions).mockResolvedValue({ roles: [
      { value: 'ADMIN', label: '系统管理员', permissions: [{ code: 'AUTH_ACCOUNT_MANAGE', label: '管理账号' }] },
      { value: 'OPERATOR', label: '操作工', permissions: [{ code: 'BUSINESS_PRODUCTION_REPORT', label: '生产报工' }] },
    ] });
  });

  it.each(['OPERATOR', 'GENERAL_MANAGER'])('拒绝 %s 加载或操作账号', async role => {
    vi.mocked(fetchAuthSession).mockResolvedValue({ username: 'other', role } as AuthSession);
    const wrapper = await openPage();
    expect(wrapper.text()).toContain('仅系统管理员');
    expect(fetchAuthAccounts).not.toHaveBeenCalled();
    expect(fetchAuthAccountOptions).not.toHaveBeenCalled();
    expect(wrapper.find('form').exists()).toBe(false);
  });

  it('查询用户并直接创建，角色权限由服务端目录展示', async () => {
    const wrapper = await openPage();
    await wrapper.get('[aria-label="查找用户"]').setValue('张三');
    expect(wrapper.findAll('tbody tr')).toHaveLength(1);
    await wrapper.findAll('button').find(button => button.text() === '新增用户')!.trigger('click');
    expect(wrapper.text()).toContain('生产报工');
    await wrapper.get('[aria-label="用户账号"]').setValue('new-user');
    await wrapper.get('[aria-label="用户姓名"]').setValue('李四');
    await wrapper.get('[aria-label="用户密码"]').setValue('TemporaryPass2026');
    await wrapper.get('[aria-label="确认用户密码"]').setValue('TemporaryPass2026');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(upsertAuthAccount).toHaveBeenCalledWith({
      username: 'new-user', operator: '李四', password: 'TemporaryPass2026', role: 'OPERATOR', dataScope: 'SELF',
      customerScope: [], allowedProcesses: [], enabled: true, demoAccount: false,
    });
    expect(wrapper.text()).toContain('已保存用户 new-user');
    expect(wrapper.text()).not.toContain('申请');
    expect(wrapper.text()).not.toContain('审批');
  });

  it('在同一表单保存角色、客户数据范围和工序，不传审批号或密码', async () => {
    const wrapper = await openPage();
    await wrapper.get('[data-user="worker"]').findAll('button')[0].trigger('click');
    await wrapper.get('[aria-label="用户角色"]').setValue('ADMIN');
    await wrapper.get('[aria-label="用户数据范围"]').setValue('CUSTOMER_RANGE');
    await wrapper.get('[aria-label="授权客户编码"]').setValue('C001，C002\nC001');
    await wrapper.get('input[value="抛光"]').setValue(true);
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(upsertAuthAccount).toHaveBeenCalledWith(expect.objectContaining({
      username: 'worker', password: undefined, role: 'ADMIN', dataScope: 'CUSTOMER_RANGE',
      customerScope: ['C001', 'C002'], allowedProcesses: ['雕刻', '抛光'],
    }));
    expect(vi.mocked(upsertAuthAccount).mock.calls[0][0]).not.toHaveProperty('preApprovalApplicationCode');
  });

  it('重置密码校验确认值，成功后清除输入并直接生效', async () => {
    const wrapper = await openPage();
    await wrapper.get('[data-user="worker"]').findAll('button')[1].trigger('click');
    await wrapper.get('[aria-label="用户密码"]').setValue('TemporaryPass2026');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.text()).toContain('两次密码不一致');
    expect(upsertAuthAccount).not.toHaveBeenCalled();
    await wrapper.get('[aria-label="确认用户密码"]').setValue('TemporaryPass2026');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(upsertAuthAccount).toHaveBeenCalledWith(expect.objectContaining({ username: 'worker', password: 'TemporaryPass2026' }));
    expect(wrapper.find('[aria-label="用户密码"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('已重置 worker 的密码');
  });

  it('启停与解锁直接调用；自身账号不可停用或删除', async () => {
    const wrapper = await openPage();
    const row = wrapper.get('[data-user="worker"]');
    await row.findAll('button').find(button => button.text() === '停用')!.trigger('click');
    await flushPromises();
    expect(updateAuthAccountStatus).toHaveBeenCalledWith('worker', { enabled: false, reason: '管理员用户管理' });
    await row.findAll('button').find(button => button.text() === '解锁')!.trigger('click');
    await flushPromises();
    expect(unlockAuthAccount).toHaveBeenCalledWith('worker', { reason: '管理员解锁' });
    for (const label of ['停用', '删除']) {
      expect(wrapper.get('[data-user="admin"]').findAll('button').find(button => button.text() === label)!.attributes('disabled')).toBeDefined();
    }
  });

  it('删除先确认，取消不发送；成功后刷新', async () => {
    const confirm = vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const wrapper = await openPage();
    const remove = () => wrapper.get('[data-user="worker"]').findAll('button').find(button => button.text() === '删除')!;
    await remove().trigger('click');
    expect(deleteAuthAccount).not.toHaveBeenCalled();
    confirm.mockReturnValue(true);
    await remove().trigger('click');
    await flushPromises();
    expect(deleteAuthAccount).toHaveBeenCalledWith('worker');
    expect(wrapper.text()).toContain('已删除用户 worker');
    confirm.mockRestore();
  });

  it('失败保留编辑内容供重试，不显示保存成功', async () => {
    vi.mocked(upsertAuthAccount).mockRejectedValue(new Error('无权执行账号管理'));
    const wrapper = await openPage();
    await wrapper.get('[data-user="worker"]').findAll('button')[0].trigger('click');
    await wrapper.get('[aria-label="用户姓名"]').setValue('新名字');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('无权执行账号管理');
    expect(wrapper.get('[aria-label="用户姓名"]').element).toHaveProperty('value', '新名字');
    expect(wrapper.text()).not.toContain('已保存用户');
  });

  it('保存自己的账号后清理本地会话并返回登录', async () => {
    const wrapper = await openPage();
    await wrapper.get('[data-user="admin"]').findAll('button')[0].trigger('click');
    expect(wrapper.text()).toContain('需要重新登录');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(logoutAuthSession).toHaveBeenCalledOnce();
    expect(fetchAuthAccounts).toHaveBeenCalledOnce();
  });

  it('没有客户授权的账号也能直接重置密码', async () => {
    vi.mocked(fetchAuthAccounts).mockResolvedValue([{ ...worker, dataScope: 'CUSTOMER_RANGE', customerScope: [] }]);
    const wrapper = await openPage();
    await wrapper.get('[data-user="worker"]').findAll('button')[1].trigger('click');
    await wrapper.get('[aria-label="用户密码"]').setValue('TemporaryPass2026');
    await wrapper.get('[aria-label="确认用户密码"]').setValue('TemporaryPass2026');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(upsertAuthAccount).toHaveBeenCalledWith(expect.objectContaining({ username: 'worker', dataScope: 'CUSTOMER_RANGE', customerScope: [], password: 'TemporaryPass2026' }));
  });

  it('新增账号按后端规则忽略大小写，避免覆盖已有用户', async () => {
    const wrapper = await openPage();
    await wrapper.findAll('button').find(button => button.text() === '新增用户')!.trigger('click');
    await wrapper.get('[aria-label="用户账号"]').setValue(' WORKER ');
    await wrapper.get('[aria-label="用户姓名"]').setValue('另一人');
    await wrapper.get('[aria-label="用户密码"]').setValue('TemporaryPass2026');
    await wrapper.get('[aria-label="确认用户密码"]').setValue('TemporaryPass2026');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('账号已存在');
    expect(upsertAuthAccount).not.toHaveBeenCalled();
  });
});
