/**
 * 把金额显示成千分位、两位小数，并可带单位。
 * 只负责展示，不参与计价；单号不要走这个函数。
 */
export function formatAmount(value: number, unit = '元'): string {
  if (!Number.isFinite(value)) {
    return '—';
  }
  const negative = value < 0;
  const fixed = Math.abs(value).toFixed(2);
  const [integer, decimal] = fixed.split('.');
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const text = `${negative ? '-' : ''}${grouped}.${decimal}`;
  return unit ? `${text} ${unit}` : text;
}
