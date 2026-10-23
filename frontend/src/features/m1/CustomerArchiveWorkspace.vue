<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue';
import type { AuthSession } from '../../services/authApi';
import { fetchStatement, type CustomerStatement } from '../../services/financeApi';
import { canManageMasterData } from '../../services/masterDataAccess';
import {
  fetchCustomers,
  fetchMaterials,
  saveCustomer,
  saveMaterial,
  type CustomerProfile,
  type MaterialProfile,
} from '../../services/masterDataApi';
import { fetchProgress, type ProgressRow } from '../../services/progressApi';
import RequirementTemplatePanel from './RequirementTemplatePanel.vue';
import { progressTraceTarget, type TraceTarget } from '../scan/traceTarget';

type Section = 'customers' | 'materials' | 'templates';

const authSession = inject<Ref<AuthSession | null>>('zhubao-auth-session', ref(null));
const openOrder = inject<((target: Pick<TraceTarget, 'objectType' | 'code'>) => void) | null>('zhubao-open-order', null);
const canManage = computed(() => authSession.value?.effectivePermissions ? authSession.value.effectivePermissions.includes('MASTER_DATA_MANAGE') : canManageMasterData(authSession.value));
const canReadStatement = computed(() => !authSession.value?.effectivePermissions || authSession.value.effectivePermissions.includes('FINANCE_CUSTOMER_READ'));
const section = ref<Section>('customers');
const loading = ref(false);
const loadError = ref('');
const saving = ref(false);
const saveError = ref('');
const saveHint = ref('');

const customers = ref<CustomerProfile[]>([]);
const materials = ref<MaterialProfile[]>([]);
const customerQuery = ref('');
const materialQuery = ref('');
const editingCustomer = ref(false);
const customerEditorOpen = ref(false);
const customerFact = ref<'progress' | 'statement'>('progress');
const editingMaterial = ref(false);
const selectedCustomerId = ref<number | null>(null);
const selectedMaterialCode = ref('');
const customerForm = ref(emptyCustomerForm());
const materialForm = ref(emptyMaterialForm());

const yearMonth = ref(currentMonth());
const statement = ref<CustomerStatement | null>(null);
const statementError = ref('');
const statementLoading = ref(false);
const progressRows = ref<ProgressRow[]>([]);
const progressError = ref('');
const progressLoading = ref(false);
let disposed = false;
let statementGeneration = 0;
let progressGeneration = 0;
let catalogGeneration = 0;
onBeforeUnmount(() => { disposed = true; ++statementGeneration; ++progressGeneration; ++catalogGeneration; });
function clearCustomerFacts() {
  ++statementGeneration; ++progressGeneration;
  statement.value = null; statementError.value = ''; statementLoading.value = false;
  progressRows.value = []; progressError.value = ''; progressLoading.value = false;
}
watch(yearMonth, () => { ++statementGeneration; statement.value = null; statementError.value = ''; statementLoading.value = false; }, { flush: 'sync' });
watch(canReadStatement, allowed => { if (!allowed) { ++statementGeneration; statement.value = null; statementError.value = ''; statementLoading.value = false; } });

const visibleCustomers = computed(() => {
  const keyword = customerQuery.value.trim().toLowerCase();
  return customers.value.filter((item) => !keyword
    || `${item.customerCode} ${item.name} ${item.contact} ${item.phone}`.toLowerCase().includes(keyword));
});
const visibleMaterials = computed(() => {
  const keyword = materialQuery.value.trim().toLowerCase();
  return materials.value.filter((item) => !keyword
    || `${item.materialCode} ${item.name} ${item.aliases}`.toLowerCase().includes(keyword));
});

onMounted(() => {
  void loadCatalog();
});

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function emptyCustomerForm() {
  return { name: '', contact: '', phone: '', remark: '', enabled: true, customerCode: '' };
}

function emptyMaterialForm() {
  return { name: '', aliases: '', remark: '', enabled: true };
}

