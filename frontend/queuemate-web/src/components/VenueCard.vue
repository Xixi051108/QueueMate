<script setup>
import { ArrowRight, Calendar, Location, Tickets } from '@element-plus/icons-vue'
import { labelOf } from '../utils/format'

defineProps({ venue: { type: Object, required: true } })
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
    <RouterLink class="card-link" :to="`/venues/${venue.id}`" :aria-label="`查看${venue.name}详情`">
      查看时段与进度
      <el-icon aria-hidden="true"><ArrowRight /></el-icon>
    </RouterLink>
  </article>
</template>

<style scoped>
.venue-card { display: grid; min-height: 296px; grid-template-rows: auto 1fr auto auto auto; gap: 16px; padding: 22px; transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease; }
.venue-card:hover { border-color: var(--qm-line-300); box-shadow: var(--qm-shadow-raised); transform: translateY(-2px); }
.venue-card__topline { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.category { color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
h2 { margin: 0 0 8px; font-size: 19px; line-height: 1.4; }
.description { display: -webkit-box; margin: 0; overflow: hidden; color: var(--qm-ink-500); line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.address { display: flex; align-items: flex-start; gap: 8px; color: var(--qm-ink-700); font-size: 14px; line-height: 1.5; }
.address .el-icon { flex: 0 0 auto; margin-top: 3px; color: var(--qm-primary-600); }
.services { display: flex; gap: 16px; border-top: 1px dashed var(--qm-line-300); padding-top: 14px; }
.services span { display: inline-flex; align-items: center; gap: 5px; color: var(--qm-ink-500); font-size: 13px; }
.services span.enabled { color: var(--qm-success-700); font-weight: 600; }
.card-link { display: flex; min-height: 44px; align-items: center; justify-content: space-between; font-size: 14px; font-weight: 700; }
@media (prefers-reduced-motion: reduce) { .venue-card:hover { transform: none; } }
</style>
