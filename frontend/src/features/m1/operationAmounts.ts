import { computed, ref, watch } from 'vue';
import type { BusinessOperationContext } from '../../types/businessOperationContext';
import {
  AMOUNT_BLANK_MESSAGE,
  AMOUNT_PAIR_MESSAGE,
  AMOUNT_RECONFIRM_MESSAGE,
} from '../../services/operationAmountPolicy';

export interface VisibleAmounts {
  quantity: string;
  weight: string;
}

/** 整批数量和重量只作为输入框里看得见的默认值。 */
export function visibleAmounts(context?: BusinessOperationContext | null): VisibleAmounts {
  if (context == null || context.quantity == null || context.weight == null || String(context.weight).trim() === '') {
    return { quantity: '', weight: '' };
  }
  return { quantity: String(context.quantity), weight: String(context.weight) };
}

export function operationAmountError(
  values: Record<string, string>,
  defaults: VisibleAmounts,
  weightReconfirmed: boolean,
): string {
  const quantity = values.quantity?.trim() ?? '';
  const weight = values.weight?.trim() ?? '';
  if ((quantity === '') !== (weight === '')) return AMOUNT_PAIR_MESSAGE;
  if (quantity === '') return AMOUNT_BLANK_MESSAGE;
  if (defaults.quantity !== '' && quantity !== defaults.quantity && !weightReconfirmed) return AMOUNT_RECONFIRM_MESSAGE;
  return '';
}

/** 切换单据时填入可见的整批数量重量；改数量后必须重新确认重量。 */
export function createAmountForm() {
  const operationValues = ref<Record<string, string>>({});
  const defaults = ref<VisibleAmounts>({ quantity: '', weight: '' });
  const weightReconfirmed = ref(true);

  function resetAmounts(context?: BusinessOperationContext | null) {
    const next = visibleAmounts(context);
    defaults.value = next;
    weightReconfirmed.value = true;
    operationValues.value = { ...next };
  }

  watch(() => operationValues.value.quantity, (quantity) => {
    const current = (quantity ?? '').trim();
    if (defaults.value.quantity !== '' && current !== defaults.value.quantity) {
      weightReconfirmed.value = false;
      return;
    }
    if ((operationValues.value.weight ?? '').trim() === defaults.value.weight) {
      weightReconfirmed.value = true;
    }
  });

  watch(() => operationValues.value.weight, (weight, previous) => {
    if (previous === undefined) return;
    if ((weight ?? '') !== (previous ?? '')) weightReconfirmed.value = true;
  });

  const amountHint = computed(() => {
    const message = operationAmountError(operationValues.value, defaults.value, weightReconfirmed.value);
    return message === AMOUNT_BLANK_MESSAGE ? '' : message;
  });
  const amountDefaultNote = computed(() => (
    defaults.value.quantity
      ? `已填入整批 ${defaults.value.quantity} 件、${defaults.value.weight} 克。修改数量后请重新确认重量。`
      : ''
  ));

  function assertAmounts() {
    const message = operationAmountError(operationValues.value, defaults.value, weightReconfirmed.value);
    if (message) throw new Error(message);
  }

  return { operationValues, resetAmounts, assertAmounts, amountHint, amountDefaultNote };
}
