import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import SampleLibraryWorkspace from './SampleLibraryWorkspace.vue';

describe('SampleLibraryWorkspace', () => {
  it('renders reusable acceptance sample scenarios', () => {
    const wrapper = mount(SampleLibraryWorkspace);

    expect(wrapper.text()).toContain('样例库与验收脚本');
    expect(wrapper.text()).toContain('客户来料主链路');
    expect(wrapper.text()).toContain('质检返工样例');
    expect(wrapper.text()).toContain('验收证据');
  });
});
