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
const yearMonth = ref('2026-10');
const statement = ref<CustomerStatement | null>(null);
const billing = ref<BillingRunResult | null>(null);
const mode = ref('WEIGHT');
const unitPrice = ref('10');
const processName = ref('雕刻');
const chargeUnit = ref('GRAM');

const receiptAmount = ref('');
const receiptDate = ref('2026-10-09');
const receiptLine = ref('');
const receiptAllocated = ref('');

const payrollLines = ref<PayrollLine[]>([]);
const consumables = ref<ConsumableStock[]>([]);
const payables = ref<PayableView[]>([]);
const operating = ref<OperatingStatement | null>(null);
const payableCode = ref('');
const payAmount = ref('');
const payDate = ref('2026-10-09');

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
    await saveConsumableItem({ code: 'GLUE', name: '胶', unitName: '支' });
    await purchaseConsumable({
      itemCode: 'GLUE',
      quantity: 10,
      amount: 30,
      supplier: '胶行',
      yearMonth: yearMonth.value.trim(),
    });
    consumables.value = await fetchConsumables();
    message.value = '已登记胶的采购';
  });
}

async function useConsumable() {
  await run(async () => {
    await issueConsumable({
      itemCode: 'GLUE',
      quantity: 1,
      customerId: customerId.value.trim() ? Number(customerId.value) : null,
      yearMonth: yearMonth.value.trim(),
    });
    consumables.value = await fetchConsumables();
    message.value = '领用已计入当月成本';
  });
}

async function addAsset() {
  await run(async () => {
    await saveFixedAsset({
      name: '抛光机',
      category: '设备',
      originalValue: 1000,
      residualValue: 0,
      startMonth: yearMonth.value.trim(),
      usefulMonths: 36,
    });
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
      supplier: '外协厂',
      amount: Number(payAmount.value || '0'),
      yearMonth: yearMonth.value.trim(),
      customerId: customerId.value.trim() ? Number(customerId.value) : null,
      motherOrderCode: '',
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
        <input v-model="yearMonth" aria-label="财务月份" />
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
      <table v-if="statement && statement.lines.length">
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
        <input v-model="receiptDate" aria-label="收款日期" />
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
      <table v-else>
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
    </section>

    <section v-else-if="section === 'consumable'" class="section-block">
      <div class="finance-actions">
        <button class="primary-button" type="button" :disabled="busy" @click="loadConsumables">查看结存</button>
        <button class="table-action" type="button" :disabled="busy" @click="addConsumable">登记胶采购</button>
        <button class="table-action" type="button" :disabled="busy" @click="useConsumable">领用 1 支</button>
      </div>
      <p v-if="consumables.length === 0" class="empty-hint">暂无消耗品</p>
      <table v-else>
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
    </section>

    <section v-else-if="section === 'asset'" class="section-block">
      <p>直线法折旧只进公司月度成本，不摊到客户。</p>
      <button class="primary-button" type="button" :disabled="busy" @click="addAsset">登记抛光机并计提本月折旧</button>
    </section>

    <section v-else-if="section === 'payable'" class="section-block">
      <div class="finance-actions">
        <button class="primary-button" type="button" :disabled="busy" @click="loadPayables">查看应付</button>
        <button class="table-action" type="button" :disabled="busy" @click="addOutsource">登记外协</button>
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
        <input v-model="payDate" aria-label="付款日期" />
      </label>
      <button class="table-action" type="button" :disabled="busy" @click="submitPayment">登记付款</button>
      <p v-if="payables.length === 0" class="empty-hint">暂无应付</p>
      <table v-else>
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
    </section>

    <section v-else class="section-block">
      <button class="primary-button" type="button" :disabled="busy" @click="loadOperating">查看经营结果</button>
      <p v-if="!operating" class="empty-hint">填写月份后查看。留空客户编号就是全公司。</p>
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
