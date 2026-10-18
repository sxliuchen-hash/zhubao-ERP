import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import DocumentManagementWorkspace from './DocumentManagementWorkspace.vue';
import { documentAuditEvents, documentPackages, documentSummary, evidenceAttachments } from '../../data/documentManagementData';
import { fetchAttachmentPreviewUrl, fetchDocumentAuditEvents, fetchDocumentPackagesPage, fetchDocumentSummary, fetchEvidenceAttachmentsPage, uploadEvidenceAttachment } from '../../services/documentManagementApi';
import type { AuthSession } from '../../services/authApi';

vi.mock('../../services/documentManagementApi', () => ({
  fetchDocumentAuditEvents: vi.fn(), fetchDocumentPackagesPage: vi.fn(), fetchDocumentSummary: vi.fn(),
  fetchEvidenceAttachmentsPage: vi.fn(), uploadEvidenceAttachment: vi.fn(), fetchAttachmentPreviewUrl: vi.fn(),
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

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve;
  });
  return { promise, resolve };
}

describe('DocumentManagementWorkspace', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(fetchDocumentSummary).mockResolvedValue(documentSummary);
    vi.mocked(fetchDocumentPackagesPage).mockResolvedValue({ items: documentPackages, nextCursor: null, hasMore: false });
    vi.mocked(fetchEvidenceAttachmentsPage).mockImplementation(async ({ documentCode }) => ({
      items: evidenceAttachments.filter((item) => item.documentCode === documentCode),
      nextCursor: null,
      hasMore: false,
    }));
    vi.mocked(fetchDocumentAuditEvents).mockResolvedValue(documentAuditEvents);
  });

  it('读取失败显示错误并保持空台账，禁止上传', async () => {
    vi.mocked(fetchDocumentPackagesPage).mockRejectedValue(new Error('附件服务不可用'));
    const wrapper = mountWorkspace();
    await flushPromises();
    expect(wrapper.text()).toContain('文档包读取失败：附件服务不可用');
    expect(wrapper.findAll('.document-master table.document-table tbody tr')).toHaveLength(0);
    expect(wrapper.findAll('.attachment-table tbody tr')).toHaveLength(0);
    expect(wrapper.get('button.primary-button').attributes('disabled')).toBeDefined();
  });

  it('首屏限制 50 条并将台账关键词交给服务端检索', async () => {
    vi.useFakeTimers();
    vi.mocked(fetchDocumentPackagesPage).mockImplementation(async (query) => ({
      items: query?.keyword ? [documentPackages[1]] : [documentPackages[0]],
      nextCursor: null,
      hasMore: false,
    }));
    const wrapper = mountWorkspace();
    try {
      await flushPromises();

      expect(fetchDocumentPackagesPage).toHaveBeenNthCalledWith(1, { limit: 50 });
      await wrapper.get('input[aria-label="查找文档包"]').setValue('质检返工');
      await vi.advanceTimersByTimeAsync(280);
      await flushPromises();

      expect(fetchDocumentPackagesPage).toHaveBeenLastCalledWith({ limit: 50, keyword: '质检返工' });
      const ledger = wrapper.get('.document-master');
      expect(ledger.text()).toContain(documentPackages[1].code);
      expect(ledger.text()).not.toContain(documentPackages[0].code);
      expect(wrapper.text()).toContain('覆盖率（已加载）');
      expect(wrapper.text()).toContain('仅统计当前已加载的文档包');
    } finally {
      wrapper.unmount();
      vi.useRealTimers();
    }
  });

  it('连续输入只在防抖后发起最后一次检索', async () => {
    vi.useFakeTimers();
    vi.mocked(fetchDocumentPackagesPage).mockResolvedValue({ items: [documentPackages[0]], nextCursor: null, hasMore: false });
    const wrapper = mountWorkspace();
    try {
      await flushPromises();
      const input = wrapper.get('input[aria-label="查找文档包"]');
      await input.setValue('质');
      await input.setValue('质检');
      await input.setValue('质检返工');
      await flushPromises();

      expect(fetchDocumentPackagesPage).toHaveBeenCalledTimes(1);
      await vi.advanceTimersByTimeAsync(279);
      await flushPromises();
      expect(fetchDocumentPackagesPage).toHaveBeenCalledTimes(1);
      await vi.advanceTimersByTimeAsync(1);
      await flushPromises();

      expect(fetchDocumentPackagesPage).toHaveBeenLastCalledWith({ limit: 50, keyword: '质检返工' });
    } finally {
      wrapper.unmount();
      vi.useRealTimers();
    }
  });

  it('检索无结果时提示匹配范围，清空后恢复完整台账', async () => {
    vi.useFakeTimers();
    vi.mocked(fetchDocumentPackagesPage).mockImplementation(async (query) => ({
      items: query?.keyword ? [] : documentPackages,
      nextCursor: null,
      hasMore: false,
    }));
    const wrapper = mountWorkspace();
    try {
      await flushPromises();
      const input = wrapper.get('input[aria-label="查找文档包"]');
      await input.setValue('不存在的文档包');
      await flushPromises();
      expect(wrapper.text()).not.toContain('没有匹配的文档包');
      await vi.advanceTimersByTimeAsync(280);
      await flushPromises();

      expect(wrapper.text()).toContain('没有匹配的文档包');
      expect(wrapper.text()).not.toContain('暂无文档包，请从业务单据建立证据关联');
      await input.setValue('');
      await vi.advanceTimersByTimeAsync(280);
      await flushPromises();

      expect(fetchDocumentPackagesPage).toHaveBeenLastCalledWith({ limit: 50 });
      expect(wrapper.text()).toContain(documentPackages[0].code);
      expect(wrapper.text()).not.toContain('没有匹配的文档包');
    } finally {
      wrapper.unmount();
      vi.useRealTimers();
    }
  });

  it('真正没有文档包时仍显示原空台账提示', async () => {
    vi.mocked(fetchDocumentPackagesPage).mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
    const wrapper = mountWorkspace();
    await flushPromises();

    expect(wrapper.text()).toContain('暂无文档包，请从业务单据建立证据关联');
    expect(wrapper.text()).not.toContain('没有匹配的文档包');
  });

  it('首屏只读取当前活动文档包的附件并标注当前包范围', async () => {
    const wrapper = mountWorkspace();
    await flushPromises();

    const currentItems = evidenceAttachments.filter((item) => item.documentCode === documentPackages[0].code);
    expect(fetchEvidenceAttachmentsPage).toHaveBeenCalledWith({ documentCode: documentPackages[0].code, limit: 50 });
    expect(wrapper.findAll('.attachment-table tbody tr')).toHaveLength(currentItems.length);
    expect(wrapper.text()).toContain(`仅显示当前文档包 ${documentPackages[0].code} 的附件`);
    expect(wrapper.text()).not.toContain(evidenceAttachments.find((item) => item.documentCode !== documentPackages[0].code)?.fileName ?? '');
  });

  it('附件续页携带当前包游标并按附件编号去重', async () => {
    const currentItems = evidenceAttachments.filter((item) => item.documentCode === documentPackages[0].code);
    vi.mocked(fetchEvidenceAttachmentsPage).mockImplementation(async ({ documentCode, afterId }) => ({
      items: afterId === undefined ? [currentItems[0], evidenceAttachments[2]] : currentItems,
      nextCursor: afterId === undefined ? 1 : null,
      hasMore: afterId === undefined,
    }));
    const wrapper = mountWorkspace();
    await flushPromises();

    const loadMore = wrapper.get('button[aria-label="加载更多附件"]');
    await loadMore.trigger('click');
    await flushPromises();

    expect(fetchEvidenceAttachmentsPage).toHaveBeenNthCalledWith(1, { documentCode: documentPackages[0].code, limit: 50 });
    expect(fetchEvidenceAttachmentsPage).toHaveBeenNthCalledWith(2, { documentCode: documentPackages[0].code, afterId: 1, limit: 50 });
    expect(wrapper.findAll('.attachment-table tbody tr')).toHaveLength(currentItems.length);
    expect(wrapper.text()).not.toContain(evidenceAttachments[2].fileName);
  });

  it('切换文档包时旧附件响应不能覆盖当前包', async () => {
    const firstPackage = evidenceAttachments.filter((item) => item.documentCode === documentPackages[0].code);
    const secondPackage = evidenceAttachments.filter((item) => item.documentCode === documentPackages[1].code);
    const firstResponse = deferred<{ items: typeof evidenceAttachments; nextCursor: number | null; hasMore: boolean }>();
    const secondResponse = deferred<{ items: typeof evidenceAttachments; nextCursor: number | null; hasMore: boolean }>();
    vi.mocked(fetchEvidenceAttachmentsPage).mockImplementation(async ({ documentCode }) => {
      if (documentCode === documentPackages[0].code) return firstResponse.promise;
      if (documentCode === documentPackages[1].code) return secondResponse.promise;
      return { items: [], nextCursor: null, hasMore: false };
    });
    const wrapper = mountWorkspace();
    await flushPromises();
    await wrapper.get('select[aria-label="上传文档包"]').setValue(documentPackages[1].code);
    await flushPromises();

    secondResponse.resolve({ items: secondPackage, nextCursor: null, hasMore: false });
    await flushPromises();
    firstResponse.resolve({ items: firstPackage, nextCursor: null, hasMore: false });
    await flushPromises();

    expect(wrapper.findAll('.attachment-table tbody tr')).toHaveLength(secondPackage.length);
    expect(wrapper.text()).toContain(secondPackage[0].fileName);
    expect(wrapper.text()).not.toContain(firstPackage[0].fileName);
    expect(fetchEvidenceAttachmentsPage).toHaveBeenCalledWith({ documentCode: documentPackages[1].code, limit: 50 });
  });

  it('当前文档包没有附件时显示范围明确的空状态', async () => {
    vi.mocked(fetchEvidenceAttachmentsPage).mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
    const wrapper = mountWorkspace();
    await flushPromises();

    expect(wrapper.text()).toContain('当前文档包暂无附件');
    expect(wrapper.findAll('.attachment-table tbody tr')).toHaveLength(0);
    expect(wrapper.text()).not.toContain('正在加载当前文档包附件');
  });

  it('卸载时清理未触发的检索计时器', async () => {
    vi.useFakeTimers();
    vi.mocked(fetchDocumentPackagesPage).mockResolvedValue({ items: [documentPackages[0]], nextCursor: null, hasMore: false });
    const wrapper = mountWorkspace();
    await flushPromises();
    await wrapper.get('input[aria-label="查找文档包"]').setValue('卸载前查询');
    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(280);
    await flushPromises();

    expect(fetchDocumentPackagesPage).toHaveBeenCalledTimes(1);
    vi.useRealTimers();
  });

  it('加载更多文档包使用游标并去除跨页重复记录', async () => {
    vi.mocked(fetchDocumentPackagesPage)
      .mockResolvedValueOnce({ items: documentPackages.slice(0, 2), nextCursor: 2, hasMore: true })
      .mockResolvedValueOnce({ items: [documentPackages[1], documentPackages[2]], nextCursor: null, hasMore: false });
    const wrapper = mountWorkspace();
    await flushPromises();

    const loadMore = wrapper.findAll('button').find((button) => button.text().includes('加载更多文档包'));
    expect(loadMore).toBeDefined();
    await loadMore!.trigger('click');
    await flushPromises();

    expect(fetchDocumentPackagesPage).toHaveBeenNthCalledWith(1, { limit: 50 });
    expect(fetchDocumentPackagesPage).toHaveBeenNthCalledWith(2, { afterId: 2, limit: 50 });
    expect(wrapper.findAll('.document-master table tbody tr')).toHaveLength(3);
    expect(wrapper.get('.document-master').text()).toContain(documentPackages[2].code);
    expect(wrapper.findAll('button').some((button) => button.text().includes('加载更多文档包'))).toBe(false);
  });

  it('新关键词的结果优先，迟到的旧请求不能覆盖台账', async () => {
    vi.useFakeTimers();
    const slow = deferred<{ items: typeof documentPackages; nextCursor: number | null; hasMore: boolean }>();
    const fast = deferred<{ items: typeof documentPackages; nextCursor: number | null; hasMore: boolean }>();
    vi.mocked(fetchDocumentPackagesPage).mockImplementation(async (query) => {
      const keyword = query?.keyword;
      if (!keyword) return { items: [documentPackages[0]], nextCursor: null, hasMore: false };
      if (keyword === '慢查询') return slow.promise;
      return fast.promise;
    });
    const wrapper = mountWorkspace();
    try {
      await flushPromises();
      const input = wrapper.get('input[aria-label="查找文档包"]');

      await input.setValue('慢查询');
      await vi.advanceTimersByTimeAsync(280);
      await flushPromises();
      await input.setValue('快速查询');
      await vi.advanceTimersByTimeAsync(280);
      await flushPromises();
      fast.resolve({ items: [documentPackages[2]], nextCursor: null, hasMore: false });
      await flushPromises();
      slow.resolve({ items: [documentPackages[1]], nextCursor: null, hasMore: false });
      await flushPromises();

      const ledger = wrapper.get('.document-master');
      expect(ledger.text()).toContain(documentPackages[2].code);
      expect(ledger.text()).not.toContain(documentPackages[1].code);
      expect(wrapper.text()).not.toContain('慢查询失败');
    } finally {
      wrapper.unmount();
      vi.useRealTimers();
    }
  });

  it('查询和上传刷新后仍使用已选文档包，不静默切换业务对象', async () => {
    vi.mocked(fetchDocumentPackagesPage).mockImplementation(async (query) => ({
      items: query?.keyword ? [documentPackages[2]] : [documentPackages[0], documentPackages[1]],
      nextCursor: null,
      hasMore: false,
    }));
    const wrapper = mountWorkspace();
    await flushPromises();
    await wrapper.get('select[aria-label="上传文档包"]').setValue(documentPackages[1].code);
    await wrapper.get('input[aria-label="查找文档包"]').setValue('硬件');
    await flushPromises();

    expect(wrapper.get('.document-detail').text()).toContain(documentPackages[1].code);
    const file = new File(['照片'], '指定包照片.jpg', { type: 'image/jpeg' });
    const fileInput = wrapper.get('input[type="file"]');
    Object.defineProperty(fileInput.element, 'files', { value: [file], configurable: true });
    await fileInput.trigger('change');
    vi.mocked(uploadEvidenceAttachment).mockResolvedValueOnce({
      code: 'ATT-SELECTED-001', documentCode: documentPackages[1].code, fileName: file.name,
      mediaType: file.type, size: 6, storageLocation: 'local://selected-photo', checksum: 'sha256-selected',
      uploadedAt: '2026-10-10 12:00:00', previewUrl: '',
    });
    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    expect(uploadEvidenceAttachment).toHaveBeenCalledWith(expect.objectContaining({
      documentCode: documentPackages[1].code,
      businessCode: documentPackages[1].businessCode,
    }));
    expect(wrapper.get('.document-detail').text()).toContain(documentPackages[1].code);
    expect(wrapper.get('.document-detail').text()).not.toContain(documentPackages[2].code);
  });

  it('所选文档包没有附件时，不预览其他包的附件', async () => {
    vi.mocked(fetchEvidenceAttachmentsPage).mockImplementation(async ({ documentCode }) => ({
      items: documentCode === documentPackages[0].code
        ? evidenceAttachments.filter((item) => item.documentCode === documentPackages[0].code)
        : [],
      nextCursor: null,
      hasMore: false,
    }));
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
    expect(fetchEvidenceAttachmentsPage).toHaveBeenCalledTimes(2);
    expect(fetchEvidenceAttachmentsPage).toHaveBeenLastCalledWith({ documentCode: documentPackages[0].code, limit: 50 });
    expect(fetchDocumentAuditEvents).toHaveBeenCalledTimes(2);
  });

  it('汇总刷新失败时仍刷新当前包附件并保留上传成功结果', async () => {
    vi.mocked(fetchDocumentSummary)
      .mockResolvedValueOnce(documentSummary)
      .mockRejectedValueOnce(new Error('汇总服务暂不可用'));
    const wrapper = mountWorkspace();
    await flushPromises();
    const file = new File(['照片'], '汇总失败仍保留.jpg', { type: 'image/jpeg' });
    const fileInput = wrapper.get('input[type="file"]');
    Object.defineProperty(fileInput.element, 'files', { value: [file], configurable: true });
    await fileInput.trigger('change');
    vi.mocked(uploadEvidenceAttachment).mockResolvedValueOnce({
      code: 'ATT-SUMMARY-FAIL-001', documentCode: documentPackages[0].code, fileName: file.name,
      mediaType: file.type, size: 6, storageLocation: 'local://summary-fail-photo', checksum: 'sha256-summary-fail',
      uploadedAt: '2026-10-10 12:00:00', previewUrl: '',
    });

    await wrapper.get('button.primary-button').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('附件上传成功');
    expect(wrapper.text()).toContain('ATT-SUMMARY-FAIL-001 / 汇总失败仍保留.jpg');
    expect(wrapper.text()).toContain('附件数据读取失败：汇总服务暂不可用');
    expect(fetchEvidenceAttachmentsPage).toHaveBeenCalledTimes(2);
    expect(fetchEvidenceAttachmentsPage).toHaveBeenLastCalledWith({ documentCode: documentPackages[0].code, limit: 50 });
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
    vi.mocked(fetchEvidenceAttachmentsPage).mockRejectedValue(new Error('附件本体读取失败'));
    const wrapper = mountWorkspace('OPERATOR');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('附件数据读取失败：附件本体读取失败');
    expect(wrapper.text()).toContain(documentPackages[0].code);
    expect(wrapper.findAll('.attachment-table tbody tr')).toHaveLength(0);
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
