import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RequirementComposer from './RequirementComposer.vue';
import { fetchShortcuts, fetchTemplates } from '../../services/masterDataApi';
import { composeRequirement } from './requirementText';

vi.mock('../../services/masterDataApi', () => ({
  fetchTemplates: vi.fn(),
  fetchShortcuts: vi.fn(),
}));

const basic = {
  templateId: 1, templateCode: 'TPL-BEAD-BASIC', name: '常规珠型加工',
  steps: ['开粗', '车形（车成珠型）', '抛光'], enabled: true, sortOrder: 1, version: 1,
};
const carve = {
  templateId: 2, templateCode: 'TPL-BEAD-CARVE', name: '珠型雕刻加工',
  steps: ['开粗', '车形（车成珠型）', '雕刻', '抛光'], enabled: true, sortOrder: 2, version: 2,
};

describe('加工要求模板勾选', () => {
  beforeEach(() => {
    vi.mocked(fetchTemplates).mockResolvedValue([basic, carve]);
    vi.mocked(fetchShortcuts).mockResolvedValue([]);
  });

  it('切换模板替换步骤并保留补充说明', async () => {
    const wrapper = mount(RequirementComposer, { props: { modelValue: '' } });
    await flushPromises();
    await wrapper.get('[aria-label="本单补充说明"]').setValue('孔径 2 毫米');
    await wrapper.get('[aria-label="加工要求模板"]').setValue('TPL-BEAD-BASIC');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value)
      .toBe(composeRequirement(basic.steps, '孔径 2 毫米'));
    await wrapper.get('[aria-label="加工要求模板"]').setValue('TPL-BEAD-CARVE');
    const text = (wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value;
    expect(text).toBe(composeRequirement(carve.steps, '孔径 2 毫米'));
    expect(text).not.toContain('先开粗，再车形（车成珠型），最后抛光\n先');
    expect(wrapper.emitted('update:templateCode')?.at(-1)).toEqual(['TPL-BEAD-CARVE']);
    wrapper.unmount();
  });

  it('手改最终文字后，取消覆盖则保留原文', async () => {
    const confirm = vi.fn(() => false);
    vi.stubGlobal('confirm', confirm);
    const wrapper = mount(RequirementComposer, { props: { modelValue: '', templateCode: '', templateVersion: 0 } });
    await flushPromises();
    await wrapper.get('[aria-label="本单补充说明"]').setValue('孔径 2 毫米');
    await wrapper.get('[aria-label="加工要求模板"]').setValue('TPL-BEAD-BASIC');
    const composed = composeRequirement(basic.steps, '孔径 2 毫米');
    await wrapper.setProps({ modelValue: composed, templateCode: 'TPL-BEAD-BASIC', templateVersion: 1 });
    await wrapper.get('[aria-label="加工要求"]').setValue('现场手改，不要覆盖');
    await wrapper.get('[aria-label="加工要求模板"]').setValue('TPL-BEAD-CARVE');
    expect(confirm).toHaveBeenCalled();
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('现场手改，不要覆盖');
    expect((wrapper.get('[aria-label="加工要求模板"]').element as HTMLSelectElement).value).toBe('TPL-BEAD-BASIC');
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  it('确认覆盖后按新模板替换步骤并保留补充说明', async () => {
    vi.stubGlobal('confirm', () => true);
    const wrapper = mount(RequirementComposer, { props: { modelValue: '' } });
    await flushPromises();
    await wrapper.get('[aria-label="本单补充说明"]').setValue('孔径 2 毫米');
    await wrapper.get('[aria-label="加工要求模板"]').setValue('TPL-BEAD-BASIC');
    await wrapper.get('[aria-label="加工要求"]').setValue('手改后再换模板');
    await wrapper.get('[aria-label="加工要求模板"]').setValue('TPL-BEAD-CARVE');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value)
      .toBe(composeRequirement(carve.steps, '孔径 2 毫米'));
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  it('自定义步骤可以加入、调整顺序和取消', async () => {
    const wrapper = mount(RequirementComposer, { props: { modelValue: '' } });
    await flushPromises();
    await wrapper.get('[aria-label="自定义加工步骤"]').setValue('开粗');
    await wrapper.findAll('button').find((button) => button.text() === '加入步骤')!.trigger('click');
    await wrapper.get('[aria-label="自定义加工步骤"]').setValue('抛光');
    await wrapper.findAll('button').find((button) => button.text() === '加入步骤')!.trigger('click');
    await wrapper.get('[aria-label="上移 抛光"]').trigger('click');
    expect(wrapper.get('[aria-label="已选加工步骤"]').text()).toMatch(/1\. 抛光[\s\S]*2\. 开粗/);
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('先抛光，再开粗');
    await wrapper.get('[aria-label="取消步骤 开粗"]').trigger('click');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('抛光');
    wrapper.unmount();
  });

  it('超过 500 字时不写入最终要求', async () => {
    const wrapper = mount(RequirementComposer, { props: { modelValue: '' } });
    await flushPromises();
    await wrapper.get('[aria-label="自定义加工步骤"]').setValue('开粗');
    await wrapper.findAll('button').find((button) => button.text() === '加入步骤')!.trigger('click');
    await wrapper.get('[aria-label="本单补充说明"]').setValue('径'.repeat(500));
    expect(wrapper.text()).toContain('加工要求超过 500 字');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('开粗');
    expect(wrapper.emitted('update:modelValue')?.some((payload) => String(payload[0]).length > 500)).toBeFalsy();
    wrapper.unmount();
  });

  it('外部清空模板和文字后不保留上一单步骤', async () => {
    const wrapper = mount(RequirementComposer, { props: { modelValue: '', templateCode: '', templateVersion: 0 } });
    await flushPromises();
    await wrapper.get('[aria-label="加工要求模板"]').setValue('TPL-BEAD-BASIC');
    const composed = composeRequirement(basic.steps, '');
    await wrapper.setProps({ modelValue: composed, templateCode: 'TPL-BEAD-BASIC', templateVersion: 1 });
    await wrapper.setProps({ modelValue: '', templateCode: '', templateVersion: 0 });
    expect(wrapper.find('[aria-label="已选加工步骤"]').exists()).toBe(false);
    expect((wrapper.get('[aria-label="加工要求模板"]').element as HTMLSelectElement).value).toBe('');
    expect((wrapper.get('[aria-label="加工要求"]').element as HTMLTextAreaElement).value).toBe('');
    wrapper.unmount();
  });
});
