import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import M0StatusCard from './M0StatusCard.vue';

describe('M0StatusCard', () => {
  it('renders M0 capabilities', () => {
    const wrapper = mount(M0StatusCard);

    expect(wrapper.text()).toContain('M0 能力边界');
    expect(wrapper.text()).toContain('Spring Boot API');
    expect(wrapper.text()).toContain('转移式库存台账');
  });
});
