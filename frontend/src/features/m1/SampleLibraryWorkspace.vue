<script setup lang="ts">
import { computed } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  acceptanceDataSets,
  hardwarePocRecords,
  openSourceSampleMappings,
  sampleScenarios,
  type Tone,
} from '../../data/m1PrototypeData';
import { registerImportSummary, registerImportTemplates, registerRuleTone } from '../../data/registerImportTemplates';

function scenarioTone(status: string): Tone {
  if (status === 'ready') return 'green';
  if (status === 'blocked') return 'red';
  return 'amber';
}

function scenarioLabel(status: string) {
  if (status === 'ready') return '可演示';
  if (status === 'blocked') return '阻塞';
  return '待补数据';
}

function mappingLabel(status: string) {
  if (status === 'applied') return '已映射';
  if (status === 'watching') return '观察中';
  return '预留';
}

function mappingTone(status: string): Tone {
  if (status === 'applied') return 'green';
  if (status === 'watching') return 'blue';
  return 'gray';
}

function dataSetLabel(status: string) {
  if (status === 'active') return '测试中';
  if (status === 'blocked') return '阻塞';
  return '待现场数据';
}

function dataSetTone(status: string): Tone {
  if (status === 'active') return 'green';
  if (status === 'blocked') return 'red';
  return 'amber';
}

function parserStatusLabel(status: string) {
  if (status === 'COMMAND_READY') return '预览就绪';
  return '解析器待接入';
}

function keyFieldLabel(template: (typeof registerImportTemplates)[number]) {
  return template.fields.find((field) =>
    field.targetField.includes('paperExpressText')
    || field.targetField.includes('operatorSignatureName')
    || field.targetField.includes('handoffAuditStatus')
  )?.targetField ?? template.fields[0]?.targetField ?? '待映射';
}

const activeScenario = sampleScenarios[0];
const activeDataSet = acceptanceDataSets.find((dataset) => dataset.scenarioId === activeScenario.id);
const activeRegisterTemplate = registerImportTemplates[0];

const sampleKpis = computed(() => [
  {
    label: '核心样例',
    value: sampleScenarios.length,
    unit: '条',
    detail: `${sampleScenarios.filter((scenario) => scenario.status === 'ready').length} 条可演示`,
    tone: 'blue' as Tone,
  },
  {
    label: '开源映射',
    value: openSourceSampleMappings.filter((mapping) => mapping.status === 'applied').length,
    unit: '项',
    detail: `覆盖 ${openSourceSampleMappings.length} 个参考对象`,
    tone: 'green' as Tone,
  },
  {
    label: '真实数据集',
    value: acceptanceDataSets.length,
    unit: '组',
    detail: `${acceptanceDataSets.filter((dataset) => dataset.status === 'active').length} 组进入回归`,
    tone: 'violet' as Tone,
  },
  {
    label: '待补缺口',
    value: acceptanceDataSets.reduce((sum, dataset) => sum + dataset.gaps.length, 0),
    unit: '项',
    detail: '现场数据、登录人、硬件证据',
    tone: 'amber' as Tone,
  },
  {
    label: '硬件 POC',
    value: hardwarePocRecords.length,
    unit: '项',
    detail: `${hardwarePocRecords.filter((record) => record.tone === 'red').length} 项阻断`,
    tone: 'red' as Tone,
  },
  {
    label: '登记模板',
    value: registerImportSummary.templates,
    unit: '类',
    detail: `${registerImportSummary.commandReadyTemplates} 类命令已对齐`,
    tone: 'green' as Tone,
  },
]);

const scenarioRows = computed(() =>
  sampleScenarios.map((scenario) => {
    const mappings = openSourceSampleMappings.filter((mapping) => mapping.mappedScenarioId === scenario.id);
    const datasets = acceptanceDataSets.filter((dataset) => dataset.scenarioId === scenario.id);

    return {
      ...scenario,
      mappingCount: mappings.length,
      dataSetCount: datasets.length,
      primaryFixture: datasets[0]?.fixture ?? '待绑定 fixture',
      coverage: `${scenario.chain.length} 节点 / ${scenario.evidence.length} 证据`,
    };
  }),
);

