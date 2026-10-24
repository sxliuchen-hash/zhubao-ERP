<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchShortcuts, saveShortcut, removeShortcut, type RequirementShortcut } from '../../services/masterDataApi';
const props = defineProps<{ canManage: boolean }>();
const items = ref<RequirementShortcut[]>([]);
const phrase = ref('');
const saving = ref(false);
const error = ref('');
onMounted(async () => { try { items.value = await fetchShortcuts(); } catch { error.value = '快捷词读取失败'; } });
async function save() {
  if (!props.canManage || saving.value || !phrase.value.trim()) return;
  saving.value = true;
  try { const saved = await saveShortcut(phrase.value.trim()); items.value = [...items.value.filter(item => item.shortcutId !== saved.shortcutId), saved]; phrase.value = ''; error.value = ''; }
  catch (caught) { error.value = caught instanceof Error ? caught.message : '快捷词保存失败'; }
  finally { saving.value = false; }
}
async function remove(item: RequirementShortcut) {
  if (!props.canManage || saving.value) return;
  saving.value = true;
  try { await removeShortcut(item.shortcutId); items.value = items.value.filter(row => row.shortcutId !== item.shortcutId); error.value = ''; }
  catch (caught) { error.value = caught instanceof Error ? caught.message : '快捷词删除失败'; }
  finally { saving.value = false; }
}
</script>
<template>
  <section aria-label="公共快捷词管理">
    <h3>常用加工要求快捷词</h3>
    <p v-if="error" role="alert">{{ error }}</p>
    <ul><li v-for="item in items" :key="item.shortcutId">{{ item.phrase }} <button v-if="canManage" type="button" :disabled="saving" :aria-label="`删除快捷词 ${item.phrase}`" @click="remove(item)">删除</button></li></ul>
    <form v-if="canManage" @submit.prevent="save"><label>新增公共快捷词<input v-model="phrase" aria-label="新快捷词" maxlength="40" /></label><button type="submit" :disabled="saving">添加快捷词</button></form>
  </section>
</template>
