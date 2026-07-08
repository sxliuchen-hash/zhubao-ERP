<script setup lang="ts">
import { onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  configurationBaselineFallback,
  configurationToneByLevel,
  type ConfigurationBaselineWorkspace,
} from '../../data/configurationBaselineData';
import type { Tone } from '../../data/m1PrototypeData';
import {
  fetchAuthAuditEvents,
  fetchAuthDemoUsers,
  fetchAuthSession,
  loginAuthSession,
  logoutAuthSession,
  type AuthAuditEvent,
  type AuthSession,
} from '../../services/authApi';
import { fetchConfigurationBaselineWorkspace } from '../../services/configurationBaselineApi';

const workspace = ref<ConfigurationBaselineWorkspace>(configurationBaselineFallback);
const authSession = ref<AuthSession | null>(null);
const authDemoUsers = ref<string[]>(['leader', 'operator', 'warehouse', 'qc', 'admin']);
const authUsername = ref('leader');
const authPassword = ref('leader123');
const authBusy = ref(false);
const authMessage = ref('');
const authAuditEvents = ref<AuthAuditEvent[]>([
  {
    eventId: 'AUTH-AUD-DEMO-001',
    eventType: 'LOGIN',
    username: 'leader',
    operator: '车间组长 马杰',
    role: 'WORKSHOP_LEADER',
    result: 'SUCCESS',
    reason: '演示账号登录成功',
    occurredAt: '2026-07-08 10:00:00',
    sessionTokenSuffix: 'demo01',
  },
]);

onMounted(async () => {
  await refreshWorkspace();
  await restoreAuthSession();
  await loadDemoUsers();
  await loadAuthAuditEvents();
});

async function refreshWorkspace() {
  try {
    workspace.value = await fetchConfigurationBaselineWorkspace();
  } catch {
    // Keep the review baseline visible when the backend is not running.
  }
}

async function restoreAuthSession() {
  try {
    const session = await fetchAuthSession();
    if (session) {
      authSession.value = session;
      await refreshWorkspace();
    }
  } catch {
    authSession.value = null;
  }
}

async function loadDemoUsers() {
  try {
    authDemoUsers.value = await fetchAuthDemoUsers();
  } catch {
    // Demo user list is optional when the frontend is running without the backend.
  }
}

async function loadAuthAuditEvents() {
  try {
    authAuditEvents.value = await fetchAuthAuditEvents();
  } catch {
    // Keep local audit sample visible when the backend is not running.
  }
}

function demoPassword(username: string): string {
  return `${username}123`;
}

function syncDemoPassword() {
  authPassword.value = demoPassword(authUsername.value);
}

async function handleLogin() {
  authBusy.value = true;
  authMessage.value = '';
  try {
    authSession.value = await loginAuthSession({
      username: authUsername.value,
      password: authPassword.value,
    });
    authMessage.value = `已登录：${authSession.value.roleLabel}`;
    await refreshWorkspace();
    await loadAuthAuditEvents();
  } catch (error) {
    authMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    authBusy.value = false;
  }
}

async function handleLogout() {
  authBusy.value = true;
  authMessage.value = '';
  try {
    await logoutAuthSession();
    authSession.value = null;
    authMessage.value = '已退出登录会话';
    await refreshWorkspace();
    await loadAuthAuditEvents();
  } finally {
    authBusy.value = false;
  }
}

function levelTone(level: string): Tone {
  return configurationToneByLevel[level] ?? 'gray';
}

function changeTone(changeControl: string): Tone {
  if (changeControl.includes('审批')) return 'red';
  if (changeControl.includes('日志')) return 'amber';
  return 'blue';
}

function requestStatusTone(status: string): Tone {
  if (status.includes('待')) return 'amber';
  if (status.includes('驳回')) return 'red';
  return 'green';
}

function riskTone(riskLevel: string): Tone {
  if (riskLevel === '高') return 'red';
  if (riskLevel === '中') return 'amber';
  return 'green';
}

function dataScopeTone(dataScope: string): Tone {
  if (dataScope === 'ALL') return 'violet';
  if (dataScope === 'SELF') return 'green';
  if (dataScope === 'TEAM') return 'blue';
  return 'amber';
}

function permissionTone(index: number): Tone {
  return ['blue', 'green', 'amber', 'violet'][index % 4] as Tone;
}
</script>

