import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import DocumentManagementWorkspace from './DocumentManagementWorkspace.vue';
import { documentAuditEvents, documentPackages, documentSummary, evidenceAttachments } from '../../data/documentManagementData';
import { fetchAttachmentPreviewUrl, fetchDocumentAuditEvents, fetchDocumentPackages, fetchDocumentSummary, fetchEvidenceAttachments, uploadEvidenceAttachment } from '../../services/documentManagementApi';

vi.mock('../../services/documentManagementApi', () => ({
  fetchDocumentAuditEvents: vi.fn(), fetchDocumentPackages: vi.fn(), fetchDocumentSummary: vi.fn(),
  fetchEvidenceAttachments: vi.fn(), uploadEvidenceAttachment: vi.fn(), fetchAttachmentPreviewUrl: vi.fn(),
}));

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
    const wrapper = mount(DocumentManagementWorkspace);
    await flushPromises();
    expect(wrapper.text()).toContain('附件服务不可用');
    expect(wrapper.text()).not.toContain('DOC-QC-20260706-021');
    expect(wrapper.findAll('table.document-table tbody tr')).toHaveLength(0);
    expect(wrapper.get('button.primary-button').attributes('disabled')).toBeDefined();
  });

  it('所选文档包没有附件时，不预览其他包的附件', async () => {
    vi.mocked(fetchEvidenceAttachments).mockResolvedValue(evidenceAttachments.filter(item => item.documentCode === documentPackages[0].code));
    const wrapper = mount(DocumentManagementWorkspace);
    await flushPromises();
    await wrapper.get('select[aria-label="上传文档包"]').setValue(documentPackages[1].code);
    const preview = wrapper.findAll('button').find(button => button.text().includes('生成预览链接'))!;
    expect(preview.attributes('disabled')).toBeDefined();
    await preview.trigger('click');
    expect(fetchAttachmentPreviewUrl).not.toHaveBeenCalled();
  });

  it('上传成功后重新读取权威台账和附件汇总', async () => {
    const wrapper = mount(DocumentManagementWorkspace);
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
    const wrapper = mount(DocumentManagementWorkspace);
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
});
