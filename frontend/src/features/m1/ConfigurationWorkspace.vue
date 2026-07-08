<script setup lang="ts">
import { onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  authAccountSummaryFallback,
  configurationBaselineFallback,
  configurationToneByLevel,
  type ConfigurationBaselineWorkspace,
} from '../../data/configurationBaselineData';
import type { Tone } from '../../data/m1PrototypeData';
import {
  changeOwnPassword,
  fetchAuthAuditIntegrity,
  fetchAuthAuditEvents,
  fetchAuthAccounts,
  fetchAuthDemoUsers,
  fetchAuthSession,
  loginAuthSession,
  logoutAuthSession,
  type AuthAuditEvent,
  type AuthAuditIntegritySummary,
  type AuthSession,
  type AuthUserAccountSummary,
} from '../../services/authApi';
import { fetchConfigurationBaselineWorkspace } from '../../services/configurationBaselineApi';

const workspace = ref<ConfigurationBaselineWorkspace>(configurationBaselineFallback);
const authSession = ref<AuthSession | null>(null);
const authDemoUsers = ref<string[]>(['leader', 'operator', 'warehouse', 'qc', 'admin']);
const authUsername = ref('leader');
const authPassword = ref('leader123');
const authBusy = ref(false);
const authMessage = ref('');
const passwordCurrent = ref('');
const passwordNew = ref('');
const passwordConfirm = ref('');
const passwordBusy = ref(false);
const passwordMessage = ref('');
const authAccounts = ref<AuthUserAccountSummary[]>(authAccountSummaryFallback);
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
    previousEventHash: '',
    eventHash: 'local-demo-integrity-hash',
  },
]);
const authAuditIntegrity = ref<AuthAuditIntegritySummary>({
  checkedEvents: 1,
  verified: true,
  latestEventId: 'AUTH-AUD-DEMO-001',
  latestEventHash: 'local-demo-integrity-hash',
  brokenEventId: '',
  message: '本地审计样例，后端启动后返回真实摘要链',
});

