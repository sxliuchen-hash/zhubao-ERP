import { computed, onBeforeUnmount, ref } from 'vue';
import {
  queryScanContext,
  type ScanContextQuery,
  type ScanContextResponse,
  type ScanContextTarget,
  type ScanObjectType,
} from '../../services/scanContextApi';

/** 已经确认对象类型和单号的规范身份。 */
export interface CanonicalScanTarget extends ScanContextTarget {
  objectType: ScanObjectType;
  code: string;
}

/** 需要选择，或服务端没有给出规范身份时，不能拿去提交。 */
export function canonicalTarget(response: ScanContextResponse | null | undefined): CanonicalScanTarget | null {
  if (!response?.requiresSelection) {
    const target = response?.target;
    if (target?.objectType && target.code) {
      return { ...target, objectType: target.objectType, code: target.code };
    }
  }
  return null;
}

/**
 * 每次调用持有自己的查询代次。扫到新内容或离开页面后，旧响应不能写回。
 * 这里只查询，不写库存，也不保存过程记录。
 */
export function useScanContext() {
  const context = ref<ScanContextResponse | null>(null);
  const loading = ref(false);
  const error = ref('');
  const revision = ref(0);
  let disposed = false;

  function invalidate() {
    revision.value += 1;
    context.value = null;
    loading.value = false;
    error.value = '';
  }

  async function lookup(input: ScanContextQuery): Promise<ScanContextResponse | null> {
    const content = input.content?.trim() ?? '';
    if (!content) {
      invalidate();
      error.value = '请先输入或扫码';
      return null;
    }
    const token = revision.value + 1;
    revision.value = token;
    context.value = null;
    error.value = '';
    loading.value = true;
    try {
      const result = await queryScanContext({
        content,
        ...(input.objectType ? { objectType: input.objectType } : {}),
        ...(input.intent ? { intent: input.intent } : {}),
      });
      if (revision.value !== token || disposed) return null;
      context.value = result;
      return result;
    } catch (caught) {
      if (revision.value !== token || disposed) return null;
      context.value = null;
      error.value = caught instanceof Error ? caught.message : '查询失败';
      return null;
    } finally {
      if (revision.value === token && !disposed) loading.value = false;
    }
  }

  onBeforeUnmount(() => {
    disposed = true;
    revision.value += 1;
    loading.value = false;
  });

  const identity = computed(() => (loading.value ? null : canonicalTarget(context.value)));

  return { context, loading, error, revision, identity, lookup, invalidate };
}
