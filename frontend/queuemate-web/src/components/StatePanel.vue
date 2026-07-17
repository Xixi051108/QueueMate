<script setup>
import { Box, CircleClose } from '@element-plus/icons-vue'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  error: { type: Boolean, default: false },
})

defineEmits(['retry'])
</script>

<template>
  <section class="state-panel surface" role="status">
    <el-icon class="state-icon" :class="{ error }" aria-hidden="true">
      <CircleClose v-if="error" />
      <Box v-else />
    </el-icon>
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
    <el-button v-if="error" @click="$emit('retry')">重新加载</el-button>
    <slot />
  </section>
</template>

<style scoped>
.state-panel { display: grid; min-height: 220px; place-items: center; align-content: center; gap: 10px; padding: 32px; text-align: center; }
.state-icon { font-size: 34px; color: var(--qm-primary-600); }
.state-icon.error { color: var(--qm-danger-700); }
h2 { margin: 0; font-size: 18px; }
p { max-width: 520px; margin: 0; color: var(--qm-ink-500); line-height: 1.6; }
</style>
