<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { changeBagFreeze, fetchBagDetail, fetchShippablePortion, submitPortionInspection, submitPortionRelease, submitPortionRework } from '../../services/productionQualityApi';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
const props = defineProps<{ bagId: string }>(); const emit = defineEmits<{ changed: []; locked: [value: boolean] }>();
const quantity = ref(''); const reasons = ref<string[]>([]); const clauses = ref(''); const pass = ref(''); const rework = ref('0'); const scrap = ref('0'); const held = ref('0');
const message = ref(''); const reworkBagId = ref(''); const version = ref(''); const requirement = ref(''); const releaseQuantity = ref(''); const round = ref('1');
const frozen = ref(0); const freezeQuantity = ref('');
const bagKey = computed(() => props.bagId);
const command = useBusinessCommand('portion-quality', bagKey, { clauses, pass, rework, scrap, held, version, requirement, releaseQuantity, round, freezeQuantity }, message);
const { pending, hasUnconfirmed } = command; let sequence = 0;
watch(() => pending.value || hasUnconfirmed.value, value => emit('locked', value), { immediate: true });
watch(bagKey, () => { quantity.value = ''; reasons.value = []; reworkBagId.value = ''; void load(); }, { immediate: true });
async function load() {
  const read = ++sequence; const id = props.bagId;
  try {
    const [detail, view] = await Promise.all([fetchBagDetail(id), fetchShippablePortion(id)]); if (read !== sequence) return;
    quantity.value = String(view.quantity); reasons.value = view.blockingReasons; frozen.value = detail.hold.frozen;
    if (!hasUnconfirmed.value && !pending.value) {
      version.value = String(detail.bag.version); requirement.value = detail.bag.requirementVersion;
      if (!releaseQuantity.value) releaseQuantity.value = String(Math.max(0, detail.inspections.reduce((sum, row) => sum + row.pass, 0) - detail.hold.released));
    }
  } catch (cause) { if (read === sequence) reasons.value = [cause instanceof Error ? cause.message : '可发条件读取失败']; }
}
async function inspect() {
  if (!version.value || !requirement.value) { message.value = '请先读取本袋真实版本'; return; }
  const result = await command.submit('inspect', { bagId: props.bagId, bagVersion: Number(version.value), requirementVersion: requirement.value, pass: Number(pass.value || 0), rework: Number(rework.value || 0), scrap: Number(scrap.value || 0), pending: Number(held.value || 0), failedClauses: clauses.value.split('、').map(item => item.trim()).filter(Boolean) }, (_id, body) => submitPortionInspection(body));
  if (result) { message.value = '本次质检已记录'; releaseQuantity.value = pass.value; emit('changed'); await load(); }
}
async function release() {
  const result = await command.submit('release', { bagId: props.bagId, bagVersion: Number(version.value), quantity: Number(releaseQuantity.value) }, (_id, body) => submitPortionRelease(body));
  if (result) { message.value = '已按本次合格份额放行'; emit('changed'); await load(); }
}
async function freeze(action: 'freezes' | 'unfreezes') {
  const result = await command.submit(action, { bagId: props.bagId, bagVersion: Number(version.value), quantity: Number(freezeQuantity.value) }, (_id, body) => changeBagFreeze(action, body));
  if (result) { message.value = action === 'freezes' ? '本次份额已冻结' : '已解除指定冻结，合格数量没有增加'; emit('changed'); await load(); }
}
async function openRework() {
  const result = await command.submit('rework', { bagId: props.bagId, bagVersion: Number(version.value), round: Number(round.value) }, (_id, body) => submitPortionRework(body));
  if (result) { reworkBagId.value = result.reworkBagId; message.value = '返工份额已独立开袋，计酬仍待核'; emit('changed'); }
}
</script>
<template>
  <section aria-label="按袋质检">
    <p>本袋可发 {{ quantity || '待核' }} · 要求版本 {{ requirement || '待读取' }}</p>
    <p v-for="reason in reasons" :key="reason">{{ reason }}</p>
    <p>需分次检验、返工或发货时，可先按实际份额分袋，再分别质检与实称。</p>
    <label>条款<input v-model="clauses" aria-label="检查条款" placeholder="用顿号分开" /></label>
    <label>合格数量<input v-model="pass" aria-label="合格数量" inputmode="decimal" /></label>
    <label>返工数量<input v-model="rework" aria-label="返工数量" inputmode="decimal" /></label>
    <label>报废数量<input v-model="scrap" inputmode="decimal" /></label><label>待核数量<input v-model="held" inputmode="decimal" /></label>
    <button type="button" :disabled="pending || !version" @click="inspect">提交本次质检</button>
    <label>本次放行数量<input v-model="releaseQuantity" inputmode="decimal" /></label>
    <button type="button" :disabled="pending || !version" @click="release">放行合格部分</button>
    <p>本袋冻结 {{ frozen }}</p><label>本次冻结或解除数量<input v-model="freezeQuantity" inputmode="decimal" /></label><button type="button" :disabled="pending || !version" @click="freeze('freezes')">冻结指定份额</button><button type="button" :disabled="pending || !version" @click="freeze('unfreezes')">复核后解除指定冻结</button>
    <label>返工轮次<input v-model="round" inputmode="numeric" /></label><button type="button" :disabled="pending || !version" @click="openRework">开出返工份额</button>
    <p v-if="reworkBagId">返工去向 {{ reworkBagId }}</p><p v-if="message" role="status">{{ message }}</p>
    <button v-if="hasUnconfirmed" type="button" @click="command.restorePending">恢复上次填写后重试</button>
    <button type="button" :disabled="pending || hasUnconfirmed" @click="command.startNext">开始下一笔质检办理</button>
  </section>
</template>
