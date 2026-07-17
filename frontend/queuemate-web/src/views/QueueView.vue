<script setup>
import { computed, onMounted, ref } from 'vue'
import StatePanel from '../components/StatePanel.vue'
import { queueApi, venueApi } from '../services/api'
import { formatDateTime, isoDate, labelOf, statusType } from '../utils/format'

const loading = ref(true)
const error = ref('')
const tickets = ref([])
const venues = ref([])
const venueId = ref('')
const status = ref('')
const venueNames = computed(() => Object.fromEntries(venues.value.map((item) => [item.id, item.name])))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [ticketData, venueData] = await Promise.all([
      queueApi.mine({ venueId: venueId.value || undefined, status: status.value || undefined, queueDate: isoDate() }),
      venueApi.list({ status: 'ACTIVE' }),
    ])
    tickets.value = ticketData
    venues.value = venueData
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-heading"><div><h1>我的排队</h1><p>查看今天领取的号码和叫号状态。</p></div></header>
    <div class="filters surface"><div class="filter-field filter-field--grow"><label class="field-label" for="queue-venue">地点</label><el-select id="queue-venue" v-model="venueId" filterable clearable placeholder="全部地点" @change="load"><el-option v-for="venue in venues" :key="venue.id" :label="venue.name" :value="venue.id" /></el-select></div><div class="filter-field"><label class="field-label" for="queue-status">号码状态</label><el-select id="queue-status" v-model="status" clearable placeholder="全部状态" @change="load"><el-option v-for="item in ['WAITING','CALLED','COMPLETED','MISSED']" :key="item" :label="labelOf(item)" :value="item" /></el-select></div></div>
    <el-skeleton v-if="loading" :rows="8" animated />
    <StatePanel v-else-if="error" title="排队记录加载失败" :description="error" error @retry="load" />
    <StatePanel v-else-if="!tickets.length" title="今天还没有取号" description="前往支持现场排队的地点详情页领取号码。"><RouterLink to="/venues"><el-button type="primary">查找可排队地点</el-button></RouterLink></StatePanel>
    <section v-else class="queue-list" aria-label="我的排队号码">
      <article v-for="ticket in tickets" :key="ticket.id" class="queue-ticket surface"><div class="queue-number"><span>我的号码</span><strong class="data-value">{{ ticket.queueNo }}</strong></div><div class="queue-detail"><span>{{ venueNames[ticket.venueId] || `地点 ${ticket.venueId}` }}</span><h2>{{ labelOf(ticket.status) }}</h2><p>取号时间 {{ formatDateTime(ticket.takenAt) }}</p><p v-if="ticket.calledAt">叫号时间 {{ formatDateTime(ticket.calledAt) }}</p></div><el-tag :type="statusType(ticket.status)" round>{{ labelOf(ticket.status) }}</el-tag></article>
    </section>
  </div>
</template>

<style scoped>
.queue-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.queue-ticket { display: grid; grid-template-columns: 120px 1fr auto; align-items: center; gap: 22px; padding: 22px; }
.queue-number { display: grid; place-items: center; gap: 5px; min-height: 110px; border-right: 1px dashed var(--qm-line-300); color: var(--qm-primary-700); }
.queue-number span { font-size: 12px; } .queue-number strong { font-size: 48px; line-height: 1; }
.queue-detail span { color: var(--qm-primary-700); font-size: 13px; font-weight: 700; } .queue-detail h2 { margin: 7px 0; font-size: 19px; } .queue-detail p { margin: 2px 0; color: var(--qm-ink-500); font-size: 12px; }
@media (max-width: 900px) { .queue-list { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .queue-ticket { grid-template-columns: 90px 1fr; gap: 14px; } .queue-ticket > .el-tag { grid-column: 2; justify-self: start; } .queue-number strong { font-size: 40px; } }
</style>
