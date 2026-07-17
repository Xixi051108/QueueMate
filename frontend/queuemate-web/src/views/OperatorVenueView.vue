<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatePanel from '../components/StatePanel.vue'
import { queueApi, slotApi, statsApi, venueApi, voucherApi } from '../services/api'
import { authState } from '../state/auth'
import { formatDateTime, formatMoney, isoDate, labelOf, statusType } from '../utils/format'

const route = useRoute()
const router = useRouter()
const venueId = computed(() => route.params.id)
const loading = ref(true)
const error = ref('')
const venue = ref(null)
const slots = ref([])
const queue = ref(null)
const busyHours = ref([])
const sectionLoading = reactive({ slots: false, queue: false, stats: false })
const actionKey = ref('')
const slotDialogOpen = ref(false)
const slotSaving = ref(false)
const slotFormRef = ref()
const redeeming = ref(false)
const consumptionCode = ref('')
const redeemed = ref(null)
const slotFilters = reactive({ dateFrom: isoDate(), dateTo: isoDate(30), status: '' })
const queueDate = ref(isoDate())
const statsFilters = reactive({ dateFrom: isoDate(-6), dateTo: isoDate() })
const slotForm = reactive({ slotDate: isoDate(), startTime: '09:00:00', endTime: '10:00:00', capacity: 10, price: 0 })
const slotRules = {
  slotDate: [{ required: true, message: '请选择时段日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
}

const maxHeat = computed(() => Math.max(1, ...busyHours.value.map((item) => Number(item.heatScore || 0))))
const canManage = computed(() => authState.role.value === 'ADMIN' || String(venue.value?.merchantId) === String(authState.user.value?.id))

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    venue.value = await venueApi.get(venueId.value)
    if (!canManage.value) throw new Error('只能管理自己名下的地点')
    await Promise.all([loadSlots(), loadQueue(), loadStats()])
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function loadSlots() {
  sectionLoading.slots = true
  try {
    slots.value = await slotApi.list(venueId.value, { dateFrom: slotFilters.dateFrom || undefined, dateTo: slotFilters.dateTo || undefined, status: slotFilters.status || undefined })
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    sectionLoading.slots = false
  }
}

async function loadQueue() {
  sectionLoading.queue = true
  try {
    queue.value = await queueApi.current(venueId.value, { queueDate: queueDate.value })
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    sectionLoading.queue = false
  }
}

async function loadStats() {
  sectionLoading.stats = true
  try {
    busyHours.value = await statsApi.busyHours(venueId.value, statsFilters)
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    sectionLoading.stats = false
  }
}

async function openSlotDialog() {
  Object.assign(slotForm, { slotDate: isoDate(), startTime: '09:00:00', endTime: '10:00:00', capacity: 10, price: Number(venue.value?.defaultPrice || 0) })
  slotDialogOpen.value = true
  await nextTick()
  slotFormRef.value?.clearValidate()
}

async function createSlot() {
  const valid = await slotFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (slotForm.startTime >= slotForm.endTime) {
    ElMessage.warning('开始时间必须早于结束时间')
    return
  }
  slotSaving.value = true
  try {
    await slotApi.create(venueId.value, { ...slotForm, capacity: Number(slotForm.capacity), price: Number(slotForm.price) })
    ElMessage.success('预约时段已创建')
    slotDialogOpen.value = false
    await loadSlots()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    slotSaving.value = false
  }
}

async function toggleSlot(slot) {
  const target = slot.status === 'OPEN' ? 'CLOSED' : 'OPEN'
  const verb = target === 'OPEN' ? '打开' : '关闭'
  const confirmed = await ElMessageBox.confirm(`${verb} ${slot.slotDate} ${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)} 时段？`, `${verb}时段`, { confirmButtonText: `确认${verb}`, cancelButtonText: '暂不操作', type: target === 'CLOSED' ? 'warning' : 'info' }).then(() => true).catch(() => false)
  if (!confirmed) return
  actionKey.value = `slot-${slot.id}`
  try {
    await slotApi.updateStatus(venueId.value, slot.id, target)
    ElMessage.success(`时段已${verb}`)
    await loadSlots()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    actionKey.value = ''
  }
}

async function transitionTicket(ticket, target) {
  const verbs = { call: '叫号', complete: '完成服务', miss: '标记过号' }
  actionKey.value = `ticket-${ticket.id}-${target}`
  try {
    await queueApi[target](ticket.id)
    ElMessage.success(`${verbs[target]}成功`)
    await loadQueue()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    actionKey.value = ''
  }
}

async function redeem() {
  const code = consumptionCode.value.trim()
  if (code.length < 8 || code.length > 32 || !/^[A-Za-z0-9]+$/.test(code)) {
    ElMessage.warning('请输入 8–32 位字母或数字消费码')
    return
  }
  redeeming.value = true
  redeemed.value = null
  try {
    redeemed.value = await voucherApi.redeem(venueId.value, code)
    consumptionCode.value = ''
    ElMessage.success('消费码核销成功')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    redeeming.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <div><el-button text :icon="ArrowLeft" @click="router.push('/manage/venues')">返回场所列表</el-button></div>
    <el-skeleton v-if="loading" :rows="12" animated />
    <StatePanel v-else-if="error" title="场所运营页加载失败" :description="error" error @retry="loadAll" />
    <template v-else-if="venue">
      <header class="operation-hero surface">
        <div><span>{{ labelOf(venue.category) }} · 场所 ID {{ venue.id }}</span><h1>{{ venue.name }}</h1><p>{{ venue.addressText || '未填写地址' }}</p></div>
        <div class="operation-status"><el-tag :type="statusType(venue.status)" round>{{ labelOf(venue.status) }}</el-tag><span>预约 {{ venue.bookingEnabled ? '开放' : '关闭' }}</span><span>排队 {{ venue.queueEnabled ? '开放' : '关闭' }}</span></div>
      </header>

      <section class="operation-section surface" aria-labelledby="slot-title">
        <div class="section-heading"><div><h2 id="slot-title">预约时段</h2><p>创建时段并控制是否接受新预约，已预约数量不会因关闭时段而改变。</p></div><el-button type="primary" :icon="Plus" :disabled="!venue.bookingEnabled || venue.status !== 'ACTIVE'" @click="openSlotDialog">创建时段</el-button></div>
        <form class="inline-filters" @submit.prevent="loadSlots"><label><span>开始日期</span><el-date-picker v-model="slotFilters.dateFrom" type="date" value-format="YYYY-MM-DD" :clearable="false" /></label><label><span>结束日期</span><el-date-picker v-model="slotFilters.dateTo" type="date" value-format="YYYY-MM-DD" :clearable="false" /></label><label><span>状态</span><el-select v-model="slotFilters.status" clearable placeholder="全部状态"><el-option label="可预约" value="OPEN" /><el-option label="已关闭" value="CLOSED" /></el-select></label><el-button :icon="Refresh" :loading="sectionLoading.slots" native-type="submit">查询时段</el-button></form>
        <StatePanel v-if="!sectionLoading.slots && !slots.length" title="当前范围没有时段" description="调整日期范围，或创建一个新的预约时段。" />
        <div v-else class="slot-records">
          <article v-for="slot in slots" :key="slot.id" class="slot-record"><div class="slot-date"><strong class="data-value">{{ slot.slotDate.slice(5) }}</strong><span>{{ slot.slotDate }}</span></div><div><h3 class="data-value">{{ slot.startTime.slice(0, 5) }}–{{ slot.endTime.slice(0, 5) }}</h3><p>已预约 {{ slot.reservedCount }} / {{ slot.capacity }} · 剩余 {{ slot.availableCapacity }} 位</p></div><strong class="data-value">{{ formatMoney(slot.price) }}</strong><el-tag :type="statusType(slot.status)" round>{{ labelOf(slot.status) }}</el-tag><el-button :loading="actionKey === `slot-${slot.id}`" @click="toggleSlot(slot)">{{ slot.status === 'OPEN' ? '关闭时段' : '重新打开' }}</el-button></article>
        </div>
      </section>

      <section class="operation-section surface" aria-labelledby="queue-title">
        <div class="section-heading"><div><h2 id="queue-title">现场排队</h2><p>叫号后可以完成服务或标记过号；公开页面会同步显示当前进度。</p></div><label class="date-control"><span>排队日期</span><el-date-picker v-model="queueDate" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="loadQueue" /></label></div>
        <div class="metric-grid"><div class="metric-card surface"><span>当前叫到</span><strong class="data-value">{{ queue?.latestCalledNo ?? '—' }}</strong></div><div class="metric-card surface"><span>下一位</span><strong class="data-value">{{ queue?.nextWaitingNo ?? '—' }}</strong></div><div class="metric-card surface"><span>等待人数</span><strong class="data-value">{{ queue?.waitingCount ?? 0 }}</strong></div></div>
        <StatePanel v-if="!sectionLoading.queue && !queue?.tickets?.length" title="当天没有排队号码" description="用户取号后，号码会按顺序出现在这里。" />
        <div v-else class="queue-records">
          <article v-for="ticket in queue?.tickets || []" :key="ticket.id" class="queue-record"><div class="queue-no"><span>号码</span><strong class="data-value">{{ ticket.queueNo }}</strong></div><div><h3>{{ labelOf(ticket.status) }}</h3><p>{{ ticket.ticketNo }} · {{ formatDateTime(ticket.takenAt) }}</p></div><el-tag :type="statusType(ticket.status)" round>{{ labelOf(ticket.status) }}</el-tag><div class="action-row"><el-button v-if="ticket.status === 'WAITING'" type="primary" :loading="actionKey === `ticket-${ticket.id}-call`" @click="transitionTicket(ticket, 'call')">叫号</el-button><template v-if="ticket.status === 'CALLED'"><el-button type="primary" :loading="actionKey === `ticket-${ticket.id}-complete`" @click="transitionTicket(ticket, 'complete')">完成服务</el-button><el-button :loading="actionKey === `ticket-${ticket.id}-miss`" @click="transitionTicket(ticket, 'miss')">标记过号</el-button></template></div></article>
        </div>
      </section>

      <div class="two-column">
        <section class="operation-section surface" aria-labelledby="voucher-title">
          <div class="section-heading"><div><h2 id="voucher-title">消费码核销</h2><p>核对用户出示的消费码，成功后预约会变为已完成。</p></div></div>
          <form class="redeem-form" @submit.prevent="redeem"><label for="consumption-code">消费码</label><el-input id="consumption-code" v-model.trim="consumptionCode" maxlength="32" placeholder="输入 8–32 位消费码" class="data-value" /><el-button type="primary" native-type="submit" :loading="redeeming">确认核销</el-button></form>
          <div v-if="redeemed" class="redeem-result"><span>最近核销结果</span><strong class="data-value">{{ redeemed.bookingNo }}</strong><p>{{ labelOf(redeemed.status) }} · {{ formatMoney(redeemed.paidAmount) }} · {{ formatDateTime(redeemed.redeemedAt) }}</p></div>
        </section>

        <section class="operation-section surface" aria-labelledby="stats-title">
          <div class="section-heading"><div><h2 id="stats-title">繁忙时段</h2><p>预约和排队按小时合并，帮助判断服务高峰。</p></div></div>
          <form class="stats-filters" @submit.prevent="loadStats"><label><span>开始日期</span><el-date-picker v-model="statsFilters.dateFrom" type="date" value-format="YYYY-MM-DD" :clearable="false" /></label><label><span>结束日期</span><el-date-picker v-model="statsFilters.dateTo" type="date" value-format="YYYY-MM-DD" :clearable="false" /></label><el-button :icon="Refresh" native-type="submit" :loading="sectionLoading.stats">更新统计</el-button></form>
          <StatePanel v-if="!sectionLoading.stats && !busyHours.length" title="当前范围暂无数据" description="产生预约或排队记录后，会显示对应小时的热度。" />
          <div v-else class="busy-list" aria-label="按小时繁忙统计"><div v-for="item in busyHours" :key="item.hour" class="busy-row"><strong class="data-value">{{ item.hour }}</strong><div class="busy-track"><i :style="{ width: `${Math.max(4, Number(item.heatScore) / maxHeat * 100)}%` }"></i></div><span>预约 {{ item.bookingCount }} · 排队 {{ item.queueCount }}</span></div></div>
        </section>
      </div>

      <el-dialog v-model="slotDialogOpen" title="创建预约时段" width="min(580px, calc(100vw - 32px))">
        <el-form ref="slotFormRef" :model="slotForm" :rules="slotRules" label-position="top" @submit.prevent="createSlot">
          <el-form-item label="日期" prop="slotDate"><el-date-picker v-model="slotForm.slotDate" type="date" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
          <div class="form-grid"><el-form-item label="开始时间" prop="startTime"><el-time-picker v-model="slotForm.startTime" value-format="HH:mm:ss" format="HH:mm" :clearable="false" /></el-form-item><el-form-item label="结束时间" prop="endTime"><el-time-picker v-model="slotForm.endTime" value-format="HH:mm:ss" format="HH:mm" :clearable="false" /></el-form-item></div>
          <div class="form-grid"><el-form-item label="容量" prop="capacity"><el-input-number v-model="slotForm.capacity" :min="1" :max="100000" :step="1" controls-position="right" /></el-form-item><el-form-item label="价格（元）" prop="price"><el-input-number v-model="slotForm.price" :min="0" :max="99999999" :precision="2" :step="5" controls-position="right" /></el-form-item></div>
        </el-form>
        <template #footer><el-button @click="slotDialogOpen = false">取消</el-button><el-button type="primary" :loading="slotSaving" @click="createSlot">创建时段</el-button></template>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.operation-hero { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; padding: 28px; border-left: 4px solid var(--qm-primary-600); }
.operation-hero span { color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.operation-hero h1 { margin: 8px 0 6px; font-size: 30px; }
.operation-hero p { margin: 0; color: var(--qm-ink-500); }
.operation-status { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px 16px; }
.operation-status > span { color: var(--qm-ink-700); }
.operation-section { min-width: 0; padding: 24px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.section-heading h2 { margin: 0; font-size: 21px; }
.section-heading p { margin: 6px 0 0; color: var(--qm-ink-500); font-size: 13px; line-height: 1.6; }
.inline-filters, .stats-filters { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; margin-bottom: 16px; padding: 14px; background: var(--qm-primary-050); }
.inline-filters label, .stats-filters label, .date-control { display: grid; gap: 6px; color: var(--qm-ink-700); font-size: 12px; font-weight: 600; }
.slot-records, .queue-records { display: grid; }
.slot-record { display: grid; grid-template-columns: 90px minmax(180px, 1fr) 110px auto auto; align-items: center; gap: 16px; padding: 15px 0; border-top: 1px solid var(--qm-line-200); }
.slot-date { display: grid; gap: 2px; }
.slot-date strong { color: var(--qm-primary-700); font-size: 20px; }
.slot-date span, .slot-record p, .queue-record p { color: var(--qm-ink-500); font-size: 12px; }
.slot-record h3, .slot-record p, .queue-record h3, .queue-record p { margin: 0; }
.queue-record { display: grid; grid-template-columns: 84px minmax(180px, 1fr) auto minmax(170px, auto); align-items: center; gap: 16px; padding: 14px 0; border-top: 1px solid var(--qm-line-200); }
.queue-no { display: grid; min-height: 72px; place-items: center; border-right: 1px dashed var(--qm-line-300); color: var(--qm-primary-700); }
.queue-no span { font-size: 11px; }
.queue-no strong { font-size: 32px; }
.two-column { display: grid; grid-template-columns: .85fr 1.15fr; gap: 16px; }
.redeem-form { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
.redeem-form label { grid-column: 1 / -1; color: var(--qm-ink-700); font-size: 13px; font-weight: 600; }
.redeem-result { display: grid; gap: 5px; margin-top: 16px; padding: 16px; border-left: 3px solid var(--qm-success-700); background: var(--qm-success-100); }
.redeem-result span, .redeem-result p { margin: 0; color: var(--qm-ink-700); font-size: 12px; }
.busy-list { display: grid; gap: 12px; }
.busy-row { display: grid; grid-template-columns: 54px minmax(80px, 1fr) 130px; align-items: center; gap: 10px; font-size: 12px; }
.busy-track { height: 10px; overflow: hidden; border-radius: 2px; background: var(--qm-line-200); }
.busy-track i { display: block; height: 100%; background: var(--qm-primary-600); }
.busy-row span { color: var(--qm-ink-500); text-align: right; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 980px) { .two-column { grid-template-columns: 1fr; } .slot-record { grid-template-columns: 72px 1fr auto; } .slot-record > strong, .slot-record > .el-tag, .slot-record > .el-button { grid-column: 2; justify-self: start; } }
@media (max-width: 700px) { .operation-hero, .section-heading { align-items: stretch; flex-direction: column; } .operation-status { justify-content: flex-start; } .inline-filters, .stats-filters { align-items: stretch; flex-direction: column; } .inline-filters .el-button, .stats-filters .el-button { width: 100%; } .queue-record { grid-template-columns: 72px 1fr; } .queue-record > .el-tag, .queue-record > .action-row { grid-column: 2; justify-self: start; } .busy-row { grid-template-columns: 48px 1fr; } .busy-row span { grid-column: 2; text-align: left; } .form-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
