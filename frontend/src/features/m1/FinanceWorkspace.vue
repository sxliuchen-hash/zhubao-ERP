<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ApiRequestError } from '../../services/apiClient';
import {
  confirmPayroll,
  fetchConsumables,
  fetchFinanceDashboard,
  fetchFinanceDetails,
  fetchOperatingStatement,
  fetchPayables,
  fetchStatement,
  generateBilling,
  generatePayroll,
  issueConsumable,
  payPayable,
  purchaseConsumable,
  recordReceipt,
  runDepreciation,
  saveConsumableItem,
  saveFixedAsset,
  saveOutsource,
  savePriceAgreement,
  saveWorkerRate,
  type BillingRunResult,
  type ConsumableStock,
  type CustomerStatement,
  type FinanceDashboard,
  type FinanceDetailKind,
  type FinanceDetailPage,
  type OperatingStatement,
  type PayableView,
  type PayrollLine,
} from '../../services/financeApi';
import { formatAmount } from '../../utils/formatAmount';

type Section = 'overview' | 'statement' | 'receipt' | 'payroll' | 'consumable' | 'asset' | 'payable' | 'operating';
type ReadStatus = 'loading' | 'ready' | 'error' | 'denied' | 'invalid';

const sections: Array<{ key: Section; label: string }> = [
  { key: 'overview', label: '经营总览' },
  { key: 'statement', label: '客户账单' },
  { key: 'receipt', label: '收款' },
  { key: 'payroll', label: '工资' },
  { key: 'consumable', label: '消耗品' },
  { key: 'asset', label: '固定资产' },
  { key: 'payable', label: '应付汇总' },
  { key: 'operating', label: '月度经营' },
];

const noCustomerLimitation = '授权范围内没有可归集客户';
const detailTitles: Record<FinanceDetailKind, string> = {
  REVENUE: '已入账加工收入明细',
  COST: '已记录成本明细',
  RECEIVABLE: '当前未收款明细',
  PAYABLE: '当前应付未付明细',
};
const sourceLabels: Record<string, string> = {
  RECEIVABLE: '加工费',
  PAYROLL: '工资',
  CONSUMABLE: '耗材领用',
  DEPRECIATION: '折旧',
  OUTSOURCE: '外协',
  PAYABLE: '应付',
};

const openTab = inject<((tab: 'inventory') => void) | undefined>('zhubao-open-tab', undefined);
const section = ref<Section>('overview');
const amountsHidden = ref(false);
const sectionLabel = computed(() => sections.find((item) => item.key === section.value)?.label ?? '财务');
const message = ref('');
const error = ref('');
const busy = ref(false);

const customerId = ref('');
const customerName = ref('');
const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' });
const yearMonth = ref(today.slice(0, 7));
const statement = ref<CustomerStatement | null>(null);
const billing = ref<BillingRunResult | null>(null);
const mode = ref('WEIGHT');
const unitPrice = ref('10');
const processName = ref('雕刻');
const chargeUnit = ref('GRAM');

const receiptAmount = ref('');
const receiptDate = ref(today);
const receiptLine = ref('');
const receiptAllocated = ref('');
const receiptKey = ref('');
const savedReceiptCode = ref('');
const receiptRefreshError = ref('');

watch([customerId, customerName, receiptAmount, receiptDate, receiptLine, receiptAllocated], () => {
  receiptKey.value = '';
  savedReceiptCode.value = '';
  receiptRefreshError.value = '';
});

function receiptIdempotencyKey() {
  if (!receiptKey.value) {
    const random = globalThis.crypto?.randomUUID?.();
    receiptKey.value = random ?? `rc-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
  return receiptKey.value;
}

const payrollLines = ref<PayrollLine[]>([]);
const consumables = ref<ConsumableStock[]>([]);
const payables = ref<PayableView[]>([]);
const operating = ref<OperatingStatement | null>(null);
const dashboard = ref<FinanceDashboard | null>(null);
const overviewStatus = ref<ReadStatus>('loading');
const overviewError = ref('');
const detailKind = ref<FinanceDetailKind | null>(null);
const detailCustomerOverride = ref<string | null>(null);
const detailPageNo = ref(1);
const detailPage = ref<FinanceDetailPage | null>(null);
const detailStatus = ref<ReadStatus>('loading');
const detailError = ref('');
const selectedMonth = ref('');
let overviewSeq = 0;
let detailSeq = 0;
const payableCode = ref('');
const payAmount = ref('');
const payDate = ref(today);
const paymentKey = ref('');

watch([payableCode, payAmount, payDate], () => {
  paymentKey.value = '';
});

function paymentIdempotencyKey() {
  if (!paymentKey.value) {
    const random = globalThis.crypto?.randomUUID?.();
    paymentKey.value = random ?? `pay-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
  return paymentKey.value;
}
const consumableForm = ref({ code: '', name: '', unitName: '', quantity: '', amount: '', supplier: '', issueQuantity: '' });
const assetForm = ref({ name: '', category: '', originalValue: '', residualValue: '0', startMonth: today.slice(0, 7), usefulMonths: '' });
const outsourceForm = ref({ supplier: '', amount: '', motherOrderCode: '' });

function requiredText(value: string, label: string) {
  if (!value.trim()) throw new Error(`请填写${label}`);
  return value.trim();
}

function requiredNumber(value: string | number, label: string, allowZero = false) {
  const number = Number(value);
  if (!String(value).trim() || !Number.isFinite(number) || (allowZero ? number < 0 : number <= 0)) {
    throw new Error(`${label}必须为${allowZero ? '非负' : '正'}数`);
  }
  return number;
}

function fail(reason: unknown) {
  error.value = reason instanceof Error ? reason.message : '请求失败';
}

async function run(action: () => Promise<void>) {
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    await action();
  } catch (reason) {
    fail(reason);
  } finally {
    busy.value = false;
  }
}

function requireCustomer() {
  if (!customerId.value.trim()) {
    throw new Error('请先填写客户编号');
  }
  return Number(customerId.value);
}

async function loadStatement() {
  await run(async () => {
    requireCustomer();
    statement.value = await fetchStatement(customerId.value.trim(), yearMonth.value.trim());
    if (statement.value.lines.length === 0) {
      message.value = '暂无账单行';
    }
  });
}

async function saveAgreement() {
  await run(async () => {
    const id = requireCustomer();
    const line = mode.value === 'PROCESS'
      ? { processName: processName.value, unit: chargeUnit.value, unitPrice: Number(unitPrice.value) }
      : { processName: '', unit: mode.value === 'WEIGHT' ? 'GRAM' : 'PIECE', unitPrice: Number(unitPrice.value) };
    await savePriceAgreement({
      customerId: id,
      customerName: customerName.value.trim() || `客户${id}`,
      mode: mode.value,
      effectiveFrom: yearMonth.value.trim(),
      effectiveTo: '',
      lines: [line],
    });
    message.value = '协议已保存，之后的入账使用这条单价';
  });
}

async function createBilling() {
  await run(async () => {
    billing.value = await generateBilling(yearMonth.value.trim(), customerId.value.trim());
    message.value = `新入账 ${billing.value.createdCount} 笔`;
    if (customerId.value.trim()) {
      statement.value = await fetchStatement(customerId.value.trim(), yearMonth.value.trim());
    }
  });
}

