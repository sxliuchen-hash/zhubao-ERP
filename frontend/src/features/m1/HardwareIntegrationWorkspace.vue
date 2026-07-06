<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import {
  getHardwarePocBlockedCount,
  hardwareDevices,
  hardwarePocRecords,
  integrationTasks,
} from '../../data/m1PrototypeData';

function deviceTone(status: string) {
  if (status === 'online') return 'green';
  if (status === 'degraded') return 'amber';
  return 'gray';
}

function deviceLabel(status: string) {
  if (status === 'online') return '在线';
  if (status === 'degraded') return '需验证';
  return '人工兜底';
}
</script>

<template>
  <div class="page-stack">
    <section class="operations-hero hardware-hero">
      <div class="hero-copy">
        <span class="eyebrow">Hardware Link</span>
        <h2>软硬件联动指挥台</h2>
        <p>扫码、标签、视频、称重按兼容方案先上线；失败任务进入兜底队列，业务单据继续闭环。</p>
      </div>
      <div class="hero-kpis">
        <article>
          <span>在线设备</span>
          <strong>1</strong>
        </article>
        <article>
          <span>人工兜底</span>
          <strong>2</strong>
        </article>
        <article>
          <span>POC 阻断</span>
          <strong>{{ getHardwarePocBlockedCount() }}</strong>
        </article>
      </div>
    </section>

    <section class="hardware-matrix-layout">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>设备兼容矩阵</h2>
            <p>按设备、协议、现场动作和兜底方式管理，不绑定具体型号。</p>
          </div>
          <button class="secondary-button" type="button">导出设备清单</button>
        </div>
        <div class="device-ops-table">
          <div class="device-ops-row device-ops-head">
            <span>设备</span>
            <span>场景</span>
            <span>协议</span>
            <span>状态</span>
            <span>失败兜底</span>
          </div>
          <div v-for="device in hardwareDevices" :key="device.code" class="device-ops-row">
            <div>
              <small>{{ device.code }}</small>
              <strong>{{ device.name }}</strong>
            </div>
            <span>{{ device.scene }}</span>
            <span>{{ device.protocol }}</span>
            <StatusBadge :label="deviceLabel(device.status)" :tone="deviceTone(device.status)" />
            <span>{{ device.fallback }}</span>
          </div>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>联动边界</h2>
            <p>一期只承诺业务可用和证据可追溯。</p>
          </div>
        </div>
        <div class="boundary-list">
          <article>
            <strong>视频</strong>
            <span>NVR 时间段截取优先，失败后人工上传 MP4。</span>
          </article>
          <article>
            <strong>标签</strong>
            <span>浏览器打印优先，PDF 标签模板兜底。</span>
          </article>
          <article>
            <strong>称重</strong>
            <span>一期人工读数录入，二期再接 RS232/USB/TCP。</span>
          </article>
          <article>
            <strong>扫码</strong>
            <span>扫码失败允许手工输入编码并二次确认。</span>
          </article>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>现场 POC 记录</h2>
          <p>每次硬件验证必须记录时间窗口、结果、证据、阻断项和下一步，避免只用口头结论推进集成。</p>
        </div>
        <button class="secondary-button" type="button">导出 POC 记录</button>
      </div>
      <div class="poc-record-grid">
        <article v-for="record in hardwarePocRecords" :key="record.code" :class="`tone-${record.tone}`">
          <div class="poc-record-head">
            <div>
              <small>{{ record.code }} / {{ record.deviceCode }}</small>
              <strong>{{ record.scene }}</strong>
            </div>
            <StatusBadge :label="record.result" :tone="record.tone" />
          </div>
          <dl>
            <dt>窗口</dt>
            <dd>{{ record.testWindow }}</dd>
            <dt>证据</dt>
            <dd>{{ record.evidence }}</dd>
            <dt>阻断</dt>
            <dd>{{ record.blocker }}</dd>
            <dt>负责人</dt>
            <dd>{{ record.owner }}</dd>
          </dl>
          <p>{{ record.nextAction }}</p>
        </article>
      </div>
    </section>

    <section class="split-layout wide-left">
      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>联动任务健康度</h2>
            <p>把硬件调用、失败处理和人工上传都纳入统一任务队列。</p>
          </div>
        </div>
        <div class="integration-list">
          <article v-for="task in integrationTasks" :key="task.id">
            <div class="integration-main">
              <strong>{{ task.title }}</strong>
              <span>{{ task.target }}</span>
              <small>{{ task.lastRun }} / {{ task.owner }}</small>
            </div>
            <div class="health-meter">
              <span>{{ task.health }}%</span>
              <i><b :style="{ width: `${task.health}%` }" /></i>
            </div>
            <p>{{ task.nextAction }}</p>
          </article>
        </div>
      </div>

      <div class="section-block">
        <div class="section-heading">
          <div>
            <h2>部署拓扑</h2>
            <p>最小部署先跑通单体服务、数据库、文件存储和现场设备。</p>
          </div>
        </div>
        <div class="topology-map">
          <span>PC 后台</span>
          <span>PWA 手机</span>
          <strong>Spring Boot API</strong>
          <span>MySQL</span>
          <span>NAS/对象存储</span>
          <span>NVR/摄像头</span>
          <span>标签打印</span>
          <span>扫码枪/电子秤</span>
        </div>
      </div>
    </section>
  </div>
</template>
