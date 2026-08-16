<script setup>
import { ArrowRight, Calendar, Location, Tickets } from '@element-plus/icons-vue'
import { labelOf } from '../utils/format'

defineProps({
  venue: { type: Object, required: true },
  linkQuery: { type: Object, default: () => ({}) },
})
</script>

<template>
  <article class="venue-card surface">
    <div class="venue-card__topline">
      <span class="category">{{ labelOf(venue.category) }}</span>
      <el-tag :type="venue.status === 'ACTIVE' ? 'success' : 'info'" effect="light" round>
        {{ labelOf(venue.status) }}
      </el-tag>
    </div>
    <div>
      <h2>{{ venue.name }}</h2>
      <p class="description">{{ venue.description || '暂无场所介绍' }}</p>
    </div>
    <div class="address">
      <el-icon aria-hidden="true"><Location /></el-icon>
      <span>{{ venue.addressText }}</span>
    </div>
    <div class="services" aria-label="可用服务">
      <span :class="{ enabled: venue.bookingEnabled }"><el-icon><Calendar /></el-icon>预约</span>
      <span :class="{ enabled: venue.queueEnabled }"><el-icon><Tickets /></el-icon>排队</span>
    </div>
    <RouterLink
      class="card-link"
      :to="{ name: 'venue-detail', params: { id: venue.id }, query: linkQuery }"
      :aria-label="`查看${venue.name}详情`"
    >
      查看时段与进度
      <el-icon aria-hidden="true"><ArrowRight /></el-icon>
    </RouterLink>
  </article>
</template>

<style scoped>
.venue-card { position: relative; display: grid; min-height: 296px; grid-template-rows: auto 1fr auto auto auto; gap: 16px; padding: 22px; overflow: hidden; transition: border-color var(--qm-motion-fast), box-shadow var(--qm-motion-fast), transform var(--qm-motion-fast); }
.venue-card::after { position: absolute; right: -28px; bottom: -30px; width: 76px; height: 76px; border: 2px solid var(--qm-ink-900); border-radius: 50%; background: var(--qm-sticker-mint); content: ""; opacity: .72; }
.venue-card:hover { border-color: var(--qm-ink-900); box-shadow: var(--qm-shadow-sticker-sm); transform: translateY(-2px) rotate(-.25deg); }
.venue-card__topline { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.category { padding: 4px 9px; transform: rotate(-1.5deg); border: 1.5px solid var(--qm-ink-900); border-radius: 8px 3px 8px 4px; background: var(--qm-sticker-sky); color: var(--qm-ink-900); font-size: 12px; font-weight: 800; }
h2 { margin: 0 0 8px; font-size: 19px; line-height: 1.4; }
.description { display: -webkit-box; margin: 0; overflow: hidden; color: var(--qm-ink-500); line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.address { display: flex; align-items: flex-start; gap: 8px; color: var(--qm-ink-700); font-size: 14px; line-height: 1.5; }
.address .el-icon { flex: 0 0 auto; margin-top: 3px; color: var(--qm-primary-600); }
.services { display: flex; gap: 16px; border-top: 1px dashed var(--qm-line-300); padding-top: 14px; }
.services span { display: inline-flex; align-items: center; gap: 5px; color: var(--qm-ink-500); font-size: 13px; }
.services span.enabled { color: var(--qm-success-700); font-weight: 600; }
.card-link { position: relative; z-index: 1; display: flex; min-height: 44px; align-items: center; justify-content: space-between; font-size: 14px; font-weight: 800; }
@media (prefers-reduced-motion: reduce) { .venue-card:hover { transform: none; } }
</style>