async function saveReceipt() {
  busy.value = true;
  error.value = '';
  message.value = '';
  receiptRefreshError.value = '';
  try {
    const id = requireCustomer();
    const allocations = receiptLine.value.trim()
      ? [{ receivableCode: receiptLine.value.trim(), amount: Number(receiptAllocated.value || receiptAmount.value) }]
      : [];
    const idempotencyKey = receiptIdempotencyKey();
    const receipt = await recordReceipt({
      customerId: id,
      customerName: customerName.value.trim() || statement.value?.customerName || `客户${id}`,
      receivedOn: receiptDate.value,
      amount: Number(receiptAmount.value),
      allocations,
      idempotencyKey,
    });
    savedReceiptCode.value = receipt.code;
    message.value = `收款已登记，单号 ${receipt.code}`;
    await refreshReceiptStatement(id);
  } catch (reason) {
    fail(reason);
  } finally {
    busy.value = false;
  }
}

async function refreshReceiptStatement(customer: number) {
  try {
    statement.value = await fetchStatement(String(customer), '');
    receiptRefreshError.value = '';
    receiptKey.value = '';
  } catch (reason) {
    const detail = reason instanceof Error ? reason.message : '查询失败';
    receiptRefreshError.value = `收款已登记，账单刷新失败：${detail}。请只重试查询，不要再次登记。`;
  }
}

async function retryReceiptQuery() {
  busy.value = true;
  error.value = '';
  try {
    await refreshReceiptStatement(requireCustomer());
  } catch (reason) {
    fail(reason);
  } finally {
    busy.value = false;
  }
}

async function buildPayroll(confirm: boolean) {
  await run(async () => {
    if (confirm) {
      await confirmPayroll(yearMonth.value.trim());
      message.value = '本月工资已锁定';
    }
    const result = confirm
      ? { lines: payrollLines.value, missingRates: [] }
      : await generatePayroll(yearMonth.value.trim());
    if (!confirm) {
      payrollLines.value = result.lines;
      message.value = result.missingRates.length
        ? `有 ${result.missingRates.length} 道工序还没有工人单价`
        : `已试算 ${result.lines.length} 行，尚未锁定`;
    }
  });
}

async function saveRate() {
  await run(async () => {
    await saveWorkerRate({
      processName: processName.value,
      unit: chargeUnit.value,
      unitPrice: Number(unitPrice.value),
      effectiveFrom: yearMonth.value.trim(),
      effectiveTo: '',
    });
    message.value = '工人单价已保存';
  });
}

async function loadConsumables() {
  await run(async () => {
    consumables.value = await fetchConsumables();
    if (consumables.value.length === 0) {
      message.value = '暂无消耗品';
    }
  });
}

async function addConsumable() {
  await run(async () => {
    const form = consumableForm.value;
    const itemCode = requiredText(form.code, '消耗品编码');
    const quantity = requiredNumber(form.quantity, '采购数量');
    const amount = requiredNumber(form.amount, '采购金额');
    const supplier = requiredText(form.supplier, '供应商');
    await saveConsumableItem({ code: itemCode, name: requiredText(form.name, '消耗品名称'), unitName: requiredText(form.unitName, '计量单位') });
    await purchaseConsumable({
      itemCode,
      quantity,
      amount,
      supplier,
      yearMonth: yearMonth.value.trim(),
    });
    consumables.value = await fetchConsumables();
    message.value = `已登记 ${form.name} 的采购`;
  });
}

async function useConsumable() {
  await run(async () => {
    await issueConsumable({
      itemCode: requiredText(consumableForm.value.code, '消耗品编码'),
      quantity: requiredNumber(consumableForm.value.issueQuantity, '领用数量'),
      customerId: customerId.value.trim() ? Number(customerId.value) : null,
      yearMonth: yearMonth.value.trim(),
    });
    consumables.value = await fetchConsumables();
    message.value = '领用已计入当月成本';
  });
}

async function addAsset() {
  await run(async () => {
    const form = assetForm.value;
    const originalValue = requiredNumber(form.originalValue, '资产原值');
    const residualValue = requiredNumber(form.residualValue, '预计残值', true);
    const usefulMonths = requiredNumber(form.usefulMonths, '使用月数');
    if (residualValue > originalValue) throw new Error('预计残值不能超过资产原值');
    if (!Number.isInteger(usefulMonths)) throw new Error('使用月数必须为正整数');
    await saveFixedAsset({
      name: requiredText(form.name, '资产名称'),
      category: requiredText(form.category, '资产类别'),
      originalValue,
      residualValue,
      startMonth: requiredText(form.startMonth, '开始折旧月份'),
      usefulMonths,
    });
    message.value = '固定资产已登记';
  });
}

async function depreciateAssets() {
  await run(async () => {
    const entries = await runDepreciation(yearMonth.value.trim());
    message.value = `已生成 ${entries.length} 条折旧`;
  });
}

async function loadPayables() {
  await run(async () => {
    payables.value = await fetchPayables(yearMonth.value.trim());
    if (payables.value.length === 0) {
      message.value = '暂无应付';
    }
  });
}

async function addOutsource() {
  await run(async () => {
    await saveOutsource({
      supplier: requiredText(outsourceForm.value.supplier, '外协供应商'),
      amount: requiredNumber(outsourceForm.value.amount, '外协金额'),
      yearMonth: yearMonth.value.trim(),
      customerId: customerId.value.trim() ? Number(customerId.value) : null,
      motherOrderCode: outsourceForm.value.motherOrderCode.trim(),
    });
    payables.value = await fetchPayables(yearMonth.value.trim());
    message.value = '外协应付已登记';
  });
}

async function submitPayment() {
  await run(async () => {
    const idempotencyKey = paymentIdempotencyKey();
    await payPayable({
      payableCode: payableCode.value.trim(),
      amount: Number(payAmount.value),
      paidOn: payDate.value,
      idempotencyKey,
    });
    payables.value = await fetchPayables(yearMonth.value.trim());
    paymentKey.value = '';
    message.value = '付款已登记';
  });
}

async function loadOperating() {
  await run(async () => {
    operating.value = await fetchOperatingStatement(yearMonth.value.trim(), customerId.value.trim());
  });
}

function asNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function normalizedCustomer(value: string): { ok: true; id: string } | { ok: false } {
  const trimmed = value.trim();
  if (!trimmed) return { ok: true, id: '' };
  if (!/^[1-9]\d{0,17}$/.test(trimmed)) return { ok: false };
  return { ok: true, id: trimmed };
}

function isDenied(reason: unknown): reason is ApiRequestError {
  return reason instanceof ApiRequestError && (reason.code === 40301 || reason.status === 403);
}

function formatAsOf(value: string | null | undefined): string {
  if (!value) return '';
  const text = value.replace('T', ' ');
  return text.length >= 16 ? text.slice(0, 16) : text;
}

function monthLabel(yearMonth: string): string {
  const month = Number(yearMonth.slice(5, 7));
  return month ? `${month}月` : yearMonth;
}

function unavailableReason(part: string): string {
  const item = dashboard.value?.capabilities?.find((entry) => entry.part === part);
  if (item && item.available === false) return item.reason || '这部分暂不可用';
  return '';
}

