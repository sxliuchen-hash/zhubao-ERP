<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  documentAuditEvents as fallbackAuditEvents,
  documentPackages as fallbackPackages,
  documentSummary as fallbackSummary,
  evidenceAttachments as fallbackAttachments,
  type DocumentAuditEvent,
  type DocumentManagementSummary,
  type DocumentPackage,
  type EvidenceAttachment,
} from '../../data/documentManagementData';
import {
  fetchAttachmentPreviewUrl,
  fetchDocumentAuditEvents,
  fetchDocumentPackages,
  fetchDocumentSummary,
  fetchEvidenceAttachments,
  uploadEvidenceAttachment,
  type AttachmentPreviewUrl,
  type EvidenceAttachmentUploadResult,
} from '../../services/documentManagementApi';

const summary = ref<DocumentManagementSummary>(fallbackSummary);
const packages = ref<DocumentPackage[]>(fallbackPackages);
const attachments = ref<EvidenceAttachment[]>(fallbackAttachments);
const auditEvents = ref<DocumentAuditEvent[]>(fallbackAuditEvents);
const uploading = ref(false);
const uploadResult = ref<EvidenceAttachmentUploadResult | null>(null);
const uploadError = ref('');
const previewing = ref(false);
const previewResult = ref<AttachmentPreviewUrl | null>(null);
const previewError = ref('');

const activePackage = computed(
  () => packages.value.find((item) => item.status.includes('缺') || item.status.includes('待')) ?? packages.value[0],
);

const activeAttachments = computed(() =>
  attachments.value.filter((item) => item.documentCode === activePackage.value?.code),
);

const previewTargetCode = computed(
  () => uploadResult.value?.code ?? activeAttachments.value[0]?.code ?? attachments.value[0]?.code ?? '',
);

const sourceBasis = [
  { label: '制造工单', value: 'Work Order / Job Card / Stock Entry', detail: '样例按工单、工序和库存台账组织。' },
  { label: '质量检查', value: 'Quality Check / Defect Evidence', detail: '质检附件必须绑定缺陷、处理动作和复检。' },
  { label: '标签编码', value: 'GS1 / 批次 / 业务单号', detail: '附件包围绕母单、工单、质检单和 POC 编码追溯。' },
  { label: '对象存储', value: 'Local / JDBC metadata / NAS / S3', detail: '当前对象内容默认本地存储，附件元数据可切换 JDBC 仓储。' },
] as const;

const evidenceCoverage = computed(() => {
  const required = packages.value.reduce((total, item) => total + item.requiredEvidence, 0);
  const linked = packages.value.reduce((total, item) => total + item.linkedEvidence, 0);

  if (required === 0) return 0;

  return Math.round((linked / required) * 100);
});

const missingPackages = computed(() =>
  packages.value
    .filter((item) => item.linkedEvidence < item.requiredEvidence)
    .map((item) => ({
      code: item.code,
      title: item.title,
      owner: item.owner,
      gap: `${item.requiredEvidence - item.linkedEvidence} 个缺口`,
      tone: item.tone,
    }))
    .slice(0, 4),
);

const controlRows = [
  { label: '元数据仓储', value: 'zhubao.documents.repository', state: 'memory / JDBC', tone: 'green' },
  { label: '内容存储', value: 'zhubao.documents.storage.local-root', state: 'local://evidence', tone: 'blue' },
  { label: '预览边界', value: 'preview-url + checksum', state: '15 分钟签名', tone: 'amber' },
  { label: '后续复用', value: 'NAS / S3 / MinIO', state: '接口预留', tone: 'violet' },
] as const;

onMounted(async () => {
  try {
    const [nextSummary, nextPackages, nextAttachments, nextAuditEvents] = await Promise.all([
      fetchDocumentSummary(),
      fetchDocumentPackages(),
      fetchEvidenceAttachments(),
      fetchDocumentAuditEvents(),
    ]);
    summary.value = nextSummary;
    packages.value = nextPackages;
    attachments.value = nextAttachments;
    auditEvents.value = nextAuditEvents;
  } catch {
    // Keep local deterministic review samples when the backend is not running.
  }
});

