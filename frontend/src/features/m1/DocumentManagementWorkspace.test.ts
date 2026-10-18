import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import DocumentManagementWorkspace from './DocumentManagementWorkspace.vue';
import { documentAuditEvents, documentPackages, documentSummary, evidenceAttachments } from '../../data/documentManagementData';
import { fetchAttachmentPreviewUrl, fetchDocumentAuditEvents, fetchDocumentPackages, fetchDocumentSummary, fetchEvidenceAttachments, uploadEvidenceAttachment } from '../../services/documentManagementApi';
import type { AuthSession } from '../../services/authApi';

vi.mock('../../services/documentManagementApi', () => ({
  fetchDocumentAuditEvents: vi.fn(), fetchDocumentPackages: vi.fn(), fetchDocumentSummary: vi.fn(),
  fetchEvidenceAttachments: vi.fn(), uploadEvidenceAttachment: vi.fn(), fetchAttachmentPreviewUrl: vi.fn(),
}));

function mountWorkspace(role = 'ADMIN', dataScope = 'ALL') {
  return mount(DocumentManagementWorkspace, {
    global: {
      provide: {
        'zhubao-auth-session': ref({ role, dataScope } as AuthSession),
      },
    },
  });
}

describe('DocumentManagementWorkspace', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(fetchDocumentSummary).mockResolvedValue(documentSummary);
    vi.mocked(fetchDocumentPackages).mockResolvedValue(documentPackages);
    vi.mocked(fetchEvidenceAttachments).mockResolvedValue(evidenceAttachments);
    vi.mocked(fetchDocumentAuditEvents).mockResolvedValue(documentAuditEvents);
  });

  it('读取失败显示错误并保持空台账，禁止上传', async () => {
    vi.mocked(fetchDocumentPackages).mockRejectedValue(new Error('附件服务不可用'));
    const wrapper = mountWorkspace();
    await flushPromises();
    expect(wrapper.text()).toContain('附件服务不可用');
    expect(wrapper.text()).not.toContain('DOC-QC-20260706-021');
    expect(wrapper.findAll('table.document-table tbody tr')).toHaveLength(0);
    expect(wrapper.get('button.primary-button').attributes('disabled')).toBeDefined();
  });

  it('所选文档包没有附件时，不预览其他包的附件', async () => {
    vi.mocked(fetchEvidenceAttachments).mockResolvedValue(evidenceAttachments.filter(item => item.documentCode === documentPackages[0].code));
    const wrapper = mountWorkspace();
    await flushPromises();
    await wrapper.get('select[aria-label="上传文档包"]').setValue(documentPackages[1].code);
    expect(wrapper.find('.document-detail .evidence-media-viewer').exists()).toBe(false);
    expect(fetchAttachmentPreviewUrl).not.toHaveBeenCalled();
  });

  it('上传成功后重新读取权威台账和附件汇总', async () => {
    const wrapper = mountWorkspace();
    await flushPromises();
    const file = new File(['照片'], '收货照片.jpg', { type: 'image/jpeg' });
    const fileInput = wrapper.get('input[type="file"]');
    Object.defineProperty(fileInput.element, 'files', { value: [file], configurable: true });
    await fileInput.trigger('change');
    vi.mocked(uploadEvidenceAttachment).mockResolvedValueOnce({
      code: 'ATT-REAL-001', documentCode: documentPackages[0].code, fileName: file.name,
      mediaType: file.type, size: 6, storageLocation: 'local://actual-photo', checksum: 'sha256-real',
      uploadedAt: '2026-10-10 12:00:00', previewUrl: '',
    });
    vi.mocked(fetchDocumentSummary).mockResolvedValueOnce({ ...documentSummary, attachments: 6 });
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('ATT-REAL-001 / 收货照片.jpg');
    expect(fetchDocumentSummary).toHaveBeenCalledTimes(2);
    expect(fetchDocumentAuditEvents).toHaveBeenCalledTimes(2);
  });

  it('将所选真实文件上传到指定文档包，失败保留错误', async () => {
    vi.mocked(uploadEvidenceAttachment).mockRejectedValue(new Error('文件格式不支持'));
    const wrapper = mountWorkspace();
    await flushPromises();
    const file = new File(['实际照片内容'], '现场照片.jpg', { type: 'image/jpeg' });
    const fileInput = wrapper.get('input[type="file"]');
    Object.defineProperty(fileInput.element, 'files', { value: [file], configurable: true });
    await fileInput.trigger('change');
    await wrapper.get('select[aria-label="上传文档包"]').setValue(documentPackages[1].code);
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();
    expect(uploadEvidenceAttachment).toHaveBeenCalledWith(expect.objectContaining({
      file, documentCode: documentPackages[1].code, businessCode: documentPackages[1].businessCode,
    }));
    expect(wrapper.text()).toContain('文件格式不支持');
    expect(wrapper.text()).not.toContain('demo-weight-photo.txt');
  });

  it('普通业务角色不请求或显示归档审计，但仍加载附件台账', async () => {
    const wrapper = mountWorkspace('OPERATOR');
    await flushPromises();

    expect(fetchDocumentAuditEvents).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('归档审计');
    expect(wrapper.text()).toContain(documentPackages[0].code);
    expect(wrapper.text()).toContain(evidenceAttachments[0].fileName);
  });

  it('非公司范围的管理角色不请求或显示归档审计', async () => {
    const wrapper = mountWorkspace('GENERAL_MANAGER', 'CUSTOMER_RANGE');
    await flushPromises();

    expect(fetchDocumentAuditEvents).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('归档审计');
    expect(wrapper.text()).toContain(documentPackages[0].code);
  });

  it('附件本体请求失败时明确显示附件数据错误', async () => {
    vi.mocked(fetchEvidenceAttachments).mockRejectedValue(new Error('附件本体读取失败'));
    const wrapper = mountWorkspace('OPERATOR');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('附件数据读取失败：附件本体读取失败');
    expect(wrapper.text()).not.toContain(documentPackages[0].code);
  });

  it('管理员审计请求失败时保留已加载附件并单独提示', async () => {
    vi.mocked(fetchDocumentAuditEvents).mockRejectedValue(new Error('审计服务暂不可用'));
    const wrapper = mountWorkspace('GENERAL_MANAGER');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('审计数据读取失败：审计服务暂不可用');
    expect(wrapper.text()).toContain(documentPackages[0].code);
    expect(wrapper.text()).toContain(evidenceAttachments[0].fileName);
    expect(wrapper.text()).not.toContain('附件数据读取失败：');
  });
});
