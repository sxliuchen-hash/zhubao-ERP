<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import EvidencePreviewLink from '../evidence/EvidencePreviewLink.vue';
import type { AuthSession } from '../../services/authApi';
import {
  type DocumentAuditEvent,
  type DocumentManagementSummary,
  type DocumentPackage,
  type EvidenceAttachment,
} from '../../data/documentManagementData';
import {
  fetchDocumentAuditEvents,
  fetchDocumentPackagesPage,
  fetchDocumentSummary,
  fetchEvidenceAttachments,
  uploadEvidenceAttachment,
  type EvidenceAttachmentUploadResult,
} from '../../services/documentManagementApi';

const DOCUMENT_PACKAGE_PAGE_SIZE = 50;
const PACKAGE_SEARCH_DEBOUNCE_MS = 280;
const summary = ref<DocumentManagementSummary>({ packages: 0, attachments: 0, missingEvidence: 0, manualFallbacks: 0, auditEvents: 0 });
const packages = ref<DocumentPackage[]>([]);
const packageNextCursor = ref<number | null>(null);
const packageHasMore = ref(false);
const packageLoading = ref(false);
const packageLoadingMore = ref(false);
const packageLoadError = ref('');
const attachments = ref<EvidenceAttachment[]>([]);
const auditEvents = ref<DocumentAuditEvent[]>([]);
const loadingError = ref('');
const auditLoadError = ref('');
const selectedFile = ref<File | null>(null);
const selectedPackageCode = ref('');
const packageKeyword = ref('');
const detailOpen = ref(false);
const uploading = ref(false);
const uploadResult = ref<EvidenceAttachmentUploadResult | null>(null);
const uploadError = ref('');
const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const canViewAudit = computed(() =>
  ['ADMIN', 'GENERAL_MANAGER'].includes(authSession.value?.role ?? '') && authSession.value?.dataScope === 'ALL',
);
const selectedPackageSnapshot = ref<DocumentPackage | null>(null);
let packageQueryVersion = 0;
let packageSearchTimer: ReturnType<typeof setTimeout> | null = null;

const activePackage = computed(
  () => selectedPackageCode.value
    ? packages.value.find((item) => item.code === selectedPackageCode.value) ?? selectedPackageSnapshot.value ?? undefined
    : packages.value.find((item) => item.status.includes('缺') || item.status.includes('待')) ?? packages.value[0],
);

const activeAttachments = computed(() =>
  attachments.value.filter((item) => item.documentCode === activePackage.value?.code),
);
const visiblePackages = computed(() => packages.value);
const hasPackageSearch = computed(() => packageKeyword.value.trim().length > 0);
const packageOptions = computed(() => {
  if (!selectedPackageSnapshot.value || packages.value.some((item) => item.code === selectedPackageSnapshot.value?.code)) {
    return packages.value;
  }
  return [selectedPackageSnapshot.value, ...packages.value];
});

function selectPackage(code: string) {
  const selected = packages.value.find((item) => item.code === code);
  if (!selected) return;
  selectedPackageSnapshot.value = selected;
  selectedPackageCode.value = code;
  detailOpen.value = true;
}

function backToPackages() {
  detailOpen.value = false;
}

watch(selectedPackageCode, () => {
  if (!selectedPackageCode.value) {
    selectedPackageSnapshot.value = null;
  } else {
    const selected = packages.value.find((item) => item.code === selectedPackageCode.value);
    if (selected) selectedPackageSnapshot.value = selected;
  }
  uploadResult.value = null;
});

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

onMounted(loadDocuments);
function clearPackageSearchTimer() {
  if (packageSearchTimer !== null) clearTimeout(packageSearchTimer);
  packageSearchTimer = null;
}

watch(packageKeyword, () => {
  clearPackageSearchTimer();
  packageQueryVersion += 1;
  packageLoading.value = true;
  packageLoadingMore.value = false;
  packages.value = [];
  packageNextCursor.value = null;
  packageHasMore.value = false;
  packageLoadError.value = '';
  packageSearchTimer = setTimeout(() => {
    packageSearchTimer = null;
    void loadPackagePage(true);
  }, PACKAGE_SEARCH_DEBOUNCE_MS);
});
onBeforeUnmount(() => {
  clearPackageSearchTimer();
  packageQueryVersion += 1;
});

function packageErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function mergePackagePage(nextItems: DocumentPackage[], reset: boolean) {
  const nextPackages = reset ? [] : [...packages.value];
  const existingCodes = new Set(nextPackages.map((item) => item.code));

  for (const item of nextItems) {
    if (existingCodes.has(item.code)) continue;
    existingCodes.add(item.code);
    nextPackages.push(item);
  }

  packages.value = nextPackages;
  if (selectedPackageCode.value) {
    const selected = nextPackages.find((item) => item.code === selectedPackageCode.value);
    if (selected) selectedPackageSnapshot.value = selected;
  }
}

async function loadPackagePage(reset = false) {
  if (!reset && (packageLoading.value || packageLoadingMore.value || !packageHasMore.value || packageNextCursor.value === null)) return;

  const requestVersion = reset ? ++packageQueryVersion : packageQueryVersion;
  const afterId = reset ? undefined : packageNextCursor.value ?? undefined;
  const keyword = packageKeyword.value.trim();

  if (reset) {
    packageLoading.value = true;
    packageLoadingMore.value = false;
    packages.value = [];
    packageNextCursor.value = null;
    packageHasMore.value = false;
  } else {
    packageLoadingMore.value = true;
  }
  packageLoadError.value = '';

  try {
    const page = await fetchDocumentPackagesPage({
      ...(afterId === undefined ? {} : { afterId }),
      limit: DOCUMENT_PACKAGE_PAGE_SIZE,
      ...(keyword ? { keyword } : {}),
    });
    if (requestVersion !== packageQueryVersion) return;

    mergePackagePage(page.items, reset);
    packageNextCursor.value = page.nextCursor;
    packageHasMore.value = page.hasMore && page.nextCursor !== null;
  } catch (error) {
    if (requestVersion !== packageQueryVersion) return;
    packageLoadError.value = packageErrorMessage(error, '文档包读取失败');
    if (reset) {
      packages.value = [];
      packageNextCursor.value = null;
      packageHasMore.value = false;
    }
  } finally {
    if (requestVersion !== packageQueryVersion) return;
    if (reset) packageLoading.value = false;
    else packageLoadingMore.value = false;
  }
}

async function loadDocuments() {
  loadingError.value = '';
  auditLoadError.value = '';

  try {
    const [nextSummary, nextAttachments] = await Promise.all([
      fetchDocumentSummary(),
      fetchEvidenceAttachments(),
    ]);
    summary.value = nextSummary;
    attachments.value = nextAttachments;
  } catch (error) {
    loadingError.value = error instanceof Error ? `附件数据读取失败：${error.message}` : '附件数据读取失败';
    return;
  }

  if (packageSearchTimer === null) await loadPackagePage(true);

  if (!canViewAudit.value) {
    auditEvents.value = [];
    return;
  }

  try {
    auditEvents.value = await fetchDocumentAuditEvents();
  } catch (error) {
    auditEvents.value = [];
    auditLoadError.value = error instanceof Error ? error.message : '审计数据读取失败';
  }
}

function selectFile(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  uploadError.value = '';
}

async function uploadEvidence() {
  if (!activePackage.value || !selectedFile.value) {
    uploadError.value = '请选择文档包和附件文件';
    return;
  }

  uploading.value = true;
  uploadResult.value = null;
  uploadError.value = '';

  try {
    const result = await uploadEvidenceAttachment({
      documentCode: activePackage.value.code,
      businessType: activePackage.value.category,
      businessCode: activePackage.value.businessCode,
      source: 'PC 文档工作台上传',
      owner: activePackage.value.owner,
      fallbackAction: '无',
      file: selectedFile.value,
    });
    uploadResult.value = result;
    await loadDocuments();
  } catch (error) {
    uploadError.value = error instanceof Error ? error.message : '上传附件失败';
  } finally {
    uploading.value = false;
  }
}

</script>

