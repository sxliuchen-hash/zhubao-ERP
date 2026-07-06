import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import DocumentManagementWorkspace from './DocumentManagementWorkspace.vue';

describe('DocumentManagementWorkspace', () => {
  it('renders document packages, attachments, storage policy and audit events', () => {
    const wrapper = mount(DocumentManagementWorkspace);

    expect(wrapper.text()).toContain('文档与附件管理工作台');
    expect(wrapper.text()).toContain('M3.38 界面密度整改');
    expect(wrapper.text()).toContain('待补证据');
    expect(wrapper.text()).toContain('仓储边界');
    expect(wrapper.text()).toContain('附件上传与预览测试台');
    expect(wrapper.text()).toContain('上传称重照片');
    expect(wrapper.text()).toContain('生成预览链接');
    expect(wrapper.text()).toContain('文档包台账');
    expect(wrapper.text()).toContain('DOC-QC-20260706-021');
    expect(wrapper.text()).toContain('附件与证据清单');
    expect(wrapper.text()).toContain('ATT-QC-20260706-021');
    expect(wrapper.text()).toContain('M3.37 元数据仓储可切换');
    expect(wrapper.text()).toContain('zhubao.documents.repository');
    expect(wrapper.text()).toContain('NAS / S3');
    expect(wrapper.text()).toContain('归档审计');
    expect(wrapper.find('table.document-table').exists()).toBe(true);
  });
});
