<script setup lang="ts">
import StatusBadge from '../../components/StatusBadge.vue';
import { hardwareDevices, integrationTasks } from '../../data/m1PrototypeData';

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
    <section class="command-hero hardware-hero">
      <div>
        <span class="eyebrow">Hardware Link</span>
        <h2>软硬件联动指挥台</h2>
        <p>一期按兼容方案落地：扫码、标签、视频、称重全部保留人工兜底，不让硬件集成卡死业务上线。</p>
      </div>
      <div class="signal-stack">
        <span>API</span>
        <i />
        <span>PWA</span>
        <i />
        <span>Device</span>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <h2>设备兼容矩阵</h2>
          <p>这里不绑定具体型号，先管理协议、现场场景、状态和失败兜底。</p>
        </div>
      </div>
      <div class="device-grid">
        <article v-for="device in hardwareDevices" :key="device.code" class="device-card">
          <div class="device-card-head">
            <div>
              <small>{{ device.code }}</small>
              <strong>{{ device.name }}</strong>
            </div>
            <StatusBadge :label="deviceLabel(device.status)" :tone="deviceTone(device.status)" />
          </div>
          <dl>
            <dt>场景</dt>
            <dd>{{ device.scene }}</dd>
            <dt>协议</dt>
            <dd>{{ device.protocol }}</dd>
            <dt>兜底</dt>
            <dd>{{ device.fallback }}</dd>
          </dl>
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