<template>
  <div class="page-stack document-page desk-page" :class="{ 'is-detail': detailOpen }">
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
          <span>覆盖率（已加载）</span>
          <strong>{{ evidenceCoverage }}%</strong>
        </article>
        <article>
          <span>缺口</span>
          <strong>{{ summary.missingEvidence }}</strong>
        </article>
      </div>
    </section>

    <p v-if="loadingError" class="operation-feedback danger" role="alert">{{ loadingError }}</p>
    <p v-else-if="packageLoading && packages.length === 0" class="empty-hint" role="status">正在加载文档包…</p>
    <p v-else-if="!packageLoadError && packages.length === 0 && hasPackageSearch" class="empty-hint">没有匹配的文档包。</p>
    <p v-else-if="!packageLoadError && packages.length === 0" class="empty-hint">暂无文档包，请从业务单据建立证据关联。</p>

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
            <small>仅统计当前已加载的文档包</small>
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

    <section class="document-upload-console" aria-label="附件上传与预览">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Upload & Preview</span>
          <h2>上传附件</h2>
          <p>选择文件后上传；在下方附件清单中直接预览或下载。</p>
        </div>
        <div class="button-row">
          <label>文档包<select v-model="selectedPackageCode" :disabled="uploading" aria-label="上传文档包"><option value="">当前待补文档包</option><option v-for="item in packageOptions" :key="item.code" :value="item.code">{{ item.code }} / {{ item.title }}</option></select></label>
          <label>附件文件<input type="file" aria-label="附件文件" :disabled="uploading" @change="selectFile" /></label>
          <button class="primary-button" type="button" :disabled="uploading || !activePackage || !selectedFile" @click="uploadEvidence">
            {{ uploading ? '正在上传' : '上传所选文件' }}
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
          <small>请检查文件和网络后重试。</small>
        </article>
      </div>
    </section>

    <section class="document-layout">
      <div class="document-master">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Package Ledger</span>
            <h2>文档包台账</h2>
            <p>每个业务动作都必须能关联文档包、附件、存储策略和审计事件。覆盖率和待补统计仅代表当前已加载范围。</p>
          </div>
        </div>
        <p v-if="packageLoadError" class="operation-feedback danger" role="alert">文档包读取失败：{{ packageLoadError }}</p>
        <p v-else-if="packageLoading && packages.length === 0" class="operation-feedback" role="status">正在加载文档包…</p>
        <label>
          <span>查找文档包</span>
          <input v-model="packageKeyword" aria-label="查找文档包" placeholder="单号、标题或负责人" />
        </label>
        <div class="document-table-wrap desk-table-wrap">
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
              <tr v-for="item in visiblePackages" :key="item.code" @click="selectPackage(item.code)">
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
        <div class="desk-cards">
          <button v-for="item in visiblePackages" :key="`card-${item.code}`" class="desk-card" type="button" @click="selectPackage(item.code)">
            <span class="desk-card-top">
              <strong class="code">{{ item.code }}</strong>
              <StatusBadge :label="item.status" :tone="item.tone" />
            </span>
            <span>{{ item.title }}</span>
            <span>{{ item.businessCode }} · {{ item.owner }}</span>
          </button>
        </div>
        <div class="document-package-pagination" aria-live="polite">
          <p v-if="packageLoadingMore" role="status">正在加载更多文档包…</p>
          <p v-else-if="packageLoading && packages.length > 0" role="status">正在刷新文档包…</p>
          <button v-if="packageHasMore" class="secondary-button" type="button" aria-label="加载更多文档包" :disabled="packageLoading || packageLoadingMore" @click="loadPackagePage(false)">
            {{ packageLoadingMore ? '正在加载更多文档包…' : '加载更多文档包' }}
          </button>
        </div>
      </div>

      <aside v-if="activePackage" class="document-detail module-side-panel">
        <div class="detail-title">
          <button class="secondary-button detail-back" type="button" @click="backToPackages">返回列表</button>
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
            <EvidencePreviewLink v-if="attachment.status !== '已下线'" :attachment-code="attachment.code" />
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
      <div class="document-table-wrap desk-table-wrap">
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
                  <EvidencePreviewLink v-if="item.status !== '已下线'" :attachment-code="item.code" />
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
      <div class="desk-cards">
        <article v-for="item in attachments" :key="`att-card-${item.code}`" class="desk-card production-handoff-card">
          <span class="desk-card-top">
            <strong class="code">{{ item.code }}</strong>
            <StatusBadge :label="item.status" :tone="item.tone" />
          </span>
          <span>{{ item.fileName }}</span>
          <span>{{ item.businessCode }} · {{ item.mediaType }}</span>
          <EvidencePreviewLink v-if="item.status !== '已下线'" :attachment-code="item.code" />
        </article>
      </div>
    </section>

    <section v-if="canViewAudit" class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>归档审计</h2>
            <p>上传、补传、归档、审批锁定都必须留下审计事件。</p>
          </div>
        </div>
        <p v-if="auditLoadError" class="operation-feedback danger" role="alert">审计数据读取失败：{{ auditLoadError }}</p>
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

<style scoped>
.document-upload-console label { min-width: 0; max-width: 100%; }
.document-upload-console select,
.document-upload-console input { box-sizing: border-box; min-width: 0; width: 100%; max-width: 100%; }
</style>
