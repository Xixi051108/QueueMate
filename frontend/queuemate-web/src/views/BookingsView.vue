<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatePanel from '../components/StatePanel.vue'
import { bookingApi, venueApi } from '../services/api'
import { formatDateTime, formatMoney, labelOf, statusType } from '../utils/format'

const loading = ref(true)
const error = ref('')
const bookings = ref([])
const venues = ref([])
const status = ref('')
const actionId = ref('')
const venueNames = computed(() => Object.fromEntries(venues.value.map((item) => [item.id, item.name])))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [bookingData, venueData] = await Promise.all([
      bookingApi.mine({ status: status.value || undefined }),
      venueApi.list(),
    ])
    bookings.value = bookingData
    venues.value = venueData
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function cancelBooking(booking) {
  const result = await ElMessageBox.prompt('可选填取消原因，最多 255 个字符。', '取消预约', {
    confirmButtonText: '确认取消', cancelButtonText: '保留预约', inputPlaceholder: '例如：行程有变', inputValidator: (value) => !value || value.length <= 255 || '取消原因最多 255 个字符',
  }).catch(() => null)
  if (!result) return
  actionId.value = booking.id
  try {
    await bookingApi.cancel(booking.id, result.value || '')
    ElMessage.success('已取消预约')
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
    <header class="page-heading"><div><h1>我的预约</h1><p>查看预约状态、消费凭证和退款结果。</p></div></header>
    <div class="filters surface">
      <div class="filter-field"><label class="field-label" for="booking-status">预约状态</label><el-select id="booking-status" v-model="status" clearable placeholder="全部状态" @change="load"><el-option v-for="item in ['BOOKED','FULFILLED','CANCELLED','NO_SHOW']" :key="item" :label="labelOf(item)" :value="item" /></el-select></div>
    </div>
    <el-skeleton v-if="loading" :rows="8" animated />
    <StatePanel v-else-if="error" title="预约加载失败" :description="error" error @retry="load" />
    <StatePanel v-else-if="!bookings.length" title="还没有预约" description="在地点详情中选择开放时段，预约会出现在这里。"><RouterLink to="/venues"><el-button type="primary">去看看地点</el-button></RouterLink></StatePanel>
    <section v-else class="booking-list" aria-label="预约记录">
      <article v-for="booking in bookings" :key="booking.id" class="booking-ticket surface">
        <div class="ticket-main"><div><span class="ticket-label">{{ venueNames[booking.venueId] || `地点 ${booking.venueId}` }}</span><h2 class="data-value">{{ booking.bookingNo }}</h2></div><el-tag :type="statusType(booking.status)" round>{{ labelOf(booking.status) }}</el-tag></div>
        <dl><div><dt>提交时间</dt><dd>{{ formatDateTime(booking.bookedAt) }}</dd></div><div><dt>支付状态</dt><dd>{{ labelOf(booking.payStatus) }}</dd></div><div><dt>实付金额</dt><dd class="data-value">{{ formatMoney(booking.paidAmount) }}</dd></div></dl>
        <div v-if="booking.voucher" class="voucher"><div><span>消费码</span><strong class="data-value">{{ booking.voucher.consumptionCode }}</strong></div><el-tag :type="statusType(booking.voucher.status)" effect="plain" round>{{ labelOf(booking.voucher.status) }}</el-tag></div>
        <div v-if="booking.status === 'BOOKED'" class="ticket-actions"><el-button type="danger" plain :loading="actionId === booking.id" @click="cancelBooking(booking)">取消预约</el-button></div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.booking-list { display: grid; gap: 16px; }
.booking-ticket { padding: 22px; }
.ticket-main { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.ticket-label { color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.ticket-main h2 { margin: 7px 0 0; font-size: 19px; }
dl { display: grid; grid-template-columns: repeat(3, 1fr); margin: 20px 0 0; padding: 16px 0; border-top: 1px dashed var(--qm-line-300); border-bottom: 1px dashed var(--qm-line-300); }
dl div { padding: 0 16px; border-right: 1px solid var(--qm-line-200); } dl div:first-child { padding-left: 0; } dl div:last-child { border: 0; }
dt { color: var(--qm-ink-500); font-size: 12px; } dd { margin: 5px 0 0; font-size: 14px; font-weight: 600; }
.voucher { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 16px; padding: 14px 16px; background: var(--qm-primary-050); }
.voucher div { display: grid; gap: 4px; } .voucher span { color: var(--qm-ink-500); font-size: 12px; } .voucher strong { font-size: 18px; letter-spacing: .05em; }
.ticket-actions { display: flex; justify-content: flex-end; margin-top: 16px; }
@media (max-width: 600px) { dl { grid-template-columns: 1fr; gap: 12px; } dl div, dl div:first-child { padding: 0; border: 0; } .voucher { align-items: flex-start; flex-direction: column; } .ticket-actions .el-button { width: 100%; } }
</style>
