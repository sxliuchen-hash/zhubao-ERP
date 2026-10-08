import { computed, inject, ref, type Ref } from 'vue';

/** 顶栏追溯和本页关键字同时生效，空关键字不筛选。 */
export function useDeskFilter<T>(items: () => T[], textOf: (item: T) => string) {
  const traceQuery = inject<Ref<string>>('zhubao-trace-query', ref(''));
  const keyword = ref('');
  const filtered = computed(() => {
    const parts = [traceQuery.value, keyword.value]
      .map((value) => value.trim().toLowerCase())
      .filter(Boolean);
    return items().filter((item) => {
      const haystack = textOf(item).toLowerCase();
      return parts.every((part) => haystack.includes(part));
    });
  });
  return { keyword, filtered };
}
