import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import DocumentManagementWorkspace from './DocumentManagementWorkspace.vue';

describe('DocumentManagementWorkspace', () => {
  it('renders document packages, attachments, storage policy and audit events', () => {
    const wrapper = mount(DocumentManagementWorkspace);

    expect(wrapper.text()).toContain('文档与附件管理工作台');
    expect(wrapper.text()).toContain('文档包台账');
    expect(wrapper.text()).toContain('DOC-QC-20260706-021');
    expect(wrapper.text()).toContain('附件与证据清单');
    expect(wrapper.text()).toContain('ATT-QC-20260706-021');
    expect(wrapper.text()).toContain('MinIO/S3');
    expect(wrapper.text()).toContain('归档审计');
    expect(wrapper.find('table.document-table').exists()).toBe(true);
  });
});