function displayAmount(value: number | null): string {
  if (amountsHidden.value) return '已隐藏';
  if (value === null) return '尚未返回';
  return formatAmount(value);
}

function amountClass(value: number | null): string {
  return value !== null && value < 0 ? 'finance-amount is-negative' : 'finance-amount';
}

const operatingStatement = computed(() => dashboard.value?.operating ?? null);
const provisional = computed(() => (operatingStatement.value?.gaps.length ?? 0) > 0);
const scopeKind = computed(() => dashboard.value?.scope?.kind ?? '');
const scopeText = computed(() => {
  const scope = dashboard.value?.scope;
  if (!scope) return '范围尚未返回';
  const kind = scope.kind === 'COMPANY' ? '全公司' : scope.kind === 'CUSTOMER' ? '单个客户' : scope.kind === 'CUSTOMERS' ? '授权客户' : scope.kind;
  return scope.note?.trim() ? `范围：${kind}。${scope.note.trim()}` : `范围：${kind}`;
});
const resultCaption = computed(() => {
  if (scopeKind.value === 'COMPANY') return '全公司已入账加工收入减去已记录成本';
  if (scopeKind.value === 'CUSTOMER' || scopeKind.value === 'CUSTOMERS') {
    return '当前范围已归集的经营结余。公司折旧和未归集成本不在内，不能相加当作全公司结余';
  }
  return '已入账加工收入减去已记录成本';
});
const balances = computed(() => dashboard.value?.currentBalances ?? null);
const balanceAsOf = computed(() => formatAsOf(balances.value?.asOf));
const noCustomers = computed(() => (balances.value?.limitation ?? '').trim() === noCustomerLimitation);
const periodPartial = computed(() => {
  const month = operatingStatement.value?.yearMonth;
  return dashboard.value?.trend?.some((point) => point.yearMonth === month && point.throughQueryTime) ?? false;
});

function balanceAmount(kind: 'outstanding' | 'unpaid'): { amount: number | null; text: string; note: string } {
  const blocked = unavailableReason('currentBalances');
  if (blocked) return { amount: null, text: blocked, note: '' };
  const current = balances.value;
  if (!current) return { amount: null, text: '尚未返回', note: '' };
  const limitation = current.limitation?.trim() ?? '';
  if (noCustomers.value) return { amount: null, text: limitation, note: '' };
  const value = asNumber(kind === 'outstanding' ? current.outstandingAmount : current.unpaidAmount);
  if (kind === 'unpaid' && limitation && (value === null || value === 0)) {
    return { amount: null, text: limitation, note: '' };
  }
  if (value === null) return { amount: null, text: '尚未返回', note: limitation };
  return { amount: value, text: displayAmount(value), note: limitation };
}

const revenueState = computed(() => {
  const blocked = unavailableReason('operating');
  if (blocked) return { amount: null as number | null, text: blocked };
  const value = asNumber(operatingStatement.value?.revenue);
  if (!operatingStatement.value || value === null) return { amount: null, text: '尚未返回' };
  return { amount: value, text: displayAmount(value) };
});
const resultState = computed(() => {
  const blocked = unavailableReason('operating');
  if (blocked) return { amount: null as number | null, text: blocked };
  const value = asNumber(operatingStatement.value?.result);
  if (!operatingStatement.value || value === null) return { amount: null, text: '尚未返回' };
  return { amount: value, text: displayAmount(value) };
});
const outstandingState = computed(() => balanceAmount('outstanding'));
const payableState = computed(() => balanceAmount('unpaid'));
const payableTitle = computed(() => (balances.value?.limitation?.trim() ? '本范围已归集应付' : '当前应付未付'));

const costRows = computed(() => {
  const current = operatingStatement.value;
  const rows = [
    { key: 'wage', label: '已确认工资', amount: asNumber(current?.wageCost) },
    { key: 'consumable', label: '耗材领用', amount: asNumber(current?.consumableCost) },
    { key: 'depreciation', label: '固定资产折旧', amount: asNumber(current?.depreciationCost) },
    { key: 'outsource', label: '外协成本', amount: asNumber(current?.outsourceCost) },
  ];
  const finite = rows.map((row) => row.amount).filter((amount): amount is number => amount !== null && amount > 0);
  const max = finite.length ? Math.max(...finite) : 0;
  const total = rows.every((row) => row.amount !== null)
    ? rows.reduce((sum, row) => sum + Math.round((row.amount ?? 0) * 100), 0) / 100
    : null;
  return {
    rows: rows.map((row) => ({
      ...row,
      width: amountsHidden.value || row.amount === null || row.amount <= 0 || max <= 0 ? '0%' : `${(row.amount / max) * 100}%`,
    })),
    total,
  };
});

interface TrendPointView {
  yearMonth: string;
  label: string;
  revenue: number | null;
  result: number | null;
  throughQueryTime: boolean;
}

const trendBlocked = computed(() => unavailableReason('trend'));
const trendPoints = computed((): TrendPointView[] => {
  const trend = dashboard.value?.trend;
  if (!Array.isArray(trend)) return [];
  return trend.map((point) => ({
    yearMonth: point.yearMonth,
    label: monthLabel(point.yearMonth),
    revenue: point.operating ? asNumber(point.operating.revenue) : null,
    result: point.operating ? asNumber(point.operating.result) : null,
    throughQueryTime: Boolean(point.throughQueryTime),
  }));
});

const lineChart = computed(() => {
  const points = trendPoints.value;
  const finite = points.flatMap((point) => [point.revenue, point.result]).filter((value): value is number => value !== null);
  if (!finite.length) return null;
  const rawMin = Math.min(0, ...finite);
  const rawMax = Math.max(0, ...finite);
  let min = rawMin;
  let max = rawMax;
  if (rawMax === rawMin) {
    if (rawMax === 0) {
      max = 1;
    } else {
      const pad = Math.abs(rawMax) * 0.2 || 1;
      min = rawMin - pad;
      max = rawMax + pad;
    }
  } else {
    const span = rawMax - rawMin;
    min = rawMin - span * 0.08;
    max = rawMax + span * 0.08;
  }
  const width = 640;
  const height = 220;
  const left = 78;
  const right = 12;
  const top = 16;
  const bottom = 28;
  const plotW = width - left - right;
  const plotH = height - top - bottom;
  const yOf = (value: number) => top + ((max - value) / (max - min)) * plotH;
  const xOf = (index: number) => (points.length <= 1 ? left + plotW / 2 : left + (index / (points.length - 1)) * plotW);
  const maxAbs = Math.max(Math.abs(rawMin), Math.abs(rawMax));
  const divisor = maxAbs >= 10000 ? 10000 : 1;
  const placed = points.map((point, index) => ({
    ...point,
    x: xOf(index),
    revenueY: point.revenue === null ? null : yOf(point.revenue),
    resultY: point.result === null ? null : yOf(point.result),
  }));
  return {
    width,
    height,
    left,
    right,
    unitLabel: divisor === 10000 ? '万元' : '元',
    ticks: [0, 1, 2, 3].map((step) => {
      const value = min + ((max - min) * step) / 3;
      return { y: yOf(value), label: (value / divisor).toFixed(2) };
    }),
    zeroY: yOf(0),
    placed,
    revenuePaths: chartPaths(placed, 'revenueY'),
    resultPaths: chartPaths(placed, 'resultY'),
  };
});

