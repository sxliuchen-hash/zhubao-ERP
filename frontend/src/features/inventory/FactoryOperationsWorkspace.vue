<script setup lang="ts">
import { computed, ref } from 'vue';
import { apiGet, apiPost } from '../../services/apiClient';
import { useBusinessCommand } from '../workspace/useBusinessCommand';
interface Item { itemId: string; category: string; materialName: string; specCode: string; unit: string; consumptionTiming: string; custodyMode: string; version: number }
interface Balance { batchCode: string; warehouseCode: string; quantity: number; unit: string; weight: { state: string; value: number | null } }
interface Issue { issueId: string; batchCode: string; fromWarehouse: string; workshopCode: string; quantity: number; returnedQuantity: number; consumedQuantity: number; unit: string; version: number }
interface Loan { loanId: string; holderId: string; holderName: string; quantity: number; returnedQuantity: number; pieceCode: string; warehouseCode: string; unit: string; version: number }
interface Position { item: Item; balances: Balance[]; receipts: { receiptId: string; batchCode: string; warehouseCode: string }[]; issues: Issue[]; loans: Loan[]; pieces: { pieceCode: string; warehouseCode: string; holderId: string; scrapped: boolean }[]; toolQuantity: number | null }
interface Stocktake { stocktakeId: string; version: number; bookQuantity: number; countedQuantity: number | null; status: string }
const items = ref<Item[]>([]); const itemId = ref(''); const position = ref<Position | null>(null); const message = ref('');
const action = ref('register'); const name = ref(''); const spec = ref(''); const unit = ref(''); const category = ref('BEAD'); const timing = ref('UNDECIDED'); const custody = ref('UNDECIDED');
const warehouse = ref(''); const workshop = ref(''); const batch = ref(''); const issueId = ref(''); const receiptId = ref(''); const loanId = ref('');
const quantity = ref(''); const weight = ref(''); const amount = ref(''); const sourceKind = ref('PURCHASE'); const supplier = ref(''); const coveredCode = ref('');
const businessDate = ref(new Date().toLocaleDateString('sv-SE')); const target = ref(''); const holder = ref(''); const holderName = ref(''); const piece = ref(''); const reason = ref('');
const returned = ref('0'); const kept = ref('0'); const scrap = ref('0'); const stocktakeJson = ref('');
const requestPath = ref('');
const key = computed(() => itemId.value || 'new-factory-item');
const command = useBusinessCommand('factory-operation', key, { action, name, spec, unit, category, timing, custody, warehouse, workshop, batch, issueId, receiptId, loanId, quantity, weight, amount, sourceKind, supplier, coveredCode, businessDate, target, holder, holderName, piece, reason, returned, kept, scrap, stocktakeJson, requestPath }, message);
const { pending, hasUnconfirmed } = command;
const stocktake = computed<Stocktake | null>(() => stocktakeJson.value ? JSON.parse(stocktakeJson.value) as Stocktake : null);
const tool = computed(() => position.value?.item.category === 'REUSABLE_TOOL');
const consumable = computed(() => position.value?.item.category === 'CONSUMABLE');
const currentIssue = computed(() => position.value?.issues.find(row => row.issueId === issueId.value));
const currentLoan = computed(() => position.value?.loans.find(row => row.loanId === loanId.value));
async function list() { try { items.value = await apiGet<Item[]>('/api/inventory/factory-items'); } catch (cause) { message.value = cause instanceof Error ? cause.message : '档案读取失败'; } }
async function load() {
  const id = itemId.value; if (!id) { position.value = null; return; }
  try { const view = await apiGet<Position>(`/api/inventory/factory-items/${encodeURIComponent(id)}/inventory?warehouse=${encodeURIComponent(warehouse.value)}`); if (itemId.value === id) position.value = view; }
  catch (cause) { if (itemId.value === id) { position.value = null; message.value = cause instanceof Error ? cause.message : '库存读取失败'; } }
}
function select() { position.value = null; if (itemId.value && action.value === 'register') action.value = 'policy'; if (!itemId.value) action.value = 'register'; void load(); }
const nullable = (value: string) => value.trim() || null;
async function submit() {
  if (hasUnconfirmed.value) {
    command.restorePending();
    const original = command.originalPayload<Record<string, unknown>>(action.value);
    if (original) await save(requestPath.value, original);
    return;
  }
  let path = ''; let body: Record<string, unknown> = {}; const item = position.value?.item; const issue = currentIssue.value; const loan = currentLoan.value;
  switch (action.value) {
    case 'register': path = '/api/inventory/factory-items'; body = { category: category.value, materialName: name.value, specCode: spec.value, unit: unit.value, consumptionTiming: timing.value, custodyMode: custody.value }; break;
    case 'policy': if (!item) return; path = `/api/inventory/factory-items/${encodeURIComponent(item.itemId)}/policy`; body = { version: item.version, materialName: item.materialName, consumptionTiming: timing.value, custodyMode: custody.value }; break;
    case 'receive': if (!item) return; path = '/api/inventory/company-material-receipts'; body = { itemId: item.itemId, quantity: quantity.value, weight: nullable(weight.value), unit: item.unit, warehouseCode: warehouse.value, bizDate: businessDate.value, sourceKind: sourceKind.value, supplierName: supplier.value, amount: nullable(amount.value), coveredPayableCode: coveredCode.value }; break;
    case 'issue': if (!item) return;
      path = consumable.value ? '/api/inventory/consumable-issues' : '/api/inventory/company-material-issues';
      body = consumable.value ? { itemId: item.itemId, receiptId: receiptId.value, workshopCode: workshop.value, quantity: quantity.value } : { itemId: item.itemId, batchCode: batch.value, fromWarehouse: warehouse.value, workshopCode: workshop.value, quantity: quantity.value, weight: nullable(weight.value), unit: item.unit, targetCode: target.value }; break;
    case 'return': if (!issue) { message.value = '请按原领用选择'; return; }
      path = `/api/inventory/${consumable.value ? 'consumable-issues' : 'company-material-issues'}/${encodeURIComponent(issue.issueId)}/returns`; body = { version: issue.version, quantity: quantity.value, weight: nullable(weight.value), unit: issue.unit }; break;
    case 'consume': if (!issue) { message.value = '请按原领用选择'; return; }
      path = `/api/inventory/${consumable.value ? 'consumable-issues' : 'company-material-issues'}/${encodeURIComponent(issue.issueId)}/consumptions`;
      body = consumable.value ? { version: issue.version, quantity: quantity.value } : { version: issue.version, consumed: quantity.value, returned: returned.value, kept: kept.value, scrap: scrap.value, amount: nullable(amount.value), targetCode: target.value }; break;
    case 'tool-in': if (!item) return; path = `/api/inventory/factory-items/${encodeURIComponent(item.itemId)}/tool-placements`; body = { warehouseCode: warehouse.value, quantity: quantity.value, pieceCode: piece.value }; break;
    case 'borrow': if (!item) return; path = '/api/inventory/tool-loans'; body = { itemId: item.itemId, warehouseCode: warehouse.value, holderId: holder.value, holderName: holderName.value, quantity: quantity.value, pieceCode: piece.value }; break;
    case 'tool-return': case 'tool-scrap': if (!loan) { message.value = '请按原借出选择'; return; }
      path = `/api/inventory/tool-loans/${encodeURIComponent(loan.loanId)}/${action.value === 'tool-return' ? 'returns' : 'scraps'}`; body = { version: loan.version, quantity: quantity.value, reason: reason.value }; break;
    case 'count-open': if (!item) return; path = '/api/inventory/stocktakes'; body = { itemId: item.itemId, batchCode: tool.value ? '' : batch.value, locationCode: warehouse.value, countedAt: 'NOW', tool: tool.value }; break;
    case 'count-record': case 'count-confirm': if (!stocktake.value) { message.value = '请先建立本次盘点快照'; return; }
      path = `/api/inventory/stocktakes/${encodeURIComponent(stocktake.value.stocktakeId)}/${action.value === 'count-record' ? 'counts' : 'confirmations'}`;
      body = { version: stocktake.value.version, countedQuantity: quantity.value }; break;
    default: return;
  }
  requestPath.value = path;
  await save(path, body);
}
async function save(path: string, body: Record<string, unknown>) {
  const result = await command.submit(action.value, body, (_code, request) => apiPost<Record<string, unknown>, Record<string, unknown>>(path, request));
  if (!result) return;
  if (action.value.startsWith('count-')) stocktakeJson.value = JSON.stringify(result);
  message.value = '本次办理已完成；同类下一笔请先点击“开始下一笔”';
  if (action.value === 'register') { await list(); } else await load();
}
void list();
</script>
<template>
  <section aria-label="本厂物品与库存办理" class="page-stack">
    <h3>本厂珠料、配件、耗材与工具</h3>
    <label>物品<select v-model="itemId" @change="select"><option value="">新建物品档案</option><option v-for="item in items" :key="item.itemId" :value="item.itemId">{{ item.materialName }} · {{ item.specCode }} · {{ item.unit }}</option></select></label>
    <button type="button" @click="list">刷新物品</button>
    <label>办理<select v-model="action"><option v-if="!itemId" value="register">新建档案</option><template v-else><option value="policy">确认管理方式</option><template v-if="!tool"><option value="receive">入库</option><option value="issue">领用到车间</option><option value="return">按原领用退回</option><option value="consume">确认实际耗用</option></template><template v-else><option value="tool-in">工具入库</option><option value="borrow">借出工具</option><option value="tool-return">按原借出归还</option><option value="tool-scrap">确认工具报废</option></template><option value="count-open">发起现在盘点</option><option value="count-record">填写本次实盘</option><option value="count-confirm">确认盘点差额</option></template></select></label>
    <template v-if="!itemId"><label>类别<select v-model="category"><option value="BEAD">珠料</option><option value="ACCESSORY">配件</option><option value="CONSUMABLE">耗材</option><option value="REUSABLE_TOOL">工具</option></select></label><label>品名<input v-model="name" /></label><label>规格<input v-model="spec" /></label><label>单位<input v-model="unit" /></label></template>
    <template v-if="action === 'register' || action === 'policy'"><label>耗材计耗<select v-model="timing"><option value="UNDECIDED">未选定</option><option value="ON_ISSUE">领用即耗</option><option value="ON_ACTUAL_USE">实际耗用</option></select></label><label>工具保管<select v-model="custody"><option value="UNDECIDED">未选定</option><option value="QUANTITY">按数量</option><option value="PIECE">逐件编号</option></select></label></template>
    <template v-if="itemId"><label>库位<input v-model="warehouse" /></label><button type="button" @click="load">读取当前库存</button>
      <template v-if="position"><p>管理方式：{{ ({ UNDECIDED: '未选定', ON_ISSUE: '领用即耗', ON_ACTUAL_USE: '实际耗用' } as Record<string,string>)[position.item.consumptionTiming] }} · {{ ({ UNDECIDED: '未选定', QUANTITY: '按数量', PIECE: '逐件编号' } as Record<string,string>)[position.item.custodyMode] }}</p><p v-for="row in position.balances" :key="`${row.batchCode}:${row.warehouseCode}`">库内 {{ row.batchCode }} · {{ row.warehouseCode }} · {{ row.quantity }}{{ row.unit }} · {{ row.weight.state === 'KNOWN' ? `${row.weight.value}克` : '重量待核' }}</p><p v-for="row in position.issues" :key="row.issueId">车间 {{ row.workshopCode }} · 领用 {{ row.issueId }} · 留存 {{ row.quantity - row.returnedQuantity - row.consumedQuantity }}{{ row.unit }}</p><p v-if="position.toolQuantity != null">所选库位可借 {{ position.toolQuantity }}{{ position.item.unit }}</p><p v-for="row in position.loans" :key="row.loanId">未还 {{ row.holderId }} {{ row.holderName }} · {{ row.quantity - row.returnedQuantity }}{{ row.unit }} · {{ row.loanId }}</p><p v-for="row in position.pieces" :key="row.pieceCode">工具 {{ row.pieceCode }} · {{ row.warehouseCode }} · {{ row.scrapped ? '已报废' : row.holderId ? `保管人 ${row.holderId}` : '在库' }}</p></template>
    </template>
    <template v-if="['receive','issue','return','consume','tool-in','borrow','tool-return','tool-scrap','count-record'].includes(action)"><label>{{ action === 'consume' ? '本次耗用数量' : action === 'count-record' ? '本次实盘数量' : '本次数量' }}<input v-model="quantity" inputmode="decimal" /></label></template>
    <template v-if="['receive','issue','return'].includes(action) && !tool && !consumable"><label>本次实重（克）<input v-model="weight" placeholder="未知留空" /></label></template>
    <template v-if="action === 'receive'"><label>入库日期<input v-model="businessDate" type="date" /></label><label>来源<select v-model="sourceKind"><option value="PURCHASE">采购新增</option><option value="OPENING">期初接续</option><option value="COVERED_PAYABLE">已有应付覆盖</option></select></label><label>供应商<input v-model="supplier" /></label><label>本次采购金额<input v-model="amount" placeholder="未知留空" /></label><label>已有应付编号<input v-model="coveredCode" /></label></template>
    <template v-if="['issue','count-open'].includes(action) && !tool"><label>来源批次<select v-model="batch"><option value="">请选择</option><option v-for="row in position?.balances" :key="`${row.batchCode}:${row.warehouseCode}`" :value="row.batchCode">{{ row.batchCode }} · {{ row.warehouseCode }} · {{ row.quantity }}{{ row.unit }}</option></select></label></template>
    <template v-if="action === 'issue'"><label>车间<input v-model="workshop" /></label><label v-if="consumable">原入库单<select v-model="receiptId"><option value="">请选择</option><option v-for="row in position?.receipts" :key="row.receiptId" :value="row.receiptId">{{ row.receiptId }} · {{ row.batchCode }} · {{ row.warehouseCode }}</option></select></label><label v-else>加工去向<input v-model="target" /></label></template>
    <template v-if="['return','consume'].includes(action)"><label>原领用<select v-model="issueId"><option value="">请选择</option><option v-for="row in position?.issues" :key="row.issueId" :value="row.issueId">{{ row.issueId }} · {{ row.workshopCode }} · 未结 {{ row.quantity - row.returnedQuantity - row.consumedQuantity }}{{ row.unit }}</option></select></label></template>
    <template v-if="action === 'consume' && !consumable"><label>同时退回<input v-model="returned" /></label><label>继续留下<input v-model="kept" /></label><label>其中报废<input v-model="scrap" /></label><label>本次耗用金额<input v-model="amount" placeholder="未知留空" /></label><label>耗用去向<input v-model="target" /></label></template>
    <template v-if="['tool-in','borrow'].includes(action)"><label>逐件工具编号<input v-model="piece" placeholder="按数量管理时留空" /></label></template>
    <template v-if="action === 'borrow'"><label>保管人编号<input v-model="holder" /></label><label>保管人姓名<input v-model="holderName" /></label></template>
    <template v-if="['tool-return','tool-scrap'].includes(action)"><label>原借出<select v-model="loanId"><option value="">请选择</option><option v-for="row in position?.loans" :key="row.loanId" :value="row.loanId">{{ row.loanId }} · {{ row.holderName }} · 未还 {{ row.quantity - row.returnedQuantity }}{{ row.unit }}</option></select></label><label v-if="action === 'tool-scrap'">报废原因<input v-model="reason" /></label></template>
    <p v-if="stocktake">本次盘点 {{ stocktake.stocktakeId }} · 原账面 {{ stocktake.bookQuantity }} · 实盘 {{ stocktake.countedQuantity ?? '待填' }} · 版本 {{ stocktake.version }}</p>
    <p v-if="message" role="status">{{ message }}</p><button type="button" :disabled="pending" @click="submit">{{ pending ? '正在提交' : hasUnconfirmed ? '原请求重试' : '提交本次办理' }}</button>
    <button v-if="hasUnconfirmed" type="button" @click="command.restorePending">恢复上次填写</button><button type="button" :disabled="pending || hasUnconfirmed" @click="command.startNext">开始下一笔</button>
  </section>
</template>
