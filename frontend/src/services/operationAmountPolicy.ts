/** 库存移动必须同时提交本次数量和本次重量，留空不沿用整批，也不按件数估算称重。 */
export const AMOUNT_PAIR_MESSAGE = '本次数量和本次重量必须同时填写';
export const AMOUNT_BLANK_MESSAGE = '请确认本次数量和本次重量；留空不会自动沿用整批重量';
export const AMOUNT_RECONFIRM_MESSAGE = '数量已改变，请重新确认本次重量，系统不会按件数估算称重';

export function confirmedQuantityAndWeight(extra: { quantity?: number | string; weight?: number | string }): {
  quantity: number;
  weight: string;
} {
  const quantityText = extra.quantity === undefined || extra.quantity === null ? '' : String(extra.quantity).trim();
  const weightText = extra.weight === undefined || extra.weight === null ? '' : String(extra.weight).trim();
  if ((quantityText === '') !== (weightText === '')) {
    throw new Error(AMOUNT_PAIR_MESSAGE);
  }
  if (quantityText === '') {
    throw new Error(AMOUNT_BLANK_MESSAGE);
  }
  return { quantity: Number(quantityText), weight: weightText };
}
