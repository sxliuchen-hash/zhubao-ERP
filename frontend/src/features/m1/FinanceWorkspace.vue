<script setup lang="ts">
import { inject, ref } from 'vue';
import {
  confirmPayroll,
  fetchConsumables,
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
  type OperatingStatement,
  type PayableView,
  type PayrollLine,
} from '../../services/financeApi';
import { formatAmount } from '../../utils/formatAmount';

type Section = 'statement' | 'receipt' | 'payroll' | 'consumable' | 'asset' | 'payable' | 'operating';

const sections: Array<{ key: Section; label: string }> = [
  { key: 'statement', label: '客户账单' },
  { key: 'receipt', label: '收款' },
  { key: 'payroll', label: '工资' },
  { key: 'consumable', label: '消耗品' },
  { key: 'asset', label: '固定资产' },
  { key: 'payable', label: '应付汇总' },
  { key: 'operating', label: '月度经营' },
];

const openTab = inject<((tab: 'inventory') => void) | undefined>('zhubao-open-tab', undefined);
const section = ref<Section>('statement');
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

const payrollLines = ref<PayrollLine[]>([]);
const consumables = ref<ConsumableStock[]>([]);
const payables = ref<PayableView[]>([]);
const operating = ref<OperatingStatement | null>(null);
const payableCode = ref('');
const payAmount = ref('');
const payDate = ref(today);
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
  await run(async () => {
    const id = requireCustomer();
    const allocations = receiptLine.value.trim()
      ? [{ receivableCode: receiptLine.value.trim(), amount: Number(receiptAllocated.value || receiptAmount.value) }]
      : [];
    await recordReceipt({
      customerId: id,
      customerName: customerName.value.trim() || statement.value?.customerName || `客户${id}`,
      receivedOn: receiptDate.value,
      amount: Number(receiptAmount.value),
      allocations,
    });
    message.value = '收款已登记';
    statement.value = await fetchStatement(String(id), '');
  });
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
    await payPayable({
      payableCode: payableCode.value.trim(),
      amount: Number(payAmount.value),
      paidOn: payDate.value,
    });
    payables.value = await fetchPayables(yearMonth.value.trim());
    message.value = '付款已登记';
  });
}

async function loadOperating() {
  await run(async () => {
    operating.value = await fetchOperatingStatement(yearMonth.value.trim(), customerId.value.trim());
  });
}

function kindLabel(kind: string) {
  if (kind === 'PAYROLL') return '工资';
  if (kind === 'CONSUMABLE') return '消耗品采购';
  if (kind === 'OUTSOURCE') return '外协';
  return kind;
}
</script>

<template>
  <div class="finance-workspace">
    <header class="section-block">
      <p>加工费按客户协议入账。来料实物仍在库存页。这里不算会计凭证。</p>
      <div class="finance-sections">
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

    <section v-if="section === 'statement'" class="section-block">
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
      <button class="primary-button" type="button" :disabled="busy" @click="saveReceipt">登记收款</button>
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

    <section v-else class="section-block">
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