async function loadCatalog() {
  const token = ++catalogGeneration;
  loading.value = true;
  loadError.value = '';
  try {
    const [customerRows, materialRows] = await Promise.all([fetchCustomers(), fetchMaterials()]);
    if (disposed || token !== catalogGeneration) return;
    customers.value = customerRows;
    materials.value = materialRows;
  } catch (error) {
    if (!disposed && token === catalogGeneration) loadError.value = error instanceof Error ? error.message : '档案加载失败';
  } finally {
    if (!disposed && token === catalogGeneration) loading.value = false;
  }
}

function startCustomer() {
  if (saving.value || !canManage.value) return;
  editingCustomer.value = true;
  customerEditorOpen.value = true;
  selectedCustomerId.value = null;
  customerForm.value = emptyCustomerForm();
  clearCustomerFacts();
  saveError.value = '';
  saveHint.value = '';
}

function chooseCustomer(item: CustomerProfile, afterSave = false) {
  if (saving.value && !afterSave) return;
  clearCustomerFacts();
  editingCustomer.value = true;
  customerEditorOpen.value = false;
  customerFact.value = 'progress';
  selectedCustomerId.value = item.customerId;
  customerForm.value = {
    name: item.name,
    contact: item.contact,
    phone: item.phone,
    remark: item.remark,
    enabled: item.enabled,
    customerCode: item.customerCode,
  };
  saveError.value = '';
  saveHint.value = '';
  void loadCustomerFacts();
}

async function loadCustomerFacts() {
  await Promise.all([loadStatement(), loadProgress()]);
}

async function loadStatement() {
  const id = selectedCustomerId.value;
  if (!id || !canReadStatement.value) return;
  const token = ++statementGeneration;
  const month = yearMonth.value.trim();
  statement.value = null;
  statementLoading.value = true;
  statementError.value = '';
  try {
    const loaded = await fetchStatement(String(id), month);
    if (disposed || token !== statementGeneration || selectedCustomerId.value !== id) return;
    if (!loaded || loaded.customerId !== id) throw new Error('账单与当前客户不一致，请重新读取');
    statement.value = loaded;
  } catch (error) {
    if (disposed || token !== statementGeneration || selectedCustomerId.value !== id) return;
    statement.value = null;
    statementError.value = error instanceof Error ? error.message : '账单读取失败';
  } finally {
    if (!disposed && token === statementGeneration) statementLoading.value = false;
  }
}

async function loadProgress() {
  const id = selectedCustomerId.value;
  if (!id) return;
  const token = ++progressGeneration;
  progressRows.value = [];
  progressLoading.value = true;
  progressError.value = '';
  try {
    const loaded = await fetchProgress({ customer: String(id) });
    if (disposed || token !== progressGeneration || selectedCustomerId.value !== id) return;
    if (!Array.isArray(loaded) || loaded.some(row => row.customerId !== null && row.customerId !== id)) throw new Error('在制单据与当前客户不一致，请重新读取');
    progressRows.value = loaded;
    if (!progressRows.value.length) progressError.value = '这家客户暂无在制单据';
  } catch (error) {
    if (disposed || token !== progressGeneration || selectedCustomerId.value !== id) return;
    progressRows.value = [];
    progressError.value = error instanceof Error ? error.message : '进度读取失败';
  } finally {
    if (!disposed && token === progressGeneration) progressLoading.value = false;
  }
}

