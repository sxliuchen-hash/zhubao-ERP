<script setup lang="ts">
import { computed, inject, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { fetchProductionTarget, fetchBagDetail, fetchBagAvailability, splitActivityBag, startProductionTarget, weighActivityBag, type ProductionTargetView } from '../../services/productionQualityApi';
import BagReportForm from './BagReportForm.vue';
import PortionQualityForm from '../quality/PortionQualityForm.vue';
import { useBusinessCommand, hasPendingBusinessCommand } from '../workspace/useBusinessCommand';
const props = withDefaults(defineProps<{ targetCode: string; mode?: 'report' | 'quality' }>(), { mode: 'report' });
const emit = defineEmits<{ changed: []; locked: [value: boolean] }>();
const target = computed(() => props.targetCode); const session = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const view = ref<ProductionTargetView | null>(null); const message = ref(''); const bagId = ref(''); const stepsJson = ref(''); const weight = ref(''); const splitQuantity = ref(''); const childLocked = ref(false);
const command = useBusinessCommand(`bag-${props.mode}`, target, { bagId, stepsJson, weight, splitQuantity }, message);
const { pending, hasUnconfirmed } = command;
const locked = computed(() => pending.value || hasUnconfirmed.value || childLocked.value);
watch(locked, value => emit('locked', value), { immediate: true });
const selected = computed(() => view.value?.bags.find(row => row.bag.id === bagId.value));
const chosenSteps = computed<string[]>({ get: () => stepsJson.value ? JSON.parse(stepsJson.value) as string[] : [], set: value => { stepsJson.value = JSON.stringify(value); } });
const canStart = computed(() => session.value?.effectivePermissions?.includes('BUSINESS_MOTHER_SPLIT') === true);
const canWeigh = computed(() => session.value?.effectivePermissions?.includes('BUSINESS_PRODUCTION_REPORT') === true);
let sequence = 0;
watch(target, () => { view.value = null; void load(); }, { immediate: true });
async function load() {
  const read = ++sequence; const code = target.value;
  try {
    const saved = await fetchProductionTarget(code); if (read !== sequence) return;
    if (bagId.value && !saved.bags.some(row => row.bag.id === bagId.value) && hasPendingBusinessCommand(props.mode === 'report' ? 'bag-report' : 'portion-quality', bagId.value)) {
      const [detail, availability] = await Promise.all([fetchBagDetail(bagId.value), fetchBagAvailability(bagId.value)]);
      if (read !== sequence) return;
      saved.bags.unshift({ bag: detail.bag, availability, shippable: { bagId: detail.bag.id, quantity: 0, blockingReasons: ['原办理结果待核对，请恢复原请求重试'] } });
    }
    view.value = saved;
    if (!stepsJson.value) chosenSteps.value = saved.route?.stepIds ?? saved.defaultSteps.map(step => step.id);
    if (!saved.bags.some(row => row.bag.id === bagId.value)) bagId.value = saved.bags[0]?.bag.id ?? '';
  } catch (cause) { if (read === sequence) message.value = cause instanceof Error ? cause.message : '执行袋读取失败'; }
}
async function start() {
  const result = await command.submit('start', { steps: view.value?.defaultSteps.filter(step => chosenSteps.value.includes(step.id)).map(step => step.id) ?? [] }, (code, body) => startProductionTarget(code, body));
  if (result) { view.value = result; bagId.value = result.bags[0]?.bag.id ?? ''; message.value = '已按确认路线建立真实来源袋'; emit('changed'); }
}
async function weigh() {
  if (!selected.value || !weight.value.trim()) { message.value = '请填写本份额的实际重量'; return; }
  const original = command.originalPayload<{ bagId: string; bagVersion: number; weight: string }>('weigh');
  const result = await command.submit('weigh', { bagId: bagId.value, bagVersion: original?.bagVersion ?? selected.value.bag.version, weight: weight.value }, (_code, body) => weighActivityBag(body));
  if (result) { message.value = '本袋实重已保存；需要再称请开始下一笔'; await load(); emit('changed'); }
}
async function split() {
  if (!selected.value) return;
  const total = selected.value.bag.quantity; const first = Number(splitQuantity.value);
  if (!Number.isFinite(first) || first <= 0 || first >= total) { message.value = '分出数量必须大于0且小于本袋数量'; return; }
  const id = selected.value.bag.id;
  const result = await command.submit('split', { quantities: [first, Number((total - first).toFixed(3))] }, (_code, body) => splitActivityBag(id, body));
  if (result) { message.value = '已按实际份额分袋；各份需分别实称'; await load(); emit('changed'); }
}
async function changed() { await load(); emit('changed'); }
</script>
<template>
  <section aria-label="加工执行袋" class="page-stack">
    <h3>{{ mode === 'quality' ? '按袋质检与放行' : '按袋报工' }}</h3>
    <p v-if="message" role="status">{{ message }}</p><button type="button" :disabled="locked" @click="load">刷新本单执行袋</button>
    <template v-if="view">
      <p style="white-space:pre-wrap">加工要求：{{ view.requirement || '原单未记录' }}</p>
      <template v-if="!view.bags.length"><p>本单暂无可办理袋；已发货袋不会重复列入。</p>
        <details v-if="canStart && !view.hasExecution"><summary>确认路线并建袋</summary><p>按本单实际工序确认，建立后不覆盖原母单号、贴纸和照片。</p>
          <label v-for="step in view.defaultSteps" :key="step.id"><input v-model="chosenSteps" type="checkbox" :value="step.id" :disabled="locked" />{{ step.name }}</label>
          <button type="button" :disabled="locked" @click="start">确认路线并建立来源袋</button>
        </details><p v-else>{{ view.hasExecution ? '本单已有执行记录，暂无在厂可办理袋。' : '请业务岗先确认路线并建立来源袋。' }}</p>
      </template>
      <template v-else><label>实际执行袋<select v-model="bagId" :disabled="locked"><option v-for="row in view.bags" :key="row.bag.id" :value="row.bag.id">{{ row.bag.id }} · {{ row.bag.quantity }}{{ view.quantityUnit }} · {{ row.availability.stepName || '工序已处理' }}</option></select></label>
        <template v-if="selected"><p>来源 {{ selected.bag.sourceKey }} · 要求版本 {{ selected.bag.requirementVersion }}</p><p>可发 {{ selected.shippable.quantity }}{{ view.quantityUnit }} · {{ selected.shippable.blockingReasons.join('；') }}</p>
          <BagReportForm v-if="mode === 'report'" :key="bagId" :bag-id="bagId" @saved="changed" @locked="childLocked = $event" />
          <PortionQualityForm v-else :key="bagId" :bag-id="bagId" @changed="changed" @locked="childLocked = $event" />
          <details v-if="canWeigh"><summary>实称重量与分袋</summary>
            <p>当前重量 {{ selected.bag.weight.state === 'KNOWN' ? `${selected.bag.weight.value}克` : '待实称' }}；分袋后不按颗数分摊原重量。</p>
            <label>本袋实际重量（克）<input v-model="weight" inputmode="decimal" /></label><button type="button" :disabled="locked" @click="weigh">保存本袋实重</button>
            <label>分出一袋数量<input v-model="splitQuantity" inputmode="decimal" /></label><button type="button" :disabled="locked" @click="split">按实物分成两袋</button>
          </details>
        </template>
      </template>
      <button v-if="hasUnconfirmed" type="button" @click="command.restorePending">恢复上次填写后重试</button><button type="button" :disabled="locked" @click="command.startNext">开始下一笔袋办理</button>
    </template>
  </section>
</template>