async function uploadDemoEvidence() {
  if (!activePackage.value) return;

  uploading.value = true;
  uploadResult.value = null;
  uploadError.value = '';

  try {
    const file = new File(['weight=121.15g;operator=he-lin;scene=document-demo'], 'demo-weight-photo.txt', {
      type: 'text/plain',
    });
    const result = await uploadEvidenceAttachment({
      documentCode: activePackage.value.code,
      businessType: activePackage.value.category,
      businessCode: activePackage.value.businessCode,
      source: 'PC 文档工作台上传',
      owner: activePackage.value.owner,
      fallbackAction: '无',
      file,
    });
    uploadResult.value = result;
    attachments.value = [
      {
        code: result.code,
        documentCode: result.documentCode,
        businessType: activePackage.value.category,
        businessCode: activePackage.value.businessCode,
        fileName: result.fileName,
        mediaType: result.mediaType,
        storageLocation: result.storageLocation,
        storagePolicy: 'hot 180d -> archive',
        source: 'PC 文档工作台上传',
        status: '已上传',
        owner: activePackage.value.owner,
        checksum: result.checksum,
        uploadedAt: result.uploadedAt,
        fallbackAction: '无',
        tone: 'green',
      },
      ...attachments.value,
    ];
    summary.value = {
      ...summary.value,
      attachments: summary.value.attachments + 1,
      auditEvents: summary.value.auditEvents + 1,
    };
  } catch (error) {
    uploadError.value = error instanceof Error ? error.message : '上传附件失败';
  } finally {
    uploading.value = false;
  }
}

async function createPreviewUrl() {
  if (!previewTargetCode.value) return;

  previewing.value = true;
  previewResult.value = null;
  previewError.value = '';

  try {
    previewResult.value = await fetchAttachmentPreviewUrl(previewTargetCode.value);
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '生成预览链接失败';
  } finally {
    previewing.value = false;
  }
}
</script>