async function submitCustomer() {
  if (!canManage.value || saving.value) return;
  const name = customerForm.value.name.trim();
  if (!name) {
    saveError.value = '请填写客户名称';
    saveHint.value = '';
    return;
  }
  saving.value = true;
  saveError.value = '';
  try {
    const saved = await saveCustomer({
      customerId: selectedCustomerId.value ?? 0,
      customerCode: customerForm.value.customerCode,
      name,
      aliases: '',
      contact: customerForm.value.contact.trim(),
      phone: customerForm.value.phone.trim(),
      remark: customerForm.value.remark.trim(),
      enabled: customerForm.value.enabled,
    });
    if (disposed) return;
    customers.value = [...customers.value.filter((item) => item.customerId !== saved.customerId), saved]
      .sort((left, right) => left.customerId - right.customerId);
    chooseCustomer(saved, true);
    saveHint.value = `已保存客户「${saved.name}」`;
  } catch (error) {
    saveHint.value = '';
    saveError.value = error instanceof Error ? error.message : '保存客户失败';
  } finally {
    saving.value = false;
  }
}

function startMaterial() {
  if (saving.value || !canManage.value) return;
  editingMaterial.value = true;
  selectedMaterialCode.value = '';
  materialForm.value = emptyMaterialForm();
  saveError.value = '';
  saveHint.value = '';
}

function chooseMaterial(item: MaterialProfile, afterSave = false) {
  if (saving.value && !afterSave) return;
  editingMaterial.value = true;
  selectedMaterialCode.value = item.materialCode;
  materialForm.value = {
    name: item.name,
    aliases: item.aliases,
    remark: item.remark,
    enabled: item.enabled,
  };
  saveError.value = '';
  saveHint.value = '';
}

async function submitMaterial() {
  if (!canManage.value || saving.value) return;
  const name = materialForm.value.name.trim();
  if (!name) {
    saveError.value = '请填写材质名称';
    saveHint.value = '';
    return;
  }
  saving.value = true;
  saveError.value = '';
  try {
    const saved = await saveMaterial({
      materialCode: selectedMaterialCode.value,
      name,
      aliases: materialForm.value.aliases.trim(),
      remark: materialForm.value.remark.trim(),
      enabled: materialForm.value.enabled,
    });
    if (disposed) return;
    materials.value = [...materials.value.filter((item) => item.materialCode !== saved.materialCode), saved]
      .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'));
    chooseMaterial(saved, true);
    saveHint.value = `已保存材质「${saved.name}」`;
  } catch (error) {
    saveHint.value = '';
    saveError.value = error instanceof Error ? error.message : '保存材质失败';
  } finally {
    saving.value = false;
  }
}

function childCode(row: ProgressRow) {
  return row.workOrderCode === row.motherOrderCode ? '—' : row.workOrderCode;
}

function money(value: number) {
  return Number.isFinite(value) ? value.toFixed(2) : '—';
}
</script>

