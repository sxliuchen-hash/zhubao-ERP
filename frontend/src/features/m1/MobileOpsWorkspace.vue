<script setup lang="ts">
import { onMounted, ref } from 'vue';
import ScanResolver from '../scan/ScanResolver.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  fetchMobileWorkspace,
  type MobileWorkspaceModel,
} from '../../services/businessWorkspaceApi';

const emptyMobile = (): MobileWorkspaceModel => ({
  tasks: [],
  capabilities: [],
  offlineDrafts: [],
});

const workspace = ref<MobileWorkspaceModel>(emptyMobile());
const loadError = ref('');

onMounted(() => {
  void loadMobileWorkspace();
});

async function loadMobileWorkspace() {
  loadError.value = '';
  try {
    const loaded = await fetchMobileWorkspace();
    workspace.value = loaded;
    if (loaded.tasks.length === 0 && loaded.offlineDrafts.length === 0) {
      loadError.value = '接口没有返回现场任务';
    }
  } catch (error) {
    workspace.value = emptyMobile();
    loadError.value = error instanceof Error ? error.message : '现场任务加载失败';
  }
}
</script>

<template>
  <div class="page-stack">
    <section class="operations-hero">
      <div class="hero-copy">
        <span class="eyebrow">PWA Field Ops</span>
        <h2>移动现场作业台</h2>
        <p>移动端只服务扫码、报工、称重、质检和库存查询，复杂管理动作回到 PC 后台处理。</p>
      </div>
      <div class="hero-kpis">
        <article>
          <span>现场任务</span>
          <strong>{{ workspace.tasks.length }}</strong>
        </article>
        <article>
          <span>离线草稿</span>
          <strong>{{ workspace.offlineDrafts.length }}</strong>
        </article>
        <article>
          <span>能力项</span>
          <strong>{{ workspace.capabilities.length }}</strong>
        </article>
      </div>
    </section>

    <section class="toolbar-band">
      <div>
        <h2>PWA 移动作业端</h2>
        <p>一期移动端聚焦扫码、报工、质检和库存查询，不做完整管理后台。</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" type="button">离线草稿</button>
        <button class="primary-button" type="button">打开扫码</button>
      </div>
    </section>

    <section class="mobile-layout">
      <div class="phone-frame">
        <div class="phone-top">
          <strong>现场作业</strong>
          <span>班组 A / 李华</span>
        </div>
        <div class="phone-card">
          <small>当前扫码对象</small>
          <strong>{{ workspace.tasks[0]?.code || '暂无单据' }}</strong>
          <span>{{ workspace.tasks[0] ? `${workspace.tasks[0].scene} / ${workspace.tasks[0].nextAction}` : '暂无当前任务' }}</span>
        </div>
        <div class="phone-actions">
          <button type="button">报工</button>
          <button type="button">称重</button>
          <button type="button">质检</button>
          <button type="button">库存</button>
        </div>
        <div class="phone-form">
          <label>
            <span>现场要求</span>
            <input :value="workspace.tasks[0]?.required || ''" readonly />
          </label>
          <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
        </div>
      </div>

      <div class="mobile-workspace-stack">
        <div class="section-block">
          <div class="section-heading">
            <div>
              <h2>移动端任务校验</h2>
              <p>移动页面只保留现场必须信息，减少录入负担。</p>
            </div>
          </div>
          <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
          <p v-else-if="workspace.tasks.length === 0" class="empty-hint">暂无现场任务</p>
          <div class="mobile-task-list">
            <article v-for="task in workspace.tasks" :key="task.code">
              <div>
                <strong>{{ task.code }}</strong>
                <span>{{ task.scene }}</span>
              </div>
              <p>{{ task.nextAction }}</p>
              <small>{{ task.required }}</small>
              <StatusBadge :label="task.status" :tone="task.status.includes('阻断') ? 'red' : task.status.includes('质检') ? 'amber' : 'green'" />
            </article>
          </div>
        </div>

        <div class="split-layout mobile-subgrid">
          <div class="section-block">
            <div class="section-heading">
              <div>
                <h2>PWA 能力边界</h2>
                <p>先满足现场高频动作，权限、离线和附件策略逐步增强。</p>
              </div>
            </div>
            <div class="evidence-health">
              <article v-for="capability in workspace.capabilities" :key="capability.label">
                <div>
                  <strong>{{ capability.label }}</strong>
                  <span>{{ capability.detail }}</span>
                </div>
                <StatusBadge :label="capability.status" :tone="capability.tone" />
              </article>
            </div>
          </div>

          <div class="section-block">
            <div class="section-heading">
              <div>
                <h2>离线草稿</h2>
                <p>弱网场景不能丢数据，但恢复后必须复核状态。</p>
              </div>
            </div>
            <p v-if="workspace.offlineDrafts.length === 0" class="empty-hint">暂无离线草稿</p>
            <div class="draft-list">
              <article v-for="draft in workspace.offlineDrafts" :key="draft.code">
                <div>
                  <strong>{{ draft.code }}</strong>
                  <span>{{ draft.scene }} / {{ draft.savedAt }}</span>
                </div>
                <StatusBadge :label="draft.risk" :tone="draft.tone" />
              </article>
            </div>
          </div>
        </div>
      </div>
    </section>

    <ScanResolver />
  </div>
</template>
