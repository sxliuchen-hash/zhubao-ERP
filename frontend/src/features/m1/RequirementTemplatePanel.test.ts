import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RequirementTemplatePanel from './RequirementTemplatePanel.vue';
import { disableTemplate, fetchTemplates, saveTemplate, type RequirementTemplate } from '../../services/masterDataApi';

vi.mock('../../services/masterDataApi', () => ({
  fetchTemplates: vi.fn(),
  saveTemplate: vi.fn(),
  disableTemplate: vi.fn(),
}));

const basic: RequirementTemplate = {
  templateId: 1, templateCode: 'TPL-BEAD-BASIC', name: '常规珠型加工',
  steps: ['开粗', '抛光'], enabled: true, sortOrder: 1, version: 1,
};

describe('加工要求模板维护', () => {
  beforeEach(() => {
    vi.mocked(fetchTemplates).mockReset();
    vi.mocked(saveTemplate).mockReset();
    vi.mocked(disableTemplate).mockReset();
  });

  it('无维护权限时只读取启用模板，不显示保存表单', async () => {
    vi.mocked(fetchTemplates).mockResolvedValue([basic]);
    const wrapper = mount(RequirementTemplatePanel, { props: { canManage: false } });
    await flushPromises();
    expect(fetchTemplates).toHaveBeenCalledWith(false);
    expect(wrapper.find('form').exists()).toBe(false);
    expect(wrapper.text()).toContain('常规珠型加工');
    expect(wrapper.text()).not.toContain('编辑');
    wrapper.unmount();
  });

  it('保存进行中忽略重复提交', async () => {
    vi.mocked(fetchTemplates).mockResolvedValue([]);
    let release: (value: RequirementTemplate) => void = () => {};
    vi.mocked(saveTemplate).mockImplementation(() => new Promise((resolve) => { release = resolve; }));
    const wrapper = mount(RequirementTemplatePanel, { props: { canManage: true } });
    await flushPromises();
    await wrapper.get('[aria-label="模板名称"]').setValue('现场珠型');
    const pending = wrapper.get('form').trigger('submit');
    await flushPromises();
    await wrapper.get('form').trigger('submit');
    expect(saveTemplate).toHaveBeenCalledTimes(1);
    release({ ...basic, name: '现场珠型', templateCode: 'TPL-NEW', requestId: 'tpl-1' });
    await pending;
    await flushPromises();
    wrapper.unmount();
  });

  it('版本冲突时重新载入，不算保存成功', async () => {
    const stale = { ...basic, version: 1, steps: ['开粗'] };
    const fresh = { ...basic, version: 4, steps: ['抛光'] };
    vi.mocked(fetchTemplates)
      .mockResolvedValueOnce([stale])
      .mockResolvedValue([fresh]);
    vi.mocked(saveTemplate).mockRejectedValue(new Error('模板已被其他人修改，请刷新后再保存'));
    const wrapper = mount(RequirementTemplatePanel, { props: { canManage: true } });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '编辑')!.trigger('click');
    await wrapper.get('[aria-label="模板名称"]').setValue('想改的名称');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('已重新载入最新内容');
    expect(wrapper.text()).not.toContain('已更新');
    expect((wrapper.get('[aria-label="模板步骤"]').element as HTMLTextAreaElement).value).toBe('抛光');
    expect(saveTemplate).toHaveBeenCalledWith(expect.objectContaining({ version: 1, name: '想改的名称' }));
    wrapper.unmount();
  });

  it('保存成功后下一笔使用新的请求编号', async () => {
    const rows: RequirementTemplate[] = [];
    vi.mocked(fetchTemplates).mockImplementation(async () => rows.map((item) => ({ ...item })));
    vi.mocked(saveTemplate).mockImplementation(async (body) => {
      const saved: RequirementTemplate = {
        templateId: rows.length + 1,
        templateCode: `TPL-NEW-${rows.length + 1}`,
        name: body.name,
        steps: body.steps,
        enabled: true,
        sortOrder: body.sortOrder,
        version: 1,
        requestId: body.requestId,
      };
      rows.push(saved);
      return saved;
    });
    const wrapper = mount(RequirementTemplatePanel, { props: { canManage: true } });
    await flushPromises();
    await wrapper.get('[aria-label="模板名称"]').setValue('现场珠型');
    await wrapper.get('[aria-label="模板步骤"]').setValue('开粗\n抛光');
    await wrapper.get('[aria-label="模板排序"]').setValue('1');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    const first = vi.mocked(saveTemplate).mock.calls[0][0].requestId;

    await wrapper.get('[aria-label="模板名称"]').setValue('现场珠型');
    await wrapper.get('[aria-label="模板步骤"]').setValue('开粗\n抛光');
    await wrapper.get('[aria-label="模板排序"]').setValue('1');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    const second = vi.mocked(saveTemplate).mock.calls[1][0].requestId;
    expect(wrapper.get('h3').text()).toBe('新建模板');
    expect(first).toMatch(/^tpl-/);
    expect(second).toMatch(/^tpl-/);
    expect(second).not.toBe(first);
    wrapper.unmount();
  });

  it('停用进行中不会再次提交', async () => {
    vi.mocked(fetchTemplates).mockResolvedValue([basic]);
    let release: (value: RequirementTemplate) => void = () => {};
    vi.mocked(disableTemplate).mockImplementation(() => new Promise((resolve) => { release = resolve; }));
    const wrapper = mount(RequirementTemplatePanel, { props: { canManage: true } });
    await flushPromises();
    const pending = wrapper.findAll('button').find((button) => button.text() === '停用')!.trigger('click');
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '停用')!.trigger('click');
    expect(disableTemplate).toHaveBeenCalledTimes(1);
    release({ ...basic, enabled: false, version: 2 });
    await pending;
    await flushPromises();
    expect(wrapper.text()).toContain('已建母单的要求不会改变');
    wrapper.unmount();
  });
});