<template>
  <div class="page-stack archive-page" :class="{ 'has-customer-detail': editingCustomer && section === 'customers' }">
    <header class="section-block">
      <div>
        <h1>客户档案</h1>
        <p>按客户核对资料、在制订单和账单。材质与加工要求模板从基础资料进入。</p>
      </div>
      <div class="finance-sections" role="tablist" aria-label="档案分区">
        <button type="button" class="table-action" :disabled="saving" :class="{ active: section === 'customers' }" :aria-selected="section === 'customers'" @click="section = 'customers'">客户档案</button>
        <button type="button" class="table-action" :disabled="saving" :class="{ active: section === 'materials' }" :aria-selected="section === 'materials'" @click="section = 'materials'">材质管理</button>
        <button type="button" class="table-action" :disabled="saving" :class="{ active: section === 'templates' }" :aria-selected="section === 'templates'" @click="section = 'templates'">加工要求模板</button>
      </div>
    </header>

    <p v-if="loading" class="empty-hint">正在读取档案</p>
    <p v-if="loadError" class="operation-feedback danger">{{ loadError }}</p>
    <p v-if="saveHint" class="operation-feedback">{{ saveHint }}</p>
    <p v-if="saveError" class="operation-feedback danger">{{ saveError }}</p>

    <section v-if="section === 'customers'" class="archive-layout" aria-label="客户档案">
      <div class="section-block customer-list-panel">
        <div class="archive-toolbar">
          <label>
            <span>搜索</span>
            <input v-model="customerQuery" type="search" aria-label="搜索客户档案" placeholder="名称、编号或电话" />
          </label>
          <button v-if="canManage" class="secondary-button" type="button" @click="startCustomer">新建客户</button>
        </div>
        <p v-if="!visibleCustomers.length" class="empty-hint">没有匹配的客户</p>
        <div v-else class="archive-list">
          <button
            v-for="item in visibleCustomers"
            :key="item.customerId"
            type="button"
            class="archive-row"
            :aria-label="`选择客户 ${item.name}`"
            :aria-pressed="selectedCustomerId === item.customerId"
            :disabled="saving"
            @click="chooseCustomer(item)"
          >
            <strong>{{ item.name }}</strong>
            <span>{{ item.customerCode }}</span>
            <span v-if="!item.enabled">已停用</span>
          </button>
        </div>
      </div>

      <div class="archive-detail">
        <button v-if="editingCustomer" class="secondary-button customer-back" type="button" :disabled="saving" @click="editingCustomer = false">返回客户列表</button>
        <section v-if="editingCustomer" class="section-block" aria-label="客户资料">
          <div class="archive-heading"><h2>{{ selectedCustomerId ? customerForm.name : '新建客户' }}</h2><button v-if="selectedCustomerId && canManage" class="secondary-button" type="button" :aria-expanded="customerEditorOpen" :disabled="saving" @click="customerEditorOpen = !customerEditorOpen">{{ customerEditorOpen ? '收起资料编辑' : '编辑客户资料' }}</button></div>
          <dl v-if="!customerEditorOpen" class="detail-grid"><dt>客户编号</dt><dd>{{ selectedCustomerId }} · {{ customerForm.customerCode }}</dd><dt>联系人</dt><dd>{{ customerForm.contact || '未填写' }}</dd><dt>电话</dt><dd>{{ customerForm.phone || '未填写' }}</dd><dt>备注</dt><dd>{{ customerForm.remark || '未填写' }}</dd><dt>状态</dt><dd>{{ customerForm.enabled ? '启用' : '停用' }}</dd></dl>
          <div v-show="customerEditorOpen" class="archive-form" :inert="!customerEditorOpen || undefined">
            <label><span>客户名称</span><input v-model="customerForm.name" aria-label="客户名称" :readonly="!canManage || saving" /></label>
            <label><span>联系人</span><input v-model="customerForm.contact" aria-label="联系人" :readonly="!canManage || saving" /></label>
            <label><span>电话</span><input v-model="customerForm.phone" aria-label="电话" :readonly="!canManage || saving" /></label>
            <label><span>备注</span><textarea v-model="customerForm.remark" aria-label="客户备注" :readonly="!canManage || saving" /></label>
            <label class="archive-check"><input v-model="customerForm.enabled" type="checkbox" aria-label="启用客户" :disabled="!canManage || saving" /><span>启用，可用来建新单</span></label>
            <button v-if="canManage" class="primary-button" type="button" aria-label="保存客户" :disabled="saving" @click="submitCustomer">
              {{ saving ? '正在保存' : '保存客户' }}
            </button>
          </div>
        </section>
        <p v-else class="section-block empty-hint">选择一家客户查看资料、账单和订单进度，或新建客户。</p>

        <nav v-if="selectedCustomerId" class="customer-fact-tabs" aria-label="客户业务核对"><button class="secondary-button" type="button" :aria-pressed="customerFact === 'progress'" @click="customerFact = 'progress'">在制订单</button><button v-if="canReadStatement" class="secondary-button" type="button" :aria-pressed="customerFact === 'statement'" @click="customerFact = 'statement'">客户账单</button></nav>

        <section v-if="selectedCustomerId && canReadStatement" class="section-block customer-fact" :class="{ 'is-current': customerFact === 'statement' }" aria-label="客户账单">
          <div class="archive-toolbar">
            <h2>{{ yearMonth || '全部' }} 账单</h2>
            <label>
              <span>月份</span>
              <input v-model="yearMonth" type="month" aria-label="账单月份" @change="loadStatement" />
            </label>
            <button class="secondary-button" type="button" :disabled="statementLoading" @click="loadStatement">
              {{ statementLoading ? '正在读取' : '查看账单' }}
            </button>
          </div>
          <p class="archive-note">这里按所选月份查看应收和收款。登记收款仍在财务页办理。</p>
          <p v-if="statementError" class="operation-feedback danger">{{ statementError }}</p>
          <template v-else-if="statement">
            <dl class="detail-grid">
              <dt>应收</dt><dd>{{ money(statement.receivableAmount) }}</dd>
              <dt>已收</dt><dd>{{ money(statement.receivedAmount) }}</dd>
              <dt>未收</dt><dd>{{ money(statement.outstandingAmount) }}</dd>
              <dt>预收</dt><dd>{{ money(statement.prepaymentAmount) }}</dd>
              <dt>在库数量汇总</dt><dd>{{ statement.stockQuantity }}</dd><dt>在库重量汇总</dt><dd>{{ statement.stockWeight }}</dd>
            </dl>
            <p v-if="statement.stockNote" class="archive-note">{{ statement.stockNote }}</p>
            <p v-if="!statement.lines.length" class="empty-hint">这个月没有账单明细</p>
            <div v-else class="module-table-wrap">
              <table class="module-table">
                <thead>
                  <tr><th>月份</th><th>单号</th><th>工序</th><th>数量</th><th>金额</th></tr>
                </thead>
                <tbody>
                  <tr v-for="line in statement.lines" :key="line.code">
                    <td>{{ line.yearMonth }}</td>
                    <td class="code">{{ line.workOrderCode || line.motherOrderCode }}</td>
                    <td>{{ line.processName }}</td>
                    <td>{{ line.quantity }} {{ line.unit }}</td>
                    <td>{{ money(line.amount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </section>

        <section v-if="selectedCustomerId" class="section-block customer-fact" :class="{ 'is-current': customerFact === 'progress' }" aria-label="客户订单进度">
          <div class="archive-heading"><h2>在制订单</h2><button class="secondary-button" type="button" :disabled="progressLoading" @click="loadProgress">刷新在制订单</button></div>
          <p class="archive-note">与进度页同一份在制单据，只看这家客户。</p>
          <p v-if="progressLoading" class="empty-hint">正在读取进度</p>
          <p v-if="progressError" class="operation-feedback" :class="{ danger: progressError !== '这家客户暂无在制单据' }">{{ progressError }}</p>
          <div v-if="progressRows.length" class="module-table-wrap">
            <table class="module-table">
              <thead>
                <tr><th>母单</th><th>子单</th><th>当前工序</th><th>期限</th><th>状态</th></tr>
              </thead>
              <tbody>
                <tr v-for="row in progressRows" :key="`${row.motherOrderCode}|${row.workOrderCode}`">
                  <td class="code"><button v-if="openOrder" class="table-action" type="button" @click="openOrder(progressTraceTarget(row, 0))">{{ row.motherOrderCode }}</button><template v-else>{{ row.motherOrderCode }}</template></td>
                  <td class="code">{{ childCode(row) }}</td>
                  <td>{{ row.currentProcess }}</td>
                  <td>{{ row.dueDate || '—' }}</td>
                  <td>{{ row.status }}<template v-if="row.overdue"> · 逾期</template></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </section>

    <section v-else-if="section === 'materials'" class="archive-layout" aria-label="材质管理">
      <div class="section-block">
        <div class="archive-toolbar">
          <label>
            <span>搜索</span>
            <input v-model="materialQuery" type="search" aria-label="搜索材质" placeholder="名称或编码" />
          </label>
          <button v-if="canManage" class="secondary-button" type="button" @click="startMaterial">新建材质</button>
        </div>
        <p class="archive-note">来料新建母单时，也可以直接新增材质并选入当前单。两边写的是同一份目录。</p>
        <p v-if="!visibleMaterials.length" class="empty-hint">没有匹配的材质</p>
        <div v-else class="archive-list">
          <button
            v-for="item in visibleMaterials"
            :key="item.materialCode"
            type="button"
            class="archive-row"
            :aria-label="`选择材质 ${item.name}`"
            :aria-pressed="selectedMaterialCode === item.materialCode"
            @click="chooseMaterial(item)"
          >
            <strong>{{ item.name }}</strong>
            <span>{{ item.materialCode }}</span>
            <span v-if="!item.enabled">已停用</span>
          </button>
        </div>
      </div>
      <section v-if="editingMaterial" class="section-block" aria-label="材质资料">
        <h2>{{ selectedMaterialCode ? '材质资料' : '新建材质' }}</h2>
        <div class="archive-form">
          <label><span>材质名称</span><input v-model="materialForm.name" aria-label="材质名称" :readonly="!canManage || saving" /></label>
          <label><span>别称</span><input v-model="materialForm.aliases" aria-label="材质别称" :readonly="!canManage || saving" placeholder="多个用顿号或竖线分开" /></label>
          <label><span>备注</span><textarea v-model="materialForm.remark" aria-label="材质备注" :readonly="!canManage || saving" /></label>
          <label class="archive-check"><input v-model="materialForm.enabled" type="checkbox" aria-label="启用材质" :disabled="!canManage || saving" /><span>启用，可在来料单中选择</span></label>
          <button v-if="canManage" class="primary-button" type="button" aria-label="保存材质" :disabled="saving" @click="submitMaterial">
            {{ saving ? '正在保存' : '保存材质' }}
          </button>
        </div>
      </section>
      <p v-else class="section-block empty-hint">选择一种材质，或新建常见材质。</p>
    </section>
    <RequirementTemplatePanel v-else-if="section === 'templates'" :can-manage="canManage" />
  </div>
</template>

<style scoped>
.archive-page h1,
.archive-page h2 { margin: 0; }
.archive-layout {
  display: grid;
  grid-template-columns: minmax(220px, 320px) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}
.customer-back { display: none; }
.archive-heading, .customer-fact-tabs { display: flex; gap: 10px; justify-content: space-between; align-items: center; flex-wrap: wrap; }
.archive-heading > *, .archive-layout > *, .customer-fact { min-width: 0; }
.archive-page { overflow-wrap: anywhere; }
.archive-detail { display: grid; gap: 12px; min-width: 0; }
.archive-toolbar,
.archive-form { display: grid; gap: 10px; }
.archive-form label,
.archive-toolbar label { display: grid; gap: 4px; }
.archive-form input,
.archive-form textarea,
.archive-toolbar input { width: 100%; }
.archive-toolbar { align-items: end; }
.archive-list { display: grid; gap: 6px; max-height: 480px; overflow: auto; }
.archive-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: transparent;
}
.archive-row[aria-pressed="true"] { border-color: var(--accent); }
.archive-check { display: flex; gap: 8px; align-items: center; }
.archive-note { color: var(--muted); margin: 0; }
.archive-form textarea { min-height: 72px; }
@media (max-width: 800px) {
  .archive-layout { grid-template-columns: minmax(0, 1fr); }
  .customer-back { display: inline-flex; }
  .has-customer-detail .customer-list-panel, .customer-fact:not(.is-current) { display: none; }
  .archive-page:not(.has-customer-detail) .archive-layout[aria-label="客户档案"] .archive-detail { display: none; }
  .archive-page button, .archive-page input { min-height: 44px; }
  .archive-page input, .archive-page textarea { box-sizing: border-box; min-width: 0; font-size: 16px; }
  .archive-page input[type="checkbox"] { min-height: initial; width: auto; }
}
</style>
