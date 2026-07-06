<script setup lang="ts">
import { onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import {
  configurationBaselineFallback,
  configurationToneByLevel,
  type ConfigurationBaselineWorkspace,
} from '../../data/configurationBaselineData';
import type { Tone } from '../../data/m1PrototypeData';
import { fetchConfigurationBaselineWorkspace } from '../../services/configurationBaselineApi';

const workspace = ref<ConfigurationBaselineWorkspace>(configurationBaselineFallback);

onMounted(async () => {
  try {
    workspace.value = await fetchConfigurationBaselineWorkspace();
  } catch {
    // Keep the review baseline visible when the backend is not running.
  }
});

function levelTone(level: string): Tone {
  return configurationToneByLevel[level] ?? 'gray';
}

function changeTone(changeControl: string): Tone {
  if (changeControl.includes('审批')) return 'red';
  if (changeControl.includes('日志')) return 'amber';
  return 'blue';
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
