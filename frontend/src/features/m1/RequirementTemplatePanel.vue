<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { disableTemplate, fetchTemplates, saveTemplate, type RequirementTemplate } from '../../services/masterDataApi';
import { nextRequestAttempt, type RequestAttempt } from '../field/stableRequest';

const props = defineProps<{ canManage: boolean }>();

const templates = ref<RequirementTemplate[]>([]);
const error = ref('');
const hint = ref('');
const saving = ref(false);
const name = ref('');
const stepsText = ref('开粗\n车形（车成珠型）\n抛光');
const sortOrder = ref('1');
const editing = ref<RequirementTemplate | null>(null);
const attempt = ref<RequestAttempt | null>(null);

onMounted(() => { void load(); });

async function load() {
  error.value = '';
  try {
    templates.value = await fetchTemplates(props.canManage);
  } catch (caught) {
    templates.value = [];
    error.value = caught instanceof Error ? caught.message : '模板加载失败';
  }
}

function edit(item: RequirementTemplate) {
  editing.value = item;
  name.value = item.name;
  stepsText.value = item.steps.join('\n');
  sortOrder.value = String(item.sortOrder);
  hint.value = '';
  error.value = '';
}

function reset() {
  editing.value = null;
  name.value = '';
  stepsText.value = '';
  sortOrder.value = String((templates.value.at(-1)?.sortOrder ?? 0) + 1);
}

async function submit() {
  if (saving.value) return;
  const steps = stepsText.value.split(/\n+/).map((step) => step.trim()).filter(Boolean);
  const fingerprint = [editing.value?.templateCode ?? '', name.value.trim(), steps.join('|'), sortOrder.value].join('#');
  const next = nextRequestAttempt(attempt.value, fingerprint, 'tpl');
  attempt.value = next;
  saving.value = true;
  error.value = '';
  hint.value = '';
  try {
    const saved = await saveTemplate({
      templateCode: editing.value?.templateCode ?? '',
      name: name.value.trim(),
      steps,
      sortOrder: Number(sortOrder.value) || 0,
      version: editing.value?.version ?? 0,
      requestId: editing.value ? undefined : next.requestId,
    });
    hint.value = editing.value ? `已更新「${saved.name}」` : `已新增「${saved.name}」`;
    attempt.value = null;
    reset();
    await load();
  } catch (caught) {
    const message = caught instanceof Error ? caught.message : '模板保存失败';
    error.value = message;
    if (message.includes('已被其他人修改')) {
      const code = editing.value?.templateCode;
      await load();
      const fresh = templates.value.find((item) => item.templateCode === code);
      if (fresh && editing.value) {
        editing.value = fresh;
        name.value = fresh.name;
        stepsText.value = fresh.steps.join('\n');
        sortOrder.value = String(fresh.sortOrder);
      }
      error.value = '模板已被其他人修改，已重新载入最新内容，请再改一次';
    }
  } finally {
    saving.value = false;
  }
}

async function disable(item: RequirementTemplate) {
  if (saving.value) return;
  saving.value = true;
  error.value = '';
  hint.value = '';
  try {
    const saved = await disableTemplate(item.templateCode, item.version);
    hint.value = saved.enabled ? '模板仍在启用' : `「${saved.name}」已停用，已建母单的要求不会改变`;
    await load();
  } catch (caught) {
    const message = caught instanceof Error ? caught.message : '停用失败';
    await load();
    error.value = message.includes('已被其他人修改')
      ? '模板已被其他人修改，请刷新后再停用'
      : message;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="section-block" aria-label="加工要求模板">
    <h2>加工要求模板</h2>
    <p class="archive-note">模板只生成母单上的文字要求。选择开粗、车形不会新增报工工序、岗位或计费项目。停用后新单不再提供，已经保存的要求保持原文。</p>
    <p v-if="error" class="operation-feedback danger">{{ error }}</p>
    <p v-if="hint" class="operation-feedback">{{ hint }}</p>
    <article v-for="item in templates" :key="item.templateCode" class="archive-row">
      <strong>{{ item.name }}</strong>
      <span>{{ item.enabled ? '启用' : '已停用' }} · 版本 {{ item.version }}</span>
      <span>{{ item.steps.join(' → ') }}</span>
      <button v-if="canManage" type="button" class="secondary-button" @click="edit(item)">编辑</button>
      <button v-if="canManage && item.enabled" type="button" class="secondary-button" @click="disable(item)">停用</button>
    </article>
    <p v-if="!templates.length && !error" class="empty-hint">还没有加工要求模板</p>
    <form v-if="canManage" class="archive-form" @submit.prevent="submit">
      <h3>{{ editing ? `编辑 ${editing.name}` : '新建模板' }}</h3>
      <label><span>名称</span><input v-model="name" aria-label="模板名称" /></label>
      <label><span>有序步骤，一行一项</span><textarea v-model="stepsText" aria-label="模板步骤" /></label>
      <label><span>排序</span><input v-model="sortOrder" aria-label="模板排序" /></label>
      <button class="primary-button" type="submit" :disabled="saving">{{ saving ? '正在保存' : '保存模板' }}</button>
      <button v-if="editing" class="secondary-button" type="button" @click="reset">取消编辑</button>
    </form>
  </section>
</template>
