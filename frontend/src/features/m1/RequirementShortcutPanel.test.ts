import { mount, flushPromises } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import Panel from './RequirementShortcutPanel.vue';
import { fetchShortcuts, saveShortcut, removeShortcut } from '../../services/masterDataApi';
vi.mock('../../services/masterDataApi', () => ({ fetchShortcuts: vi.fn(), saveShortcut: vi.fn(), removeShortcut: vi.fn() }));
describe('基础资料快捷词', () => {
  it('有权限时新增删除，无权限时只读', async () => {
    vi.mocked(fetchShortcuts).mockResolvedValue([{ shortcutId: 1, phrase: '抛光', sortOrder: 1 }]);
    vi.mocked(saveShortcut).mockResolvedValue({ shortcutId: 2, phrase: '开粗', sortOrder: 2 });
    const wrapper = mount(Panel, { props: { canManage: true } });
    await flushPromises();
    await wrapper.get('input').setValue('开粗');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('开粗');
    await wrapper.get('[aria-label="删除快捷词 抛光"]').trigger('click');
    await flushPromises();
    expect(removeShortcut).toHaveBeenCalledWith(1);
    expect(wrapper.text()).not.toContain('抛光');
    await wrapper.setProps({ canManage: false });
    expect(wrapper.find('button').exists()).toBe(false);
    expect(wrapper.find('input').exists()).toBe(false);
  });
});