<template>
  <div class="page-stack">
    <section class="operations-hero">
      <div class="hero-copy">
        <span class="eyebrow">Configuration Baseline</span>
        <h2>配置与构型基线台</h2>
        <p>把系统参数、编码规则、角色权限、工序模板、仓库状态和视频参数集中管理，作为 M3 开发和试点上线的冻结依据。</p>
      </div>
      <div class="hero-kpis">
        <article>
          <span>系统参数</span>
          <strong>{{ workspace.summary.systemParameters }}</strong>
        </article>
        <article>
          <span>需审批项</span>
          <strong>{{ workspace.summary.approvalRequiredItems }}</strong>
        </article>
        <article>
          <span>仓库状态</span>
          <strong>{{ workspace.summary.warehouseStates }}</strong>
        </article>
        <article>
          <span>变更单</span>
          <strong>{{ workspace.summary.changeRequests }}</strong>
        </article>
      </div>
    </section>

    <section class="control-filter-bar" aria-label="配置基线筛选">
      <div>
        <span>冻结阶段</span>
        <strong>M2 文档冻结 -> M3 开发中期</strong>
      </div>
      <div>
        <span>变更原则</span>
        <strong>C1 配置需审批并保留历史</strong>
      </div>
      <div>
        <span>当前模式</span>
        <strong>只读评审基线，M3 接入真实配置表</strong>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" type="button">只看 C1</button>
        <button class="secondary-button" type="button">导出基线</button>
      </div>
    </section>

    <section class="security-context-panel" aria-label="当前权限与数据范围边界">
      <div class="security-context-main">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">SECURITY CONTEXT</span>
            <h2>当前权限 / 数据范围边界</h2>
            <p>M3 轻量登录会话优先，请求头上下文保留为评审和自动化测试兜底。</p>
          </div>
          <StatusBadge :label="workspace.securityContext.headerBased ? '请求头上下文' : '真实登录上下文'" tone="amber" />
        </div>

        <form class="auth-session-strip" aria-label="轻量登录会话" @submit.prevent="handleLogin">
          <div>
            <span>登录会话</span>
            <strong>{{ authSession ? `${authSession.operator} / ${authSession.roleLabel}` : '未登录，使用请求头兜底' }}</strong>
            <small>{{ authSession ? `过期时间 ${authSession.expiresAt}` : 'Bearer token 会自动写入后续 API 请求' }}</small>
          </div>
          <label>
            <span>演示账号</span>
            <select v-model="authUsername" :disabled="authBusy" @change="syncDemoPassword">
              <option v-for="username in authDemoUsers" :key="username" :value="username">{{ username }}</option>
            </select>
          </label>
          <label>
            <span>密码</span>
            <input v-model="authPassword" :disabled="authBusy" type="password" autocomplete="current-password" />
          </label>
          <div class="auth-session-actions">
            <button class="primary-button" type="submit" :disabled="authBusy">
              {{ authBusy ? '处理中' : '登录' }}
            </button>
            <button class="secondary-button" type="button" :disabled="authBusy || !authSession" @click="handleLogout">退出</button>
          </div>
          <small v-if="authMessage" class="auth-session-message">{{ authMessage }}</small>
        </form>

        <div class="security-identity-grid">
          <article>
            <span>操作人</span>
            <strong>{{ workspace.securityContext.operator }}</strong>
            <small>{{ workspace.securityContext.headerContract.operatorHeader }}</small>
          </article>
          <article>
            <span>角色</span>
            <strong>{{ workspace.securityContext.roleLabel }}</strong>
            <small>{{ workspace.securityContext.role }}</small>
          </article>
          <article>
            <span>数据范围</span>
            <strong>{{ workspace.securityContext.dataScopeLabel }}</strong>
            <StatusBadge :label="workspace.securityContext.dataScope" :tone="dataScopeTone(workspace.securityContext.dataScope)" />
          </article>
        </div>

        <div class="security-boundary-note">
          <strong>数据边界说明</strong>
          <span>{{ workspace.securityContext.dataBoundary }}</span>
        </div>
      </div>

      <aside class="security-context-side">
        <div>
          <span class="eyebrow">ALLOWED ACTIONS</span>
          <h3>敏感动作白名单</h3>
        </div>
        <div class="security-permission-list">
          <article
            v-for="(permission, index) in workspace.securityContext.allowedPermissions"
            :key="permission.code"
            :class="`tone-${permissionTone(index)}`"
          >
            <strong>{{ permission.label }}</strong>
            <span>{{ permission.code }}</span>
          </article>
        </div>
        <div class="security-header-contract">
          <strong>请求头契约</strong>
          <span>{{ workspace.securityContext.headerContract.roleHeader }}</span>
          <span>{{ workspace.securityContext.headerContract.dataScopeHeader }}</span>
        </div>
        <div class="security-menu-contract">
          <strong>授权菜单树</strong>
          <article v-for="menu in workspace.securityContext.menuTree" :key="menu.code">
            <b>{{ menu.label }}</b>
            <span>{{ menu.code }} / {{ menu.module }}</span>
            <small>{{ menu.buttonCodes.length ? menu.buttonCodes.join(' / ') : '只读菜单' }}</small>
          </article>
        </div>
        <div class="security-audit-contract">
          <strong>登录审计</strong>
          <article v-for="event in authAuditEvents.slice(0, 4)" :key="event.eventId">
            <b>{{ event.eventType }} / {{ event.result }}</b>
            <span>{{ event.eventId }} · {{ event.username || 'unknown' }} / {{ event.role || '未识别角色' }}</span>
            <small>{{ event.reason }} · {{ event.occurredAt }} · token *{{ event.sessionTokenSuffix || 'none' }}</small>
          </article>
        </div>
      </aside>

      <div class="security-limitation-row">
        <article v-for="note in workspace.securityContext.limitationNotes" :key="note">
          <b />
          <span>{{ note }}</span>
        </article>
      </div>
    </section>

    <section class="toolbar-band">
      <div>
        <h2>配置基线总览</h2>
        <p>当前页面用于确认规则是否真实适合车间现场，后续编辑能力必须接入权限、审批和审计日志。</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" type="button">变更记录</button>
        <button class="primary-button" type="button">冻结当前基线</button>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>配置变更审批</h2>
            <p>C1 配置和编码规则变更必须先形成变更单，再审批、落库和留痕。</p>
          </div>
          <StatusBadge :label="`${workspace.summary.changeRequests} 张变更单`" tone="amber" />
        </div>
        <div class="change-request-list">
          <article v-for="request in workspace.changeRequests" :key="request.code">
            <div class="change-request-head">
              <div>
                <small>{{ request.code }}</small>
                <strong>{{ request.targetName }}</strong>
                <span>{{ request.targetKey }} / {{ request.changeType }}</span>
              </div>
              <div class="change-request-badges">
                <StatusBadge :label="request.status" :tone="requestStatusTone(request.status)" />
                <StatusBadge :label="`${request.riskLevel}风险`" :tone="riskTone(request.riskLevel)" />
              </div>
            </div>
            <div class="value-diff">
              <span>原值：{{ request.beforeValue }}</span>
              <span>新值：{{ request.afterValue }}</span>
            </div>
            <p>{{ request.reason }}</p>
            <small>{{ request.applicant }} -> {{ request.approver }} / {{ request.submittedAt }}</small>
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>审计日志</h2>
            <p>所有配置导出、审批和后续修改都必须能关联证据和 traceId。</p>
          </div>
          <StatusBadge :label="`${workspace.summary.auditEvents} 条事件`" tone="blue" />
        </div>
        <div class="audit-timeline">
          <article v-for="event in workspace.auditEvents" :key="event.eventId">
            <time>{{ event.occurredAt }}</time>
            <div>
              <strong>{{ event.operation }}</strong>
              <span>{{ event.targetKey }} / {{ event.operator }} / {{ event.result }}</span>
              <small>{{ event.evidence }} / {{ event.traceId }}</small>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>系统参数</h2>
            <p>影响库存、损耗、工资和视频的参数不允许散落在代码里。</p>
          </div>
        </div>
        <div class="config-table">
          <div class="config-row config-head">
            <span>参数</span>
            <span>值</span>
            <span>等级</span>
            <span>说明</span>
            <span>变更控制</span>
          </div>
          <div v-for="parameter in workspace.systemParameters" :key="parameter.key" class="config-row">
            <strong>{{ parameter.key }}</strong>
            <span>{{ parameter.value }}</span>
            <StatusBadge :label="parameter.level" :tone="levelTone(parameter.level)" />
            <span>{{ parameter.description }} / {{ parameter.effectiveScope }}</span>
            <StatusBadge :label="parameter.changeControl" :tone="changeTone(parameter.changeControl)" />
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>编码规则</h2>
            <p>编码生成后不可修改，作废单据编码不复用。</p>
          </div>
        </div>
        <div class="rule-stack">
          <article v-for="rule in workspace.codeRules" :key="rule.objectType">
            <div>
              <strong>{{ rule.objectType }}</strong>
              <span>{{ rule.format }}</span>
            </div>
            <small>{{ rule.example }} / {{ rule.uniqueness }}</small>
          </article>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>角色权限基线</h2>
          <p>敏感动作必须进入审计范围，不能只依靠页面按钮隐藏。</p>
        </div>
      </div>
      <div class="role-card-grid">
        <article v-for="role in workspace.rolePermissions" :key="role.role">
          <div class="role-card-head">
            <div>
              <strong>{{ role.role }}</strong>
              <span>{{ role.dataScope }}</span>
            </div>
            <StatusBadge :label="`${role.sensitiveActions.length} 个敏感动作`" tone="amber" />
          </div>
          <div class="chip-row">
            <span v-for="permission in role.permissions" :key="permission">{{ permission }}</span>
          </div>
          <small>敏感：{{ role.sensitiveActions.join(' / ') }}</small>
        </article>
      </div>
    </section>

    <section class="split-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>工序模板</h2>
            <p>模板变更只影响新工单，已生成工序实例保留快照字段。</p>
          </div>
        </div>
        <div class="template-grid">
          <article v-for="template in workspace.processTemplates" :key="template.code">
            <small>{{ template.code }}</small>
            <strong>{{ template.name }}</strong>
            <span>{{ template.materialScope }}</span>
            <div class="process-chain">
              <b v-for="step in template.steps" :key="step">{{ step }}</b>
            </div>
            <p>{{ template.lossRule }}</p>
            <p>{{ template.wageRule }}</p>
            <p>{{ template.qualityRule }}</p>
            <StatusBadge :label="template.snapshotFields" tone="blue" />
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>仓库与库存状态</h2>
            <p>客户所有权、批次、状态和位置共同构成库存查询维度。</p>
          </div>
        </div>
        <div class="warehouse-grid">
          <article v-for="state in workspace.warehouseStates" :key="state.code">
            <div>
              <small>{{ state.code }}</small>
              <strong>{{ state.name }}</strong>
              <span>{{ state.transitionRule }}</span>
            </div>
            <StatusBadge :label="state.negativeAllowed ? '允许负库存' : '禁止负库存'" :tone="state.negativeAllowed ? 'red' : 'green'" />
          </article>
        </div>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>视频参数与兜底</h2>
            <p>视频失败不能卡死业务，但必须在追溯链路上显示证据状态。</p>
          </div>
        </div>
        <div class="config-table video-config-table">
          <div class="config-row config-head">
            <span>参数</span>
            <span>值</span>
            <span>场景</span>
            <span>兜底</span>
            <span>变更</span>
          </div>
          <div v-for="parameter in workspace.videoParameters" :key="parameter.key" class="config-row">
            <strong>{{ parameter.key }}</strong>
            <span>{{ parameter.value }}</span>
            <span>{{ parameter.scene }}</span>
            <span>{{ parameter.fallback }}</span>
            <StatusBadge :label="parameter.changeControl" :tone="changeTone(parameter.changeControl)" />
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>组件与备份</h2>
            <p>开源组件版本、许可证和备份策略要在试点前冻结。</p>
          </div>
        </div>
        <div class="rule-stack">
          <article v-for="component in workspace.components" :key="component.component">
            <div>
              <strong>{{ component.component }}</strong>
              <span>{{ component.version }} / {{ component.license }}</span>
            </div>
            <small>{{ component.purpose }} / {{ component.risk }}</small>
          </article>
          <article v-for="policy in workspace.backupPolicies" :key="policy.target">
            <div>
              <strong>{{ policy.target }}</strong>
              <span>{{ policy.strategy }}</span>
            </div>
            <small>{{ policy.rpo }} / {{ policy.rto }} / {{ policy.owner }}</small>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>