onMounted(async () => {
  await refreshWorkspace();
  await restoreAuthSession();
  await loadDemoUsers();
  await loadAuthAccounts();
  await loadAuthAuditEvents();
  await loadAuthAuditIntegrity();
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

async function loadAuthAuditIntegrity() {
  try {
    authAuditIntegrity.value = await fetchAuthAuditIntegrity();
  } catch {
    // Keep local audit integrity sample visible when the backend is not running.
  }
}

async function loadAuthAccounts() {
  try {
    authAccounts.value = await fetchAuthAccounts();
  } catch {
    // Keep local account boundary samples visible when the backend is not running.
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
    await loadAuthAccounts();
    await loadAuthAuditEvents();
    await loadAuthAuditIntegrity();
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
    await loadAuthAccounts();
    await loadAuthAuditEvents();
    await loadAuthAuditIntegrity();
  } finally {
    authBusy.value = false;
  }
}

async function handleChangePassword() {
  passwordBusy.value = true;
  passwordMessage.value = '';
  try {
    if (!authSession.value) {
      passwordMessage.value = '请先登录后再自助改密';
      return;
    }
    if (passwordNew.value !== passwordConfirm.value) {
      passwordMessage.value = '两次新密码不一致';
      return;
    }
    authSession.value = await changeOwnPassword({
      currentPassword: passwordCurrent.value,
      newPassword: passwordNew.value,
    });
    passwordCurrent.value = '';
    passwordNew.value = '';
    passwordConfirm.value = '';
    passwordMessage.value = `自助改密成功，密码到期 ${authSession.value.passwordExpiresAt}`;
    await refreshWorkspace();
    await loadAuthAccounts();
    await loadAuthAuditEvents();
    await loadAuthAuditIntegrity();
  } catch (error) {
    passwordMessage.value = error instanceof Error ? error.message : '自助改密失败';
  } finally {
    passwordBusy.value = false;
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

function accountStatusLabel(account: AuthUserAccountSummary): string {
  if (!account.enabled) return '停用';
  if (account.passwordExpired) return '密码过期';
  if (account.locked) return '锁定';
  return '启用';
}

function accountStatusTone(account: AuthUserAccountSummary): Tone {
  if (!account.enabled) return 'gray';
  if (account.passwordExpired) return 'red';
  if (account.locked) return 'red';
  if (account.failedLoginCount > 0) return 'amber';
  return 'green';
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
          <StatusBadge :label="workspace.securityContext.headerBased ? '请求头上下文' : '登录会话上下文'" tone="amber" />
        </div>

        <form class="auth-session-strip" aria-label="轻量登录会话" @submit.prevent="handleLogin">
          <div>
            <span>登录会话</span>
            <strong>{{ authSession ? `${authSession.operator} / ${authSession.roleLabel}` : '未登录，使用请求头兜底' }}</strong>
            <small>
              {{
                authSession
                  ? `会话 ${authSession.expiresAt} / 密码到期 ${authSession.passwordExpiresAt || '未记录'}`
                  : 'Bearer token 会自动写入后续 API 请求'
              }}
            </small>
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

        <form class="auth-password-strip" aria-label="自助改密续期" @submit.prevent="handleChangePassword">
          <div>
            <span>自助改密 / 续期</span>
            <strong>{{ authSession ? '登录态内刷新 passwordChangedAt' : '需先建立登录会话' }}</strong>
            <small>仅接受 Bearer token，不使用请求头角色兜底；新密码沿用生产密码策略。</small>
          </div>
          <label>
            <span>当前密码</span>
            <input
              v-model="passwordCurrent"
              :disabled="passwordBusy || !authSession"
              type="password"
              autocomplete="current-password"
            />
          </label>
          <label>
            <span>新密码</span>
            <input
              v-model="passwordNew"
              :disabled="passwordBusy || !authSession"
              type="password"
              autocomplete="new-password"
            />
          </label>
          <label>
            <span>确认新密码</span>
            <input
              v-model="passwordConfirm"
              :disabled="passwordBusy || !authSession"
              type="password"
              autocomplete="new-password"
            />
          </label>
          <div class="auth-session-actions">
            <button class="primary-button" type="submit" :disabled="passwordBusy || !authSession">
              {{ passwordBusy ? '处理中' : '更新密码' }}
            </button>
          </div>
          <small v-if="passwordMessage" class="auth-session-message">{{ passwordMessage }}</small>
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
          <span>
            {{ workspace.securityContext.dataBoundary }}
            <template v-if="workspace.securityContext.customerScope.length">
              / {{ workspace.securityContext.customerScope.join('、') }}
            </template>
          </span>
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
          <span>{{ workspace.securityContext.headerContract.customerScopeHeader }}</span>
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
          <div class="security-audit-integrity">
            <span>完整性摘要</span>
            <b>{{ authAuditIntegrity.verified ? '摘要链通过' : '摘要链异常' }}</b>
            <small>
              {{ authAuditIntegrity.message }} · 检查 {{ authAuditIntegrity.checkedEvents }} 条
              · 最新 {{ authAuditIntegrity.latestEventId || 'none' }}
              · hash *{{ authAuditIntegrity.latestEventHash ? authAuditIntegrity.latestEventHash.slice(-8) : 'none' }}
            </small>
          </div>
          <article v-for="event in authAuditEvents.slice(0, 4)" :key="event.eventId">
            <b>{{ event.eventType }} / {{ event.result }}</b>
            <span>{{ event.eventId }} · {{ event.username || 'unknown' }} / {{ event.role || '未识别角色' }}</span>
            <small>
              {{ event.reason }} · {{ event.occurredAt }}
              · token *{{ event.sessionTokenSuffix || 'none' }}
              · hash *{{ event.eventHash ? event.eventHash.slice(-8) : 'none' }}
            </small>
          </article>
        </div>
        <div class="security-account-contract">
          <strong>账号管理边界</strong>
          <article v-for="account in authAccounts" :key="account.username">
            <div>
              <b>{{ account.username }} / {{ account.operator }}</b>
              <span>{{ account.roleLabel }} · {{ account.dataScopeLabel }}</span>
              <small>
                {{ account.demoAccount ? '评审 seed' : '生产账号' }} · 失败 {{ account.failedLoginCount }} 次
                <template v-if="account.customerScope.length"> · 客户 {{ account.customerScope.join('、') }}</template>
                <template v-if="account.lockedUntil"> · 锁定至 {{ account.lockedUntil }}</template>
                <template v-if="account.passwordChangedAt"> · 改密 {{ account.passwordChangedAt }}</template>
                <template v-if="account.passwordExpiresAt"> · 到期 {{ account.passwordExpiresAt }}</template>
                <template v-if="account.passwordExpired"> · 密码已过期</template>
              </small>
            </div>
            <StatusBadge :label="accountStatusLabel(account)" :tone="accountStatusTone(account)" />
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