function chartPaths(
  points: Array<{ x: number; revenueY: number | null; resultY: number | null }>,
  key: 'revenueY' | 'resultY',
): string[] {
  const paths: string[] = [];
  let current: string[] = [];
  for (const point of points) {
    const y = point[key];
    if (y === null) {
      if (current.length) paths.push(current.join(' '));
      current = [];
      continue;
    }
    current.push(`${point.x.toFixed(1)},${y.toFixed(1)}`);
  }
  if (current.length) paths.push(current.join(' '));
  return paths;
}

const selectedPoint = computed(() => trendPoints.value.find((point) => point.yearMonth === selectedMonth.value) ?? null);
const pointReadout = computed(() => {
  const point = selectedPoint.value;
  if (!point) return '选择月份查看该月加工收入和经营结余';
  if (amountsHidden.value) return `${point.yearMonth} 金额已隐藏`;
  const partial = point.throughQueryTime ? '该月截至查询时刻，不与完整月份比较增幅。' : '';
  return `${point.yearMonth} 加工收入 ${displayAmount(point.revenue)}，经营结余 ${displayAmount(point.result)}。${partial}`;
});
const chartAria = computed(() => (amountsHidden.value ? '六个月加工收入与经营结余，金额已隐藏' : pointReadout.value));
const trendReason = computed(() => dashboard.value?.capabilities?.find((item) => item.part === 'trend' && item.available)?.reason ?? '');

function barHeight(value: number | null): string {
  if (amountsHidden.value || value === null || value <= 0) return '0px';
  const max = Math.max(...trendPoints.value.map((point) => (point.revenue !== null && point.revenue > 0 ? point.revenue : 0)));
  if (max <= 0) return '0px';
  return `${Math.round((value / max) * 96)}px`;
}

const rankingBlocked = computed(() => unavailableReason('receivableRanking'));
const ranking = computed(() => dashboard.value?.receivableRanking ?? null);
const rankingRows = computed(() => ranking.value?.rows ?? []);
const rankingMax = computed(() => {
  const amounts = rankingRows.value.map((row) => asNumber(row.outstandingAmount) ?? 0).filter((amount) => amount > 0);
  return amounts.length ? Math.max(...amounts) : 0;
});

function rankWidth(amount: number | null): string {
  if (amountsHidden.value || amount === null || amount <= 0 || rankingMax.value <= 0) return '0%';
  return `${(amount / rankingMax.value) * 100}%`;
}

const recordedCostText = computed(() => (costRows.value.total === null ? '尚未返回' : displayAmount(costRows.value.total)));

function metricAria(title: string, text: string, numeric: boolean): string {
  if (numeric && amountsHidden.value) return `${title}，金额已隐藏`;
  return `${title}，${text}`;
}

function pointAria(point: TrendPointView): string {
  if (amountsHidden.value) return `${point.yearMonth}，金额已隐藏`;
  return `${point.yearMonth}，加工收入 ${displayAmount(point.revenue)}，经营结余 ${displayAmount(point.result)}`;
}

async function loadOverview() {
  const seq = ++overviewSeq;
  const parsed = normalizedCustomer(customerId.value);
  if (!parsed.ok) {
    dashboard.value = null;
    overviewStatus.value = 'invalid';
    overviewError.value = '';
    return;
  }
  const month = yearMonth.value.trim();
  if (!/^\d{4}-\d{2}$/.test(month)) {
    dashboard.value = null;
    overviewStatus.value = 'error';
    overviewError.value = '月份不正确';
    return;
  }
  overviewStatus.value = 'loading';
  overviewError.value = '';
  dashboard.value = null;
  try {
    const data = await fetchFinanceDashboard(month, parsed.id);
    if (seq !== overviewSeq) return;
    if (!data) {
      overviewStatus.value = 'error';
      overviewError.value = '经营总览没有返回数据';
      return;
    }
    dashboard.value = data;
    overviewStatus.value = 'ready';
    const months = data.trend?.map((point) => point.yearMonth) ?? [];
    if (!months.includes(selectedMonth.value)) selectedMonth.value = months[months.length - 1] ?? '';
  } catch (reason) {
    if (seq !== overviewSeq) return;
    dashboard.value = null;
    if (isDenied(reason)) {
      overviewStatus.value = 'denied';
      overviewError.value = reason.message || '当前账号不能查看该范围的财务';
      return;
    }
    overviewStatus.value = 'error';
    overviewError.value = reason instanceof Error ? reason.message : '请求失败';
  }
}

function detailCustomerId(): string {
  return detailCustomerOverride.value ?? customerId.value;
}

async function loadDetail() {
  const seq = ++detailSeq;
  const kind = detailKind.value;
  if (!kind) return;
  const parsed = normalizedCustomer(detailCustomerId());
  if (!parsed.ok) {
    detailPage.value = null;
    detailStatus.value = 'invalid';
    detailError.value = '';
    return;
  }
  const month = yearMonth.value.trim();
  detailStatus.value = 'loading';
  detailError.value = '';
  detailPage.value = null;
  try {
    const page = await fetchFinanceDetails({
      kind,
      yearMonth: month,
      customerId: parsed.id,
      page: detailPageNo.value,
      size: 20,
    });
    if (seq !== detailSeq) return;
    if (!page) {
      detailStatus.value = 'error';
      detailError.value = '明细没有返回数据';
      return;
    }
    detailPage.value = page;
    detailPageNo.value = page.page || detailPageNo.value;
    detailStatus.value = 'ready';
  } catch (reason) {
    if (seq !== detailSeq) return;
    detailPage.value = null;
    if (isDenied(reason)) {
      detailStatus.value = 'denied';
      detailError.value = reason.message || '当前账号不能查看该明细';
      return;
    }
    detailStatus.value = 'error';
    detailError.value = reason instanceof Error ? reason.message : '请求失败';
  }
}

function openDetail(kind: FinanceDetailKind, customerOverride?: string) {
  detailKind.value = kind;
  detailCustomerOverride.value = customerOverride ?? null;
  detailPageNo.value = 1;
  void loadDetail();
}

function closeDetail() {
  detailSeq += 1;
  detailKind.value = null;
  detailPage.value = null;
  detailCustomerOverride.value = null;
  detailError.value = '';
  void loadOverview();
}

function changeDetailPage(page: number) {
  if (page < 1 || detailStatus.value === 'loading') return;
  detailPageNo.value = page;
  void loadDetail();
}

const detailTotalText = computed(() => {
  const amount = asNumber(detailPage.value?.totalAmount);
  if (amount === null) return '合计尚未返回';
  return amountsHidden.value ? '全部合计已隐藏' : `全部合计 ${formatAmount(amount)}`;
});
const canNextDetail = computed(() => {
  const page = detailPage.value;
  if (!page) return false;
  return page.page * page.size < page.total;
});
const activeDetailTitle = computed(() => (detailKind.value ? detailTitles[detailKind.value] : ''));

watch([yearMonth, customerId], () => {
  if (section.value !== 'overview') return;
  if (detailKind.value) {
    detailCustomerOverride.value = null;
    detailPageNo.value = 1;
    void loadDetail();
    return;
  }
  void loadOverview();
});

watch(section, (value, previous) => {
  if (previous === undefined) return;
  detailSeq += 1;
  detailKind.value = null;
  detailPage.value = null;
  detailCustomerOverride.value = null;
  if (value === 'overview') void loadOverview();
});