const researchCards = [
  {
    source: 'ERPNext Job Card',
    referenceUrl: 'https://docs.frappe.io/erpnext/job-card',
    reusablePattern: '工单按工序拆到 Job Card，记录工作站、员工、完工数量、时间日志、物料请求和 WIP 进度。',
    mappedTo: '生产工单 / 工序报工 / 损耗试算',
    gate: '报工样例必须写库存台账或证据事件，不能只改页面状态。',
    tone: 'blue' as Tone,
  },
  {
    source: 'Odoo Quality Checks',
    referenceUrl:
      'https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/quality/quality_management/quality_checks.html',
    reusablePattern: '质检点可由规则自动生成，也可落在制造单、库存作业或 Shop Floor 工序上处理 Pass/Fail。',
    mappedTo: '质检返工 / 复检放行 / 缺陷附件',
    gate: '质检样例必须包含缺陷、处理动作、返工或复检结果、照片和责任人。',
    tone: 'green' as Tone,
  },
  {
    source: 'InvenTree Build Orders',
    referenceUrl: 'https://docs.inventree.org/en/stable/manufacturing/build/',
    reusablePattern: '构建单按 BOM 分配库存，完工时扣减已分配库存并生成成品，测试结果和附件可绑定到构建输出。',
    mappedTo: '客户库存隔离 / 批次追溯 / 验收测试',
    gate: '样例必须校验客户所有权、批次、库位、状态和测试结果，不允许只保留汇总数。',
    tone: 'violet' as Tone,
  },
] as const;

const testGates = [
  { code: 'G1', label: '业务主链路', detail: '来料、拆包、工单、报工、质检、出库必须一条样例跑通。', state: '已接入', tone: 'green' as Tone },
  { code: 'G2', label: '库存台账', detail: '数量、重量、状态、库位变化必须有 before/after。', state: '已接入', tone: 'green' as Tone },
  { code: 'G3', label: '证据链', detail: '称重、照片、视频、附件、操作日志需要统一编号。', state: '部分缺口', tone: 'amber' as Tone },
  { code: 'G4', label: '硬件兜底', detail: 'NVR、扫码、标签、电子秤失败时保留人工路径。', state: 'POC 中', tone: 'amber' as Tone },
  { code: 'G5', label: '自动化测试', detail: 'fixture 必须被后端或前端测试引用。', state: '已接入', tone: 'blue' as Tone },
  { code: 'G6', label: '真实数据替换', detail: '试点前用脱敏真实单据替换演示样例。', state: '未完成', tone: 'red' as Tone },
] as const;

const fieldGapRows = computed(() =>
  acceptanceDataSets.flatMap((dataset) =>
    dataset.gaps.map((gap, index) => ({
      code: `${dataset.code}-G${index + 1}`,
      scenarioId: dataset.scenarioId,
      gap,
      fixture: dataset.fixture,
      tone: dataset.status === 'active' ? ('amber' as Tone) : ('red' as Tone),
    })),
  ),
);

const registerTemplateRows = computed(() =>
  registerImportTemplates.map((template) => ({
    ...template,
    requiredFields: template.fields.filter((field) => field.required).length,
    blockerRules: template.validationRules.filter((rule) => rule.level === 'BLOCK').length,
  })),
);
</script>

