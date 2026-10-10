<script setup lang="ts">
defineProps<{
  name: string;
}>();
</script>

<template>
  <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
    <path v-if="name === 'dashboard'" d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1z" />
    <template v-else-if="name === 'inbound'">
      <path d="M3 8.5 12 4l9 4.5-9 4.5-9-4.5z" />
      <path d="M3 8.5V16l9 4.5L21 16V8.5" />
      <path d="M12 13v7.5" />
    </template>
    <template v-else-if="name === 'progress'">
      <path d="M5 6h14M5 12h14M5 18h9" />
    </template>
    <template v-else-if="name === 'production'">
      <path d="M3 21V10l6 3.5V10l6 3.5V8h6v13" />
      <path d="M3 21h18" />
    </template>
    <template v-else-if="name === 'quality'">
      <path d="M12 3.5 19 6.5v5.2c0 4.2-2.8 7-7 8.8-4.2-1.8-7-4.6-7-8.8V6.5z" />
      <path d="M9 12.2 11 14l4-4.2" />
    </template>
    <template v-else-if="name === 'finance'">
      <path d="M6 4.5h12v15H6z" />
      <path d="M9 8.5h6M9 12h6M9 15.5h4" />
    </template>
    <template v-else-if="name === 'inventory'">
      <path d="M12 3.5 20 7.5 12 11.5 4 7.5z" />
      <path d="M4 12 12 16l8-4" />
      <path d="M4 16.5 12 20.5l8-4" />
    </template>
    <template v-else-if="name === 'approvals'">
      <path d="M8 4.5h8a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V5.5a1 1 0 0 1 1-1z" />
      <path d="M9 4.5h6V7H9z" />
      <path d="M9 12.5 11 14.5 15 10.5" />
    </template>
    <template v-else-if="name === 'label'">
      <path d="M3.5 12.5V5.5h7l10 10-7 7z" />
      <circle class="is-fill" cx="8" cy="9" r="1.2" />
    </template>
    <template v-else-if="name === 'sample'">
      <path d="M6 3.5h8l4 4V20a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1z" />
      <path d="M14 3.5V8h4" />
      <path d="M8 12h8M8 16h5" />
    </template>
    <template v-else-if="name === 'documents'">
      <path d="M5 6.5h14v11H5z" />
      <path d="M8 14.5 10.2 12 13 14.5l1.6-1.6L17 15" />
      <circle class="is-fill" cx="9" cy="10" r="1" />
    </template>
    <template v-else-if="name === 'hardware'">
      <path d="M8 8h8v8H8z" />
      <path d="M10 4.5V8M14 4.5V8M10 16v3.5M14 16v3.5M4.5 10H8M4.5 14H8M16 10h3.5M16 14h3.5" />
    </template>
    <template v-else-if="name === 'configuration'">
      <path d="M4 7h16M4 12h16M4 17h16" />
      <circle cx="8" cy="7" r="1.7" />
      <circle cx="15" cy="12" r="1.7" />
      <circle cx="10" cy="17" r="1.7" />
    </template>
    <path v-else-if="name === 'operations'" d="M3 12h4l2.2-5 3.6 10L15 12h6" />
    <template v-else-if="name === 'mobile'">
      <path d="M8 3.5h8a1 1 0 0 1 1 1v15a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1z" />
      <path d="M11 18.5h2" />
    </template>
    <circle v-else cx="12" cy="12" r="3" />
  </svg>
</template>
