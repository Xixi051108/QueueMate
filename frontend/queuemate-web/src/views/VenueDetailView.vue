<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Calendar, Location, Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatePanel from '../components/StatePanel.vue'
import { bookingApi, queueApi, venueApi } from '../services/api'
import { authState } from '../state/auth'
import { formatMoney, isoDate, labelOf } from '../utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const actionId = ref('')
const error = ref('')
const venue = ref(null)
const slots = ref([])
const queue = ref(null)

const isUser = computed(() => authState.role.value === 'USER')

async function load() {
  loading.value = true
  error.value = ''
  try {
    venue.value = await venueApi.get(route.params.id)
    const tasks = []
    if (venue.value.bookingEnabled) {
      tasks.push(venueApi.slots(route.params.id, { dateFrom: isoDate(), dateTo: isoDate(14), status: 'OPEN' }).then((data) => { slots.value = data }))
    }
    if (venue.value.queueEnabled) {
      tasks.push(queueApi.current(route.params.id).then((data) => { queue.value = data }))
    }
    await Promise.all(tasks)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function requireUser(target) {
  if (!authState.isAuthenticated.value) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return false
  }
  if (!isUser.value) {
    ElMessage.warning('当前角色不能使用用户预约或取号功能')
    return false
  }
  return target
}

async function book(slot) {
  if (!requireUser(true)) return
  const price = Number(slot.price || 0)
  const confirmed = await ElMessageBox.confirm(
    price > 0 ? `本次预约将从站内钱包支付 ${formatMoney(price)}。` : '这是一个免费时段，确认后将占用一个名额。',
    '确认预约',
    { confirmButtonText: '确认预约', cancelButtonText: '暂不预约', type: 'info' },
  ).then(() => true).catch(() => false)
  if (!confirmed) return
  actionId.value = slot.id
  try {
    await bookingApi.create(slot.id)
    ElMessage.success('预约成功')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    actionId.value = ''
  }
}

async function takeNumber() {
  if (!requireUser(true)) return
  const confirmed = await ElMessageBox.confirm('确认领取今天的现场排队号码？', '现场取号', {
    confirmButtonText: '确认取号', cancelButtonText: '暂不取号', type: 'info',
  }).then(() => true).catch(() => false)
  if (!confirmed) return
  actionId.value = 'queue'
  try {
    const ticket = await queueApi.take(route.params.id)
    ElMessage.success(`取号成功：${ticket.queueNo} 号`)
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    actionId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div><el-button text :icon="ArrowLeft" @click="router.push('/venues')">返回地点列表</el-button></div>
    <el-skeleton v-if="loading" :rows="10" animated />
    <StatePanel v-else-if="error" title="地点详情加载失败" :description="error" error @retry="load" />
    <template v-else-if="venue">
      <header class="venue-hero surface">
        <div class="venue-hero__main">
          <div class="hero-meta"><span>{{ labelOf(venue.category) }}</span><el-tag :type="venue.status === 'ACTIVE' ? 'success' : 'info'" round>{{ labelOf(venue.status) }}</el-tag></div>
          <h1>{{ venue.name }}</h1>
          <p>{{ venue.description || '暂无场所介绍' }}</p>
          <div class="address"><el-icon><Location /></el-icon>{{ venue.addressText }}</div>
        </div>
        <div class="service-board" aria-label="地点服务">
          <div><span>预约服务</span><strong>{{ venue.bookingEnabled ? '开放' : '未开放' }}</strong></div>
          <div><span>现场排队</span><strong>{{ venue.queueEnabled ? '开放' : '未开放' }}</strong></div>
          <div><span>参考价格</span><strong class="data-value">{{ formatMoney(venue.defaultPrice) }}</strong></div>
        </div>
      </header>

      <section v-if="venue.bookingEnabled" class="surface detail-section">
        <div class="section-heading"><div><h2><el-icon><Calendar /></el-icon>可预约时段</h2><p>展示未来 14 天仍开放的时段。</p></div></div>
        <StatePanel v-if="!slots.length" title="暂时没有开放时段" description="稍后回来看看，或选择其他地点。" />
        <div v-else class="slot-list">
          <article v-for="slot in slots" :key="slot.id" class="slot-row">
            <div class="slot-date"><strong class="data-value">{{ slot.slotDate.slice(5) }}</strong><span>{{ slot.slotDate }}</span></div>
            <div class="slot-time"><strong class="data-value">{{ slot.startTime.slice(0, 5) }}–{{ slot.endTime.slice(0, 5) }}</strong><span>剩余 {{ slot.availableCapacity }} / {{ slot.capacity }} 位</span></div>
            <div class="slot-price data-value">{{ Number(slot.price) ? formatMoney(slot.price) : '免费' }}</div>
            <el-button type="primary" :disabled="slot.availableCapacity <= 0" :loading="actionId === slot.id" @click="book(slot)">{{ slot.availableCapacity > 0 ? '确认预约' : '已满' }}</el-button>
          </article>
        </div>
      </section>

      <section v-if="venue.queueEnabled" class="surface detail-section queue-section">
        <div class="section-heading"><div><h2><el-icon><Tickets /></el-icon>今日排队进度</h2><p>号码按地点和日期独立计算。</p></div><el-button type="primary" :loading="actionId === 'queue'" @click="takeNumber">领取现场号码</el-button></div>
        <div class="queue-board">
          <div><span>当前叫到</span><strong class="data-value">{{ queue?.latestCalledNo ?? '—' }}</strong></div>
          <div><span>下一位</span><strong class="data-value">{{ queue?.nextWaitingNo ?? '—' }}</strong></div>
          <div><span>等待人数</span><strong class="data-value">{{ queue?.waitingCount ?? 0 }}</strong></div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.venue-hero { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 32px; padding: clamp(24px, 4vw, 40px); }
.hero-meta { display: flex; align-items: center; gap: 12px; color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.venue-hero h1 { margin: 14px 0 10px; font-size: clamp(30px, 5vw, 44px); line-height: 1.2; }
.venue-hero p { max-width: 680px; margin: 0; color: var(--qm-ink-700); line-height: 1.75; }
.address { display: flex; align-items: center; gap: 8px; margin-top: 22px; color: var(--qm-ink-500); }
.service-board { display: grid; border: 1px solid var(--qm-line-200); border-radius: 6px; background: var(--qm-primary-050); }
.service-board div { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 15px 18px; border-bottom: 1px dashed var(--qm-line-300); }
.service-board div:last-child { border-bottom: 0; }
.service-board span { color: var(--qm-ink-500); font-size: 13px; }
.service-board strong { color: var(--qm-ink-900); }
.detail-section { padding: 24px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.section-heading h2 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 20px; }
.section-heading p { margin: 5px 0 0; color: var(--qm-ink-500); font-size: 14px; }
.slot-list { display: grid; }
.slot-row { display: grid; grid-template-columns: 110px minmax(180px, 1fr) 120px auto; align-items: center; gap: 18px; padding: 16px 0; border-top: 1px solid var(--qm-line-200); }
.slot-date, .slot-time { display: grid; gap: 3px; }
.slot-date strong { color: var(--qm-primary-700); font-size: 20px; }
.slot-date span, .slot-time span { color: var(--qm-ink-500); font-size: 12px; }
.slot-time strong { font-size: 16px; }
.slot-price { font-weight: 700; text-align: right; }
.queue-board { display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px dashed var(--qm-line-300); }
.queue-board div { display: grid; gap: 6px; padding: 22px; border-right: 1px solid var(--qm-line-200); }
.queue-board div:last-child { border: 0; }
.queue-board span { color: var(--qm-ink-500); font-size: 13px; }
.queue-board strong { color: var(--qm-primary-700); font-size: 32px; }
@media (max-width: 760px) { .venue-hero { grid-template-columns: 1fr; } .section-heading { align-items: stretch; flex-direction: column; } .slot-row { grid-template-columns: 1fr auto; gap: 12px; } .slot-price { text-align: left; } .slot-row .el-button { grid-column: 1 / -1; width: 100%; } .queue-board div { padding: 16px 10px; } .queue-board strong { font-size: 26px; } }
</style>