<template>
  <div class="page-stack document-page">
    <section class="operations-hero document-hero">
      <div class="hero-copy">
        <h2>附件</h2>
        <p>查看单据照片、称重记录和视频，缺的证据从这里补上。</p>
      </div>
      <div class="hero-kpis">
        <article>
          <span>文档包</span>
          <strong>{{ summary.packages }}</strong>
        </article>
        <article>
          <span>附件</span>
          <strong>{{ summary.attachments }}</strong>
        </article>
        <article>
          <span>覆盖率</span>
          <strong>{{ evidenceCoverage }}%</strong>
        </article>
        <article>
          <span>缺口</span>
          <strong>{{ summary.missingEvidence }}</strong>
        </article>
      </div>
    </section>

    <section class="document-control-board" aria-label="文档证据运营板">
      <article v-if="activePackage" class="document-active-case" :class="`tone-${activePackage.tone}`">
        <div class="panel-title-row">
          <div>
            <span class="eyebrow">Active Package</span>
            <h2>{{ activePackage.title }}</h2>
          </div>
          <StatusBadge :label="activePackage.status" :tone="activePackage.tone" />
        </div>
        <dl class="document-case-grid">
          <dt>文档包</dt>
          <dd>{{ activePackage.code }}</dd>
          <dt>业务对象</dt>
          <dd>{{ activePackage.businessCode }}</dd>
          <dt>负责人</dt>
          <dd>{{ activePackage.owner }}</dd>
          <dt>证据</dt>
          <dd>{{ activePackage.linkedEvidence }}/{{ activePackage.requiredEvidence }}</dd>
          <dt>保留策略</dt>
          <dd>{{ activePackage.retentionPolicy }}</dd>
          <dt>更新时间</dt>
          <dd>{{ activePackage.lastUpdated }}</dd>
        </dl>
      </article>

      <div class="document-gap-stack">
        <div class="panel-title-row">
          <div>
            <span class="eyebrow">Evidence Gaps</span>
            <h2>待补证据</h2>
          </div>
          <StatusBadge :label="`${missingPackages.length} 项`" tone="amber" />
        </div>
        <article v-for="item in missingPackages" :key="item.code" :class="`tone-${item.tone}`">
          <strong>{{ item.code }}</strong>
          <span>{{ item.title }}</span>
          <small>{{ item.owner }} / {{ item.gap }}</small>
        </article>
      </div>

    </section>

    <section class="document-upload-console" aria-label="附件上传与预览测试">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Upload & Preview</span>
          <h2>上传附件</h2>
          <p>选择文件后上传，并生成短期预览链接。</p>
        </div>
        <div class="button-row">
          <button class="primary-button" type="button" :disabled="uploading" @click="uploadDemoEvidence">
            {{ uploading ? '正在上传' : '上传称重照片' }}
          </button>
          <button class="secondary-button" type="button" :disabled="previewing || !previewTargetCode" @click="createPreviewUrl">
            {{ previewing ? '正在生成' : '生成预览链接' }}
          </button>
        </div>
      </div>
      <div class="document-upload-result-grid">
        <article v-if="uploadResult" class="operation-feedback success">
          <strong>附件上传成功</strong>
          <span>{{ uploadResult.code }} / {{ uploadResult.fileName }}</span>
          <small>{{ uploadResult.checksum }}</small>
        </article>
        <article v-else-if="uploadError" class="operation-feedback danger">
          <strong>附件上传失败</strong>
          <span>{{ uploadError }}</span>
          <small>请确认后端已启动，或继续使用本地样例兜底评审页面。</small>
        </article>
        <article v-if="previewResult" class="operation-feedback success">
          <strong>预览链接已生成</strong>
          <span>{{ previewResult.url }}</span>
          <small>过期时间：{{ previewResult.expiresAt }}</small>
        </article>
        <article v-else-if="previewError" class="operation-feedback danger">
          <strong>生成预览链接失败</strong>
          <span>{{ previewError }}</span>
          <small>只有后端已登记的附件才能生成真实预览链接。</small>
        </article>
      </div>
    </section>

    <section class="document-layout">
      <div class="document-master">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Package Ledger</span>
            <h2>文档包台账</h2>
            <p>每个业务动作都必须能关联文档包、附件、存储策略和审计事件。</p>
          </div>
          <StatusBadge label="M3.37 元数据仓储可切换" tone="green" />
        </div>
        <div class="document-table-wrap">
          <table class="document-table">
            <thead>
              <tr>
                <th>文档包</th>
                <th>业务对象</th>
                <th>负责人</th>
                <th>证据完整度</th>
                <th>保留策略</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in packages" :key="item.code">
                <td>
                  <span class="cell-stack">
                    <b>{{ item.title }}</b>
                    <small>{{ item.code }}</small>
                  </span>
                </td>
                <td>
                  <span class="cell-stack">
                    <b>{{ item.category }}</b>
                    <small>{{ item.businessCode }}</small>
                  </span>
                </td>
                <td>{{ item.owner }}</td>
                <td>{{ item.linkedEvidence }}/{{ item.requiredEvidence }}</td>
                <td>{{ item.retentionPolicy }}</td>
                <td><StatusBadge :label="item.status" :tone="item.tone" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="activePackage" class="document-detail">
        <div class="detail-title">
          <span>当前缺口</span>
          <strong>{{ activePackage.code }}</strong>
          <StatusBadge :label="activePackage.status" :tone="activePackage.tone" />
        </div>
        <dl class="detail-grid">
          <dt>标题</dt>
          <dd>{{ activePackage.title }}</dd>
          <dt>业务对象</dt>
          <dd>{{ activePackage.businessCode }}</dd>
          <dt>负责人</dt>
          <dd>{{ activePackage.owner }}</dd>
          <dt>用途</dt>
          <dd>{{ activePackage.purpose }}</dd>
          <dt>更新时间</dt>
          <dd>{{ activePackage.lastUpdated }}</dd>
        </dl>
        <div class="document-linked-list">
          <strong>关联附件</strong>
          <article v-for="attachment in activeAttachments" :key="attachment.code" :class="`tone-${attachment.tone}`">
            <span>{{ attachment.fileName }}</span>
            <StatusBadge :label="attachment.status" :tone="attachment.tone" />
          </article>
        </div>
      </aside>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Attachment Register</span>
          <h2>附件与证据清单</h2>
          <p>当前管理附件元数据、存储位置、校验和与兜底动作，上传内容进入本地存储适配，元数据可切换 JDBC 仓储。</p>
        </div>
      </div>
      <div class="document-table-wrap">
        <table class="document-table attachment-table">
          <thead>
            <tr>
              <th>附件</th>
              <th>业务绑定</th>
              <th>介质</th>
              <th>存储位置</th>
              <th>校验</th>
              <th>兜底</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in attachments" :key="item.code">
              <td>
                <span class="cell-stack">
                  <b>{{ item.code }}</b>
                  <small>{{ item.fileName }}</small>
                </span>
              </td>
              <td>{{ item.businessType }} / {{ item.businessCode }}</td>
              <td>{{ item.mediaType }}</td>
              <td>{{ item.storageLocation }}</td>
              <td>{{ item.checksum }}</td>
              <td>{{ item.fallbackAction }}</td>
              <td><StatusBadge :label="item.status" :tone="item.tone" /></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>归档审计</h2>
            <p>上传、补传、归档、审批锁定都必须留下审计事件。</p>
          </div>
        </div>
        <div class="document-audit-list">
          <article v-for="event in auditEvents" :key="event.eventCode" :class="`tone-${event.tone}`">
            <time>{{ event.time }}</time>
            <div>
              <strong>{{ event.action }}</strong>
              <span>{{ event.documentCode }} / {{ event.operator }}</span>
              <small>{{ event.detail }}</small>
            </div>
            <StatusBadge :label="event.eventCode" :tone="event.tone" />
          </article>
        </div>
      </div>
    </section>
  </div>
</template>