onMounted(() => {
  void loadOverview();
});

onBeforeUnmount(() => {
  overviewSeq += 1;
  detailSeq += 1;
});

function kindLabel(kind: string) {
  if (kind === 'PAYROLL') return '工资';
  if (kind === 'CONSUMABLE') return '消耗品采购';
  if (kind === 'OUTSOURCE') return '外协';
  return kind;
}
</script>

<template>
  <div class="finance-workspace">
    <header class="section-block finance-page-head">
      <div>
        <p class="finance-crumb">财务经营 / {{ sectionLabel }}</p>
        <h1>{{ sectionLabel }}</h1>
        <p v-if="section === 'overview'" class="finance-note">经营读取不生成账单、工资或折旧。来料实物仍在库存页，这里不算会计凭证。</p>
      </div>
      <button
        type="button"
        class="table-action finance-hide-amount"
        :aria-pressed="amountsHidden"
        :aria-label="amountsHidden ? '显示金额' : '隐藏金额'"
        @click="amountsHidden = !amountsHidden"
      >
        {{ amountsHidden ? '显示金额' : '隐藏金额' }}
      </button>
      <div class="finance-sections" aria-label="财务功能">
        <button
          v-for="item in sections"
          :key="item.key"
          type="button"
          class="table-action"
          :class="{ active: section === item.key }"
          @click="section = item.key"
        >
          {{ item.label }}
        </button>
      </div>
    </header>

    <section class="section-block finance-filters">
      <label>
        月份
        <input v-model="yearMonth" type="month" aria-label="财务月份" />
      </label>
      <label>
        客户编号
        <input v-model="customerId" aria-label="客户编号" />
      </label>
      <label>
        客户名称
        <input v-model="customerName" aria-label="客户名称" />
      </label>
    </section>

    <p v-if="error" class="operation-feedback danger">{{ error }}</p>
    <p v-else-if="message" class="operation-feedback">{{ message }}</p>

    <section v-if="section === 'overview' && detailKind" class="section-block finance-detail" :aria-label="activeDetailTitle">
      <button type="button" class="table-action finance-back" aria-label="返回经营总览" @click="closeDetail">返回经营总览</button>
      <div>
        <h2>{{ activeDetailTitle }}</h2>
        <p class="finance-note">{{ scopeText }}。筛选月份 {{ yearMonth }}<span v-if="detailCustomerOverride">，客户 {{ detailCustomerOverride }}</span><span v-else-if="customerId.trim()">，客户 {{ customerId.trim() }}</span>。</p>
        <p v-if="detailKind === 'RECEIVABLE' || detailKind === 'PAYABLE'" class="finance-note">未收和应付按当前时点，不按筛选月份改成该月小计。</p>
      </div>
      <p v-if="detailStatus === 'loading'" role="status">正在读取明细</p>
      <div v-else-if="detailStatus === 'denied'" role="alert" class="operation-feedback danger">
        <p>无权限</p>
        <p>{{ detailError }}</p>
        <button type="button" class="table-action finance-retry" @click="loadDetail">重新读取</button>
      </div>
      <div v-else-if="detailStatus === 'invalid'" role="alert" class="operation-feedback warning">
        <p>客户编号不正确</p>
        <p>请填写正整数客户编号，或留空查看当前账号可看的范围。</p>
      </div>
      <div v-else-if="detailStatus === 'error'" role="alert" class="operation-feedback danger">
        <p>读取失败</p>
        <p>{{ detailError }}</p>
        <button type="button" class="table-action finance-retry" @click="loadDetail">重试</button>
      </div>
      <template v-else-if="detailPage">
        <p>{{ detailTotalText }}，共 {{ detailPage.total }} 笔。这是筛选后的全量合计，不是本页相加。</p>
        <p v-if="detailPage.asOf" class="finance-note">截至 {{ formatAsOf(detailPage.asOf) }}</p>
        <p v-if="detailPage.total === 0" class="empty-hint">无明细记录</p>
        <div v-else class="erp-table-wrap finance-desktop-only" tabindex="0" aria-label="财务明细，可横向滚动">
          <table>
            <thead>
              <tr>
                <th>来源</th>
                <th>单号</th>
                <th>客户</th>
                <th>月份</th>
                <th>说明</th>
                <th class="amount">金额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in detailPage.rows" :key="`${row.sourceCode}-${index}`">
                <td>{{ sourceLabels[row.sourceType] || row.sourceType }}</td>
                <td class="code">{{ row.sourceCode }}</td>
                <td>{{ row.customerName || (row.customerId ? `客户 ${row.customerId}` : '—') }}</td>
                <td>{{ row.yearMonth || '—' }}</td>
                <td>{{ row.note || '—' }}</td>
                <td :class="amountClass(asNumber(row.amount))">{{ displayAmount(asNumber(row.amount)) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <ul v-if="detailPage.total > 0" class="finance-detail-cards finance-mobile-only">
          <li v-for="(row, index) in detailPage.rows" :key="`card-${row.sourceCode}-${index}`">
            <strong>{{ sourceLabels[row.sourceType] || row.sourceType }} {{ row.sourceCode }}</strong>
            <span>{{ row.customerName || (row.customerId ? `客户 ${row.customerId}` : '未归集客户') }}</span>
            <span>{{ row.yearMonth || '不按月份' }} {{ row.note }}</span>
            <span :class="amountClass(asNumber(row.amount))">{{ displayAmount(asNumber(row.amount)) }}</span>
          </li>
        </ul>
        <div v-if="detailPage.total > detailPage.size" class="finance-pager">
          <button type="button" class="table-action" :disabled="detailPage.page <= 1" @click="changeDetailPage(detailPage.page - 1)">上一页</button>
          <span>第 {{ detailPage.page }} 页</span>
          <button type="button" class="table-action" :disabled="!canNextDetail" @click="changeDetailPage(detailPage.page + 1)">下一页</button>
        </div>
      </template>
    </section>

    <section v-else-if="section === 'overview'" class="section-block finance-overview" aria-label="经营总览">
      <p v-if="overviewStatus === 'loading'" role="status">正在读取经营总览</p>
      <div v-else-if="overviewStatus === 'denied'" role="alert" class="operation-feedback danger">
        <p>无权限</p>
        <p>{{ overviewError }}</p>
        <button type="button" class="table-action finance-retry" @click="loadOverview">重新读取</button>
      </div>
      <div v-else-if="overviewStatus === 'invalid'" role="alert" class="operation-feedback warning">
        <p>客户编号不正确</p>
        <p>请填写正整数客户编号，或留空查看当前账号可看的范围。</p>
      </div>
      <div v-else-if="overviewStatus === 'error'" role="alert" class="operation-feedback danger">
        <p>读取失败</p>
        <p>{{ overviewError }}</p>
        <button type="button" class="table-action finance-retry" @click="loadOverview">重试</button>
      </div>
      <template v-else>
        <div class="finance-overview-meta">
          <p>{{ scopeText }}</p>
          <p v-if="dashboard?.generatedAt" class="finance-note">读取于 {{ formatAsOf(dashboard.generatedAt) }}</p>
        </div>
        <div class="finance-metric-grid">
          <button
            type="button"
            class="finance-metric-card is-primary"
            data-metric="revenue"
            :aria-label="metricAria('已入账加工收入', revenueState.text, revenueState.amount !== null)"
            @click="openDetail('REVENUE')"
          >
            <span>已入账加工收入</span>
            <strong :class="amountClass(amountsHidden ? null : revenueState.amount)">{{ revenueState.text }}</strong>
            <small>{{ operatingStatement?.yearMonth || yearMonth }} 入账月份<span v-if="periodPartial">，截至查询时刻</span></small>
          </button>
          <button
            type="button"
            class="finance-metric-card"
            data-metric="result"
            :aria-label="metricAria(provisional ? '经营结余，暂估' : '经营结余', resultState.text, resultState.amount !== null)"
            @click="openDetail('COST')"
          >
            <span>经营结余<span v-if="provisional" class="finance-provisional"> · 暂估</span></span>
            <strong :class="amountClass(amountsHidden ? null : resultState.amount)">{{ resultState.text }}</strong>
            <small>{{ resultCaption }}</small>
            <small v-if="provisional">{{ operatingStatement?.gaps.join('；') }}</small>
          </button>
          <button
            type="button"
            class="finance-metric-card"
            data-metric="outstanding"
            :aria-label="metricAria('当前未收款', outstandingState.text, outstandingState.amount !== null)"
            @click="openDetail('RECEIVABLE')"
          >
            <span>当前未收款</span>
            <strong :class="amountClass(amountsHidden ? null : outstandingState.amount)">{{ outstandingState.text }}</strong>
            <small>{{ balanceAsOf ? `截至 ${balanceAsOf}` : '截至时间尚未返回' }}</small>
          </button>
          <button
            type="button"
            class="finance-metric-card"
            data-metric="payable"
            :aria-label="metricAria(payableTitle, payableState.text, payableState.amount !== null)"
            @click="openDetail('PAYABLE')"
          >
            <span>{{ payableTitle }}</span>
            <strong :class="amountClass(amountsHidden ? null : payableState.amount)">{{ payableState.text }}</strong>
            <small>{{ balanceAsOf ? `截至 ${balanceAsOf}` : '截至时间尚未返回' }}</small>
            <small v-if="payableState.note">{{ payableState.note }}</small>
          </button>
        </div>

        <div class="finance-chart-grid">
          <article class="finance-panel" aria-label="收入与结余趋势">
            <h2>收入与结余趋势</h2>
            <p class="finance-legend"><span class="finance-swatch is-revenue"></span>加工收入 <span class="finance-swatch is-result"></span>经营结余 <span>纵轴：{{ amountsHidden ? '已隐藏' : (lineChart?.unitLabel || '元') }}，读数按元</span></p>
            <p v-if="trendBlocked" role="status">{{ trendBlocked }}</p>
            <p v-else-if="!dashboard?.trend" role="status">趋势尚未返回</p>
            <p v-else-if="trendPoints.length === 0" class="empty-hint">这段查询没有月份结果</p>
            <template v-else>
              <p v-if="amountsHidden" class="finance-hidden-chart">金额已隐藏</p>
              <svg
                v-else-if="lineChart"
                class="finance-line-chart finance-desktop-only"
                :viewBox="`0 0 ${lineChart.width} ${lineChart.height}`"
                role="img"
                :aria-label="chartAria"
              >
                <line
                  :x1="lineChart.left"
                  :x2="lineChart.width - lineChart.right"
                  :y1="lineChart.zeroY"
                  :y2="lineChart.zeroY"
                  stroke="var(--border)"
                />
                <g v-for="(tick, index) in lineChart.ticks" :key="index">
                  <line :x1="lineChart.left" :x2="lineChart.width - lineChart.right" :y1="tick.y" :y2="tick.y" stroke="var(--border)" />
                  <text :x="4" :y="tick.y + 4" fill="var(--ink-muted)" font-size="12">{{ tick.label }}</text>
                </g>
                <text :x="4" :y="14" fill="var(--ink-muted)" font-size="12">{{ lineChart.unitLabel }}</text>
                <polyline v-for="(path, index) in lineChart.revenuePaths" :key="`revenue-${index}`" :points="path" fill="none" stroke="var(--action)" stroke-width="2" />
                <polyline v-for="(path, index) in lineChart.resultPaths" :key="`result-${index}`" :points="path" fill="none" stroke="var(--success)" stroke-width="2" stroke-dasharray="5 4" />
                <template v-for="point in lineChart.placed" :key="point.yearMonth">
                  <circle v-if="point.revenueY !== null" :cx="point.x" :cy="point.revenueY" r="4" fill="var(--action)">
                    <title>{{ pointAria(point) }}</title>
                  </circle>
                  <circle v-if="point.resultY !== null" :cx="point.x" :cy="point.resultY" r="4" fill="var(--success)">
                    <title>{{ pointAria(point) }}</title>
                  </circle>
                  <text :x="point.x" :y="lineChart.height - 8" text-anchor="middle" fill="var(--ink-muted)" font-size="12">{{ point.label }}</text>
                </template>
              </svg>
              <div v-if="!amountsHidden" class="finance-bar-chart finance-mobile-only">
                <div class="finance-bar-plot">
                  <button
                    v-for="point in trendPoints"
                    :key="`bar-${point.yearMonth}`"
                    type="button"
                    class="finance-bar"
                    :aria-pressed="selectedMonth === point.yearMonth"
                    :aria-label="pointAria(point)"
                    @click="selectedMonth = point.yearMonth"
                  >
                    <span class="finance-bar-fill" :style="{ height: barHeight(point.revenue) }"></span>
                    <span>{{ point.label }}</span>
                  </button>
                </div>
              </div>
              <div class="finance-month-picker finance-desktop-only" role="group" aria-label="选择趋势月份">
                <button
                  v-for="point in trendPoints"
                  :key="point.yearMonth"
                  type="button"
                  class="finance-month-button"
                  :aria-pressed="selectedMonth === point.yearMonth"
                  :aria-label="pointAria(point)"
                  @click="selectedMonth = point.yearMonth"
                >
                  {{ point.label }}
                </button>
              </div>
              <p class="finance-point-readout" aria-live="polite">{{ pointReadout }}</p>
              <p v-if="trendReason" class="finance-note">{{ trendReason }}</p>
            </template>
          </article>
          <article class="finance-panel" aria-label="已记录成本构成">
            <h2>已记录成本构成</h2>
            <p v-if="unavailableReason('operating')" role="status">{{ unavailableReason('operating') }}</p>
            <template v-else>
              <button
                v-for="row in costRows.rows"
                :key="row.key"
                type="button"
                class="finance-cost-row"
                @click="openDetail('COST')"
              >
                <span>{{ row.label }}</span>
                <span :class="amountClass(amountsHidden ? null : row.amount)">{{ row.amount === null ? '尚未返回' : displayAmount(row.amount) }}</span>
                <span class="finance-cost-track" aria-hidden="true"><span :style="{ width: row.width }"></span></span>
              </button>
              <p class="finance-cost-total"><span>已记录成本合计</span><span :class="amountClass(amountsHidden ? null : costRows.total)">{{ recordedCostText }}</span></p>
              <p v-if="scopeKind === 'CUSTOMER' || scopeKind === 'CUSTOMERS'" class="finance-note">公司折旧和未分配成本不计入此范围。</p>
            </template>
          </article>
        </div>

        <div class="finance-lower-grid">
          <article class="finance-panel" aria-label="客户未收款">
            <h2>客户未收款</h2>
            <p class="finance-note">{{ balanceAsOf ? `截至 ${balanceAsOf}` : '截至当前' }}。顺序按接口返回，总额以上方未收款为准。</p>
            <p v-if="rankingBlocked" role="status">{{ rankingBlocked }}</p>
            <p v-else-if="!ranking" role="status">客户未收排行尚未返回</p>
            <p v-else-if="noCustomers" role="status">{{ noCustomerLimitation }}</p>
            <p v-else-if="ranking.total === 0" class="empty-hint">当前没有未收款</p>
            <template v-else>
              <button
                v-for="row in rankingRows"
                :key="row.customerId"
                type="button"
                class="finance-rank-row"
                :data-customer-id="row.customerId"
                :aria-label="metricAria(row.customerName || `客户 ${row.customerId}`, displayAmount(asNumber(row.outstandingAmount)), asNumber(row.outstandingAmount) !== null)"
                @click="openDetail('RECEIVABLE', String(row.customerId))"
              >
                <span class="finance-rank-name">{{ row.customerName || `客户 ${row.customerId}` }}</span>
                <span :class="amountClass(asNumber(row.outstandingAmount))">{{ displayAmount(asNumber(row.outstandingAmount)) }}</span>
                <span class="finance-rank-track" aria-hidden="true"><span :style="{ width: rankWidth(asNumber(row.outstandingAmount)) }"></span></span>
              </button>
              <p v-if="ranking.truncated" class="finance-note">共 {{ ranking.total }} 名客户有未收，这里显示前 {{ ranking.returned }} 名。未显示部分不能用本页金额相加代替总额。</p>
            </template>
          </article>
          <article class="finance-panel" aria-label="账务核对">
            <h2>账务核对<span v-if="provisional" class="finance-provisional"> · 暂估</span></h2>
            <p v-if="!operatingStatement" role="status">经营结果尚未返回</p>
            <p v-else-if="operatingStatement.gaps.length === 0" class="empty-hint">本月没有缺项说明</p>
            <ul v-else class="finance-gap-list">
              <li v-for="gap in operatingStatement.gaps" :key="gap">{{ gap }}</li>
            </ul>
            <p v-if="balances?.limitation" class="finance-note">{{ balances.limitation }}</p>
            <p class="finance-note">客户来料数量和克重不计入这里的金额。</p>
          </article>
        </div>
      </template>
    </section>

    <section v-else-if="section === 'statement'" class="section-block">
      <div class="finance-actions">
        <button class="primary-button" type="button" :disabled="busy" @click="loadStatement">查询账单</button>
        <button class="table-action" type="button" :disabled="busy" @click="createBilling">生成本月账单</button>
        <button class="table-action" type="button" :disabled="busy" @click="saveAgreement">保存协议</button>
        <button class="table-action" type="button" @click="openTab?.('inventory')">看库存</button>
      </div>
      <label>
        计价方式
        <select v-model="mode" aria-label="计价方式">
          <option value="WEIGHT">按克</option>
          <option value="PIECE">按件</option>
          <option value="PROCESS">按工序</option>
        </select>
      </label>
      <label v-if="mode === 'PROCESS'">
        工序
        <select v-model="processName" aria-label="工序">
          <option>雕刻</option>
          <option>镶嵌</option>
          <option>抛光</option>
        </select>
      </label>
      <label v-if="mode === 'PROCESS'">
        单位
        <select v-model="chargeUnit" aria-label="工序单位">
          <option value="JOB">元/次</option>
          <option value="GRAM">元/克</option>
          <option value="PIECE">元/件</option>
        </select>
      </label>
      <label>
        单价
        <input v-model="unitPrice" aria-label="单价" />
      </label>
      <p v-if="statement" class="empty-hint">
        {{ statement.customerName }} 应收 {{ formatAmount(statement.receivableAmount) }} ，已收 {{ formatAmount(statement.receivedAmount) }} ，未收
        {{ formatAmount(statement.outstandingAmount) }} ，预收 {{ formatAmount(statement.prepaymentAmount) }} 。在库
        {{ statement.stockQuantity }} 件 / {{ statement.stockWeight }} 克。{{ statement.stockNote }}
      </p>
      <p v-if="statement && statement.lines.length === 0" class="empty-hint">暂无账单行</p>
      <div v-if="statement && statement.lines.length" class="erp-table-wrap" tabindex="0" aria-label="账单明细，可横向滚动">
      <table>
        <thead>
          <tr>
            <th>来源</th>
            <th>母单</th>
            <th>子单</th>
            <th>工序</th>
            <th class="amount">金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in statement.lines" :key="line.code">
            <td class="code">{{ line.sourceType }}</td>
            <td class="code">{{ line.motherOrderCode }}</td>
            <td class="code">{{ line.workOrderCode || '—' }}</td>
            <td>{{ line.processName || '—' }}</td>
            <td class="amount">{{ formatAmount(line.amount) }}</td>
          </tr>
        </tbody>
      </table>
      </div>
      <p v-if="billing && billing.unpriced.length === 0 && billing.createdCount === 0" class="empty-hint">没有新的可入账单</p>
      <ul v-if="billing && billing.unpriced.length">
        <li v-for="(item, index) in billing.unpriced" :key="index">
          {{ item.motherOrderCode || item.workOrderCode }} {{ item.processName }} {{ item.reason }}
        </li>
      </ul>
    </section>

    <section v-else-if="section === 'receipt'" class="section-block">
      <label>
        收款日期
        <input v-model="receiptDate" type="date" aria-label="收款日期" />
      </label>
      <label>
        金额
        <input v-model="receiptAmount" aria-label="收款金额" />
      </label>
      <label>
        核销账单号
        <input v-model="receiptLine" aria-label="核销账单号" placeholder="不填则全部记为预收" />
      </label>
      <label>
        核销金额
        <input v-model="receiptAllocated" aria-label="核销金额" />
      </label>
      <p v-if="savedReceiptCode">收款单号 {{ savedReceiptCode }}</p>
      <p v-if="receiptRefreshError" class="operation-feedback danger">{{ receiptRefreshError }}</p>
      <button v-if="receiptRefreshError" class="table-action" type="button" :disabled="busy" @click="retryReceiptQuery">重新查询账单</button>
      <button class="primary-button" type="button" :disabled="busy || !!receiptRefreshError" @click="saveReceipt">登记收款</button>
    </section>

    <section v-else-if="section === 'payroll'" class="section-block">
      <label>
        工序
        <select v-model="processName" aria-label="工资工序">
          <option>雕刻</option>
          <option>镶嵌</option>
          <option>抛光</option>
        </select>
      </label>
      <label>
        单位
        <select v-model="chargeUnit" aria-label="工资单位">
          <option value="JOB">元/次</option>
          <option value="PIECE">元/件</option>
          <option value="GRAM">元/克</option>
        </select>
      </label>
      <label>
        工人单价
        <input v-model="unitPrice" aria-label="工人单价" />
      </label>
      <div class="finance-actions">
        <button class="table-action" type="button" :disabled="busy" @click="saveRate">保存工人单价</button>
        <button class="primary-button" type="button" :disabled="busy" @click="buildPayroll(false)">试算工资</button>
        <button class="table-action" type="button" :disabled="busy" @click="buildPayroll(true)">确认并锁定</button>
      </div>
      <p v-if="payrollLines.length === 0" class="empty-hint">暂无工资行</p>
      <div v-else class="erp-table-wrap" tabindex="0" aria-label="工资明细，可横向滚动">
      <table>
        <thead>
          <tr>
            <th>工人</th>
            <th>单号</th>
            <th>工序</th>
            <th class="amount">计件</th>
            <th class="amount">扣损</th>
            <th class="amount">实得</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(line, index) in payrollLines" :key="`${line.orderCode}-${index}`">
            <td>{{ line.operatorName }}</td>
            <td class="code">{{ line.orderCode }}</td>
            <td>{{ line.processName }}</td>
            <td class="amount">{{ formatAmount(line.grossAmount) }}</td>
            <td class="amount">{{ formatAmount(line.lossDeduction) }}</td>
            <td class="amount">{{ formatAmount(line.netAmount) }}</td>
          </tr>
        </tbody>
      </table>
      </div>
    </section>

    <section v-else-if="section === 'consumable'" class="section-block">
      <form class="account-admin-grid" aria-label="消耗品采购登记" @submit.prevent="addConsumable">
        <label>消耗品编码<input v-model="consumableForm.code" aria-label="消耗品编码" required /></label>
        <label>消耗品名称<input v-model="consumableForm.name" aria-label="消耗品名称" required /></label>
        <label>计量单位<input v-model="consumableForm.unitName" aria-label="计量单位" required /></label>
        <label>采购数量<input v-model="consumableForm.quantity" aria-label="采购数量" type="number" min="0.001" step="any" required /></label>
        <label>采购金额<input v-model="consumableForm.amount" aria-label="采购金额" type="number" min="0.01" step="0.01" required /></label>
        <label>供应商<input v-model="consumableForm.supplier" aria-label="采购供应商" required /></label>
        <button class="table-action" type="submit" :disabled="busy">登记采购</button>
      </form>
      <form class="finance-actions" aria-label="消耗品领用登记" @submit.prevent="useConsumable">
        <label>领用数量<input v-model="consumableForm.issueQuantity" aria-label="领用数量" type="number" min="0.001" step="any" required /></label>
        <button class="table-action" type="submit" :disabled="busy">登记领用</button>
      </form>
      <div class="finance-actions">
        <button class="primary-button" type="button" :disabled="busy" @click="loadConsumables">查看结存</button>
      </div>
      <p v-if="consumables.length === 0" class="empty-hint">暂无消耗品</p>
      <div v-else class="erp-table-wrap" tabindex="0" aria-label="消耗品结存，可横向滚动">
      <table>
        <thead>
          <tr>
            <th>编码</th>
            <th>名称</th>
            <th>结存数量</th>
            <th class="amount">结存金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in consumables" :key="item.code">
            <td class="code">{{ item.code }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.onHandQuantity }} {{ item.unitName }}</td>
            <td class="amount">{{ formatAmount(item.onHandAmount) }}</td>
          </tr>
        </tbody>
      </table>
      </div>
    </section>

    <section v-else-if="section === 'asset'" class="section-block">
      <p>直线法折旧只进公司月度成本，不摊到客户。</p>
      <form class="account-admin-grid" aria-label="固定资产登记" @submit.prevent="addAsset">
        <label>资产名称<input v-model="assetForm.name" aria-label="资产名称" required /></label>
        <label>资产类别<input v-model="assetForm.category" aria-label="资产类别" required /></label>
        <label>资产原值<input v-model="assetForm.originalValue" aria-label="资产原值" type="number" min="0.01" step="0.01" required /></label>
        <label>预计残值<input v-model="assetForm.residualValue" aria-label="预计残值" type="number" min="0" step="0.01" required /></label>
        <label>开始折旧月份<input v-model="assetForm.startMonth" aria-label="开始折旧月份" type="month" required /></label>
        <label>使用月数<input v-model="assetForm.usefulMonths" aria-label="使用月数" type="number" min="1" step="1" required /></label>
        <button class="primary-button" type="submit" :disabled="busy">登记固定资产</button>
      </form>
      <button class="table-action" type="button" :disabled="busy" @click="depreciateAssets">计提本月折旧</button>
    </section>

    <section v-else-if="section === 'payable'" class="section-block">
      <form class="account-admin-grid" aria-label="外协登记" @submit.prevent="addOutsource">
        <label>外协供应商<input v-model="outsourceForm.supplier" aria-label="外协供应商" required /></label>
        <label>外协金额<input v-model="outsourceForm.amount" aria-label="外协金额" type="number" min="0.01" step="0.01" required /></label>
        <label>关联母单<input v-model="outsourceForm.motherOrderCode" aria-label="外协母单号" /></label>
        <button class="table-action" type="submit" :disabled="busy">登记外协</button>
      </form>
      <div class="finance-actions">
        <button class="primary-button" type="button" :disabled="busy" @click="loadPayables">查看应付</button>
      </div>
      <label>
        应付单号
        <input v-model="payableCode" aria-label="应付单号" />
      </label>
      <label>
        金额
        <input v-model="payAmount" aria-label="付款金额" />
      </label>
      <label>
        付款日期
        <input v-model="payDate" type="date" aria-label="付款日期" />
      </label>
      <button class="table-action" type="button" :disabled="busy" @click="submitPayment">登记付款</button>
      <p v-if="payables.length === 0" class="empty-hint">暂无应付</p>
      <div v-else class="erp-table-wrap" tabindex="0" aria-label="应付明细，可横向滚动">
      <table>
        <thead>
          <tr>
            <th>单号</th>
            <th>种类</th>
            <th>对象</th>
            <th class="amount">应付</th>
            <th class="amount">未付</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in payables" :key="item.code">
            <td class="code">{{ item.code }}</td>
            <td>{{ kindLabel(item.kind) }}</td>
            <td>{{ item.payee }}</td>
            <td class="amount">{{ formatAmount(item.amount) }}</td>
            <td class="amount">{{ formatAmount(item.unpaidAmount) }}</td>
          </tr>
        </tbody>
      </table>
      </div>
    </section>

    <section v-else-if="section === 'operating'" class="section-block">
      <button class="primary-button" type="button" :disabled="busy" @click="loadOperating">查看经营结果</button>
      <p v-if="!operating" class="empty-hint">填写月份后查看。查询范围由当前账号的财务权限与客户授权决定。</p>
      <div v-else>
        <p>
          {{ operating.yearMonth }} 收入 {{ formatAmount(operating.revenue) }} ，工资 {{ formatAmount(operating.wageCost) }} ，消耗
          {{ formatAmount(operating.consumableCost) }} ，折旧 {{ formatAmount(operating.depreciationCost) }} ，外协 {{ formatAmount(operating.outsourceCost) }} ，结果
          {{ formatAmount(operating.result) }}
        </p>
        <p v-if="operating.gaps.length === 0" class="empty-hint">本月没有缺口说明</p>
        <ul v-else>
          <li v-for="gap in operating.gaps" :key="gap">{{ gap }}</li>
        </ul>
      </div>
    </section>
  </div>
</template>
