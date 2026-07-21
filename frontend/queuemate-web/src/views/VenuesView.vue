<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import VenueCard from '../components/VenueCard.vue'
import StatePanel from '../components/StatePanel.vue'
import { VENUE_CATEGORIES } from '../constants/venue'
import { venueApi } from '../services/api'
import { labelOf } from '../utils/format'

const loading = ref(true)
const error = ref('')
const venues = ref([])
const filters = reactive({ keyword: '', category: '', status: 'ACTIVE' })

async function loadVenues() {
  loading.value = true
  error.value = ''
  try {
    venues.value = await venueApi.list({
      keyword: filters.keyword.trim() || undefined,
      category: filters.category || undefined,
      status: filters.status || undefined,
    })
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(filters, { keyword: '', category: '', status: 'ACTIVE' })
  loadVenues()
}

onMounted(loadVenues)
</script>

<template>
  <div class="page">
    <header class="page-heading">
      <div>
        <h1>找到下一站</h1>
        <p>浏览可预约、可现场排队的本地模拟场所。</p>
      </div>
      <span v-if="!loading" class="result-count data-value">{{ venues.length }} 个地点</span>
    </header>

    <form class="filters surface" aria-label="筛选地点" @submit.prevent="loadVenues">
      <div class="filter-field filter-field--grow">
        <label class="field-label" for="venue-keyword">搜索地点</label>
        <el-input id="venue-keyword" v-model="filters.keyword" clearable placeholder="名称、地址或介绍" :prefix-icon="Search" />
      </div>
      <div class="filter-field">
        <label class="field-label" for="venue-category">场所类型</label>
        <el-select id="venue-category" v-model="filters.category" clearable placeholder="全部类型">
          <el-option v-for="item in VENUE_CATEGORIES" :key="item" :label="labelOf(item)" :value="item" />
        </el-select>
      </div>
      <div class="filter-field">
        <label class="field-label" for="venue-status">营业状态</label>
        <el-select id="venue-status" v-model="filters.status" clearable placeholder="全部状态">
          <el-option label="营业中" value="ACTIVE" />
          <el-option label="已停用" value="INACTIVE" />
        </el-select>
      </div>
      <el-button type="primary" native-type="submit" :loading="loading">查询地点</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
    </form>

    <el-skeleton v-if="loading" :rows="8" animated />
    <StatePanel v-else-if="error" title="地点加载失败" :description="error" error @retry="loadVenues" />
    <StatePanel v-else-if="!venues.length" title="没有找到地点" description="换一个关键词或清除筛选条件后再试。">
      <el-button type="primary" @click="reset">清除筛选</el-button>
    </StatePanel>
    <section v-else class="venue-grid" aria-label="地点列表">
      <VenueCard v-for="venue in venues" :key="venue.id" :venue="venue" />
    </section>
  </div>
</template>

<style scoped>
.result-count { color: var(--qm-ink-500); font-size: 14px; }
.venue-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
@media (max-width: 1023px) { .venue-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) { .venue-grid { grid-template-columns: 1fr; } }
</style>
