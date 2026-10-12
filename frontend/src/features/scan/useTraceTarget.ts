import { inject, ref, watch, type Ref } from 'vue';
import type { ScanIntent } from '../../services/scanContextApi';
import type { TraceTarget } from './traceTarget';

/** 只消费指定意图的追溯目标。同一个 token 不重复打开。 */
export function useTraceTarget(intents: ScanIntent[], open: (target: TraceTarget) => void) {
  const current = inject<Ref<TraceTarget | null>>('zhubao-trace-target', ref(null));
  const seen = ref(0);
  watch(current, (target) => {
    if (!target || !target.code.trim() || target.token === seen.value) return;
    if (!intents.includes(target.intent)) return;
    seen.value = target.token;
    open(target);
  }, { immediate: true });
}
