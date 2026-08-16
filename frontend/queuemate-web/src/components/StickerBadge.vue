<script setup>
defineProps({
  text: { type: String, required: true },
  tone: {
    type: String,
    default: 'sun',
    validator: (value) => ['sun', 'coral', 'mint', 'sky'].includes(value),
  },
  icon: {
    type: String,
    default: 'spark',
    validator: (value) => ['spark', 'ticket', 'route'].includes(value),
  },
  tilt: {
    type: String,
    default: 'left',
    validator: (value) => ['left', 'right', 'none'].includes(value),
  },
})
</script>

<template>
  <span class="sticker-badge" :class="[`sticker-badge--${tone}`, `sticker-badge--${tilt}`]" aria-hidden="true">
    <svg v-if="icon === 'spark'" viewBox="0 0 24 24" focusable="false">
      <path d="M12 2.8c.7 5.2 4 8.5 9.2 9.2-5.2.7-8.5 4-9.2 9.2-.7-5.2-4-8.5-9.2-9.2 5.2-.7 8.5-4 9.2-9.2Z" />
    </svg>
    <svg v-else-if="icon === 'ticket'" viewBox="0 0 24 24" focusable="false">
      <path d="M4 6.5h16v4a2.4 2.4 0 0 0 0 4.8v2.2H4v-2.2a2.4 2.4 0 0 0 0-4.8v-4Z" />
      <path class="sticker-line" d="M9 8.8v6.4" />
    </svg>
    <svg v-else viewBox="0 0 24 24" focusable="false">
      <circle cx="5" cy="17.5" r="2.2" />
      <circle cx="19" cy="6.5" r="2.2" />
      <path class="sticker-line" d="M7.2 17.5c7.8 0 2.4-11 9.6-11" />
    </svg>
    <span>{{ text }}</span>
  </span>
</template>

<style scoped>
.sticker-badge {
  --sticker-bg: var(--qm-sticker-sun);
  display: inline-flex;
  width: max-content;
  max-width: 100%;
  min-height: 34px;
  align-items: center;
  gap: 7px;
  padding: 6px 11px;
  border: 2px solid var(--qm-ink-900);
  border-radius: 10px 4px 9px 5px;
  background: var(--sticker-bg);
  box-shadow: var(--qm-shadow-sticker-sm);
  color: var(--qm-ink-900);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .04em;
  line-height: 1.15;
}
.sticker-badge--coral { --sticker-bg: var(--qm-sticker-coral); }
.sticker-badge--mint { --sticker-bg: var(--qm-sticker-mint); }
.sticker-badge--sky { --sticker-bg: var(--qm-sticker-sky); }
.sticker-badge--left { transform: rotate(-2.5deg); }
.sticker-badge--right { transform: rotate(2.5deg); }
.sticker-badge--none { transform: none; }
svg { width: 16px; height: 16px; flex: 0 0 auto; overflow: visible; fill: var(--qm-ink-900); }
.sticker-line { fill: none; stroke: var(--qm-ink-900); stroke-linecap: round; stroke-width: 1.8; }
</style>
