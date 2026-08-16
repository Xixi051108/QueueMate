<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import VenueCard from '../components/VenueCard.vue'
import StatePanel from '../components/StatePanel.vue'
import { VENUE_CATEGORIES } from '../constants/venue'
import { venueApi } from '../services/api'
import { labelOf } from '../utils/format'

const route = useRoute()
const router = useRouter()
const PAGE_SIZE = 9
const loading = ref(true)
const error = ref('')
const venues = ref([])
const total = ref(0)
const currentPage = ref(1)
const filters = reactive({ keyword: '', category: '', status: 'ACTIVE' })

function queryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function restoreFiltersFromRoute() {
  const keyword = queryValue(route.query.keyword)
  const category = queryValue(route.query.category)
  const status = queryValue(route.query.status)

  filters.keyword = typeof keyword === 'string' ? keyword : ''
  filters.category = VENUE_CATEGORIES.includes(category) ? category : ''
  filters.status = status === 'ALL' ? '' : status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const page = Number.parseInt(queryValue(route.query.page), 10)
  currentPage.value = Number.isSafeInteger(page) && page > 0 ? page : 1
}

function filtersToQuery(page = 1) {
  const query = {}
  const keyword = filters.keyword.trim()
  if (keyword) query.keyword = keyword
  if (filters.category) query.category = filters.category
  if (filters.status === 'INACTIVE') query.status = 'INACTIVE'
  if (!filters.status) query.status = 'ALL'
  if (page > 1) query.page = String(page)
  return query
}

async function loadVenues() {
  loading.value = true
  error.value = ''
  try {
    const result = await venueApi.page({
      keyword: filters.keyword.trim() || undefined,
      category: filters.category || undefined,
      status: filters.status || undefined,
      page: currentPage.value,
      pageSize: PAGE_SIZE,
    })
    if (result.totalPages > 0 && currentPage.value > result.totalPages) {
      await navigateToPage(result.totalPages)
      return
    }
    venues.value = result.items
    total.value = result.total
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function navigateToPage(page) {
  const target = router.resolve({ name: 'venues', query: filtersToQuery(page) })
  if (target.fullPath === route.fullPath) {
    await loadVenues()
    return
  }
  await router.push(target)
}

async function applyFilters() {
  await navigateToPage(1)
}

async function reset() {
  Object.assign(filters, { keyword: '', category: '', status: 'ACTIVE' })
  await applyFilters()
}

async function changePage(page) {
  await navigateToPage(page)
}

watch(
  () => route.fullPath,
  async () => {
    restoreFiltersFromRoute()
    await loadVenues()
  },
  { immediate: true },
)
</script>

<template>
  <div class="page">
    <header class="page-heading" data-sticker="现在出发">
      <div>
        <h1>找到下一站</h1>
        <p>浏览可预约、可现场排队的本地模拟场所。</p>
      </div>
      <span v-if="!loading" class="result-count data-value">{{ total }} 个地点</span>
    </header>

    <form class="filters surface" aria-label="筛选地点" @submit.prevent="applyFilters">
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
      <VenueCard v-for="venue in venues" :key="venue.id" :venue="venue" :link-query="route.query" />
    </section>
    <nav v-if="!loading && !error && total > PAGE_SIZE" class="pagination" aria-label="地点列表分页">
      <el-pagination
        background
        layout="prev, pager, next"
        prev-text="上一页"
        next-text="下一页"
        :current-page="currentPage"
        :page-size="PAGE_SIZE"
        :pager-count="5"
        :total="total"
        @current-change="changePage"
      />
    </nav>
  </div>
</template>

<style scoped>
.result-count { color: var(--qm-ink-500); font-size: 14px; }
.venue-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
.pagination { display: flex; justify-content: center; padding: 10px 0 4px; }
.pagination :deep(.btn-prev),
.pagination :deep(.btn-next),
.pagination :deep(.el-pager li) { min-width: 44px; height: 44px; }
.pagination :deep(.btn-prev),
.pagination :deep(.btn-next) { padding: 0 14px; font-weight: 700; }
@media (max-width: 1023px) { .venue-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) {
  .venue-grid { grid-template-columns: 1fr; }
  .pagination :deep(.btn-prev),
  .pagination :deep(.btn-next),
  .pagination :deep(.el-pager li) { margin: 0 2px; }
}
@media (max-width: 480px) {
  .pagination :deep(.el-pager li:not(.is-active)) { display: none; }
}
</style>
