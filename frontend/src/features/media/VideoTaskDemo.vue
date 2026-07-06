<script setup lang="ts">
const sampleTask = {
  bizType: 'inbound_unpack',
  bizCode: 'CC-20260706-001-JL',
  cameraCode: 'CAM-UNPACK-01',
  startAt: '2026-07-06T01:00:00Z',
  endAt: '2026-07-06T01:05:00Z',
  bufferSeconds: 5,
};
</script>

<template>
  <section class="panel">
    <div class="panel-heading">
      <h2>视频截取任务</h2>
      <span class="status-pill">async</span>
    </div>
    <p class="muted">一期按业务时间段创建视频任务，后台用 NVR/FFmpeg 截取，失败后允许人工上传。</p>
    <pre class="result-box">{{ sampleTask }}</pre>
  </section>
</template>