<template>
  <div class="sample-control-workbench page-stack">
    <section class="sample-command-surface" aria-label="样例验收指挥台">
      <div class="sample-command-copy">
        <div class="console-title-line">
          <span class="eyebrow">SAMPLE CONTROL</span>
          <StatusBadge label="M3.66 登记表预览" tone="green" />
        </div>
        <h2>样例验收指挥台</h2>
        <p>
          样例库不再只做演示说明，而是把开源参考、业务样例、fixture、自动化测试、硬件证据和真实数据缺口放到同一张管理台里。
        </p>
        <div class="sample-command-tags">
          <span>业务评审</span>
          <span>自动化回归</span>
          <span>现场培训</span>
          <span>验收签字</span>
          <span>真实数据替换</span>
        </div>
      </div>

      <div class="sample-kpi-grid" aria-label="样例库关键指标">
        <article v-for="item in sampleKpis" :key="item.label" :class="`tone-${item.tone}`">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
          <em>{{ item.detail }}</em>
        </article>
      </div>
    </section>

    <section class="sample-ops-grid register-import-grid" aria-label="现场登记表导入准备度">
      <div class="sample-table-panel register-import-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">REGISTER IMPORT</span>
            <h2>现场登记表导入准备度</h2>
            <p>把现场 Excel/纸质表固化成模板、字段映射、阻断规则和上传预览解析，先预检再确认入账。</p>
          </div>
          <StatusBadge :label="`${registerImportSummary.templates} 类模板`" tone="green" />
        </div>
        <div class="register-kpi-strip" aria-label="登记表导入指标">
          <article>
            <span>映射字段</span>
            <strong>{{ registerImportSummary.fields }}</strong>
            <small>{{ registerImportSummary.requiredFields }} 个必填</small>
          </article>
          <article>
            <span>命令对齐</span>
            <strong>{{ registerImportSummary.commandReadyTemplates }}</strong>
            <small>入库/报工/出货</small>
          </article>
          <article>
            <span>解析状态</span>
            <strong>上传预览</strong>
            <small>{{ registerImportSummary.parserStatus }}</small>
          </article>
        </div>
        <div class="erp-table-wrap sample-matrix-wrap">
          <table class="erp-table register-template-table">
            <thead>
              <tr>
                <th>模板</th>
                <th>来源文件</th>
                <th>业务链路</th>
                <th>目标命令</th>
                <th>字段</th>
                <th>阻断规则</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="template in registerTemplateRows" :key="template.code" class="register-template-row">
                <td>
                  <span class="cell-stack">
                    <b>{{ template.code }}</b>
                    <small>{{ template.name }}</small>
                  </span>
                </td>
                <td>{{ template.sourceFile }}</td>
                <td>{{ template.businessFlow }}</td>
                <td>{{ template.targetCommand }}</td>
                <td>
                  <span class="cell-stack">
                    <b>{{ template.fields.length }} 项 / {{ template.requiredFields }} 必填</b>
                    <small>{{ keyFieldLabel(template) }}</small>
                  </span>
                </td>
                <td>{{ template.blockerRules }} 条</td>
                <td><StatusBadge :label="parserStatusLabel(template.parserStatus)" tone="green" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside class="register-detail-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">ACTIVE TEMPLATE</span>
            <h2>{{ activeRegisterTemplate.name }}</h2>
          </div>
          <StatusBadge label="命令已对齐" tone="green" />
        </div>
        <dl class="detail-grid">
          <dt>目标命令</dt>
          <dd>{{ activeRegisterTemplate.targetCommand }}</dd>
          <dt>台账类型</dt>
          <dd>{{ activeRegisterTemplate.targetLedgerType }}</dd>
          <dt>下一动作</dt>
          <dd>{{ activeRegisterTemplate.nextAction }}</dd>
        </dl>
        <div class="register-field-list" aria-label="字段映射">
          <article v-for="field in activeRegisterTemplate.fields.slice(0, 7)" :key="field.sourceColumn">
            <strong>{{ field.sourceColumn }}</strong>
            <span>{{ field.targetField }}</span>
            <small>{{ field.normalizeRule }}</small>
          </article>
        </div>
        <div class="register-rule-list" aria-label="导入校验规则">
          <article
            v-for="rule in activeRegisterTemplate.validationRules"
            :key="rule.code"
            :class="`tone-${registerRuleTone(rule.level)}`"
          >
            <b>{{ rule.code }}</b>
            <span>{{ rule.description }}</span>
            <small>{{ rule.fallback }}</small>
          </article>
        </div>
      </aside>
    </section>

    <section class="sample-ops-grid" aria-label="样例矩阵与当前样例">
      <div class="sample-table-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">SCENARIO MATRIX</span>
            <h2>样例矩阵</h2>
          </div>
          <StatusBadge :label="`${scenarioRows.length} 条样例`" tone="blue" />
        </div>
        <div class="erp-table-wrap sample-matrix-wrap">
          <table class="erp-table sample-matrix-table">
            <thead>
              <tr>
                <th>样例</th>
                <th>目标</th>
                <th>链路/证据</th>
                <th>开源映射</th>
                <th>fixture</th>
                <th>负责人</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="scenario in scenarioRows" :key="scenario.id" class="sample-row">
                <td>
                  <span class="cell-stack">
                    <b>{{ scenario.id }}</b>
                    <small>{{ scenario.name }}</small>
                  </span>
                </td>
                <td>{{ scenario.purpose }}</td>
                <td>{{ scenario.coverage }}</td>
                <td>{{ scenario.mappingCount }} 项</td>
                <td>{{ scenario.primaryFixture }}</td>
                <td>{{ scenario.owner }}</td>
                <td><StatusBadge :label="scenarioLabel(scenario.status)" :tone="scenarioTone(scenario.status)" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside class="active-sample-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">ACTIVE SAMPLE</span>
            <h2>当前主样例</h2>
          </div>
          <StatusBadge :label="scenarioLabel(activeScenario.status)" :tone="scenarioTone(activeScenario.status)" />
        </div>
        <div class="active-sample-code">
          <strong>{{ activeScenario.id }}</strong>
          <span>{{ activeScenario.name }}</span>
        </div>
        <dl class="detail-grid active-sample-detail">
          <dt>负责人</dt>
          <dd>{{ activeScenario.owner }}</dd>
          <dt>链路节点</dt>
          <dd>{{ activeScenario.chain.length }} 个</dd>
          <dt>证据类型</dt>
          <dd>{{ activeScenario.evidence.length }} 类</dd>
          <dt>fixture</dt>
          <dd>{{ activeDataSet?.fixture ?? '待绑定' }}</dd>
          <dt>自动化</dt>
          <dd>{{ activeDataSet?.automatedTest ?? '待接入' }}</dd>
        </dl>
        <div class="sample-chain-strip">
          <span v-for="node in activeScenario.chain" :key="node">{{ node }}</span>
        </div>
        <div class="sample-evidence-list">
          <article v-for="evidence in activeScenario.evidence" :key="evidence">
            <b />
            <span>{{ evidence }}</span>
          </article>
        </div>
      </aside>
    </section>

    <section class="sample-ops-grid reference-grid" aria-label="开源调研命中">
      <div class="sample-reference-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">OPEN SOURCE BASIS</span>
            <h2>开源调研命中</h2>
            <p>本轮把官方文档里的成熟对象转成样例验收规则，避免临时造轮子。</p>
          </div>
          <StatusBadge :label="`${researchCards.length} 个来源`" tone="blue" />
        </div>
        <div class="reference-card-grid">
          <article
            v-for="item in researchCards"
            :key="item.source"
            class="reference-card"
            :class="`tone-${item.tone}`"
          >
            <div>
              <small>{{ item.mappedTo }}</small>
              <strong>{{ item.source }}</strong>
            </div>
            <p>{{ item.reusablePattern }}</p>
            <span>{{ item.gate }}</span>
            <a :href="item.referenceUrl" target="_blank" rel="noreferrer">查看官方文档</a>
          </article>
        </div>
      </div>

      <aside class="sample-gate-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">TEST GATES</span>
            <h2>验收门禁</h2>
          </div>
          <StatusBadge label="6 项门禁" tone="violet" />
        </div>
        <div class="gate-card-list">
          <article v-for="gate in testGates" :key="gate.code" class="gate-card" :class="`tone-${gate.tone}`">
            <b>{{ gate.code }}</b>
            <div>
              <strong>{{ gate.label }}</strong>
              <span>{{ gate.detail }}</span>
            </div>
            <StatusBadge :label="gate.state" :tone="gate.tone" />
          </article>
        </div>
      </aside>
    </section>

    <section class="sample-ops-grid acceptance-grid" aria-label="真实验收数据集和现场缺口">
      <div class="sample-table-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">REAL DATA TEST</span>
            <h2>真实验收数据集</h2>
          </div>
          <StatusBadge :label="`${acceptanceDataSets.length} 组数据`" tone="green" />
        </div>
        <div class="erp-table-wrap sample-matrix-wrap">
          <table class="erp-table fixture-table">
            <thead>
              <tr>
                <th>数据集</th>
                <th>绑定样例</th>
                <th>自动化测试</th>
                <th>覆盖链路</th>
                <th>证据</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dataset in acceptanceDataSets" :key="dataset.code" class="fixture-row">
                <td>
                  <span class="cell-stack">
                    <b>{{ dataset.code }}</b>
                    <small>{{ dataset.fixture }}</small>
                  </span>
                </td>
                <td>{{ dataset.scenarioId }}</td>
                <td>{{ dataset.automatedTest }}</td>
                <td>{{ dataset.covers.length }} 项</td>
                <td>{{ dataset.evidence.join(' / ') }}</td>
                <td><StatusBadge :label="dataSetLabel(dataset.status)" :tone="dataSetTone(dataset.status)" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside class="field-gap-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">FIELD DATA QUEUE</span>
            <h2>现场数据缺口</h2>
          </div>
          <StatusBadge :label="`${fieldGapRows.length} 项`" tone="amber" />
        </div>
        <div class="field-gap-list">
          <article v-for="gap in fieldGapRows" :key="gap.code" :class="`tone-${gap.tone}`">
            <strong>{{ gap.code }}</strong>
            <span>{{ gap.gap }}</span>
            <small>{{ gap.scenarioId }} / {{ gap.fixture }}</small>
          </article>
        </div>
      </aside>
    </section>

    <section class="section-block mapping-ledger-panel">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">MAPPING LEDGER</span>
          <h2>已有开源样例映射台账</h2>
        </div>
        <StatusBadge :label="`${openSourceSampleMappings.length} 条映射`" tone="blue" />
      </div>
      <div class="mapping-ledger-grid">
        <article v-for="mapping in openSourceSampleMappings" :key="mapping.source">
          <div>
            <small>{{ mapping.mappedScenarioId }}</small>
            <strong>{{ mapping.source }}</strong>
          </div>
          <p>{{ mapping.reusablePattern }}</p>
          <dl class="detail-grid">
            <dt>模块</dt>
            <dd>{{ mapping.mappedModule }}</dd>
            <dt>规则</dt>
            <dd>{{ mapping.acceptanceRule }}</dd>
          </dl>
          <StatusBadge :label="mappingLabel(mapping.status)" :tone="mappingTone(mapping.status)" />
        </article>
      </div>
    </section>
  </div>
</template>
