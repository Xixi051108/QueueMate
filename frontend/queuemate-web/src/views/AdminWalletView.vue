<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatePanel from '../components/StatePanel.vue'
import { adminApi } from '../services/api'
import { formatBalanceChange, formatDateTime, formatMoney, isBalanceIncrease, labelOf, statusType } from '../utils/format'

const loading = ref(true)
const error = ref('')
const transactions = ref([])
const filters = reactive({ userId: '', type: '' })
const dialogOpen = ref(false)
const saving = ref(false)
const adjustFormRef = ref()
const adjustment = reactive({ userId: null, amount: 10, remark: '' })
const rules = {
  userId: [{ required: true, message: '请输入用户 ID', trigger: 'blur' }],
  amount: [{ validator: (_rule, value, callback) => Number(value) !== 0 && Number(value) >= -100000 && Number(value) <= 100000 ? callback() : callback(new Error('调整金额应在 -100000 至 100000 之间，且不能为 0')), trigger: 'blur' }],
  remark: [{ max: 255, message: '备注最多 255 个字符', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    transactions.value = await adminApi.walletTransactions({ userId: filters.userId || undefined, type: filters.type || undefined })
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(filters, { userId: '', type: '' })
  load()
}

async function adjust() {
  const valid = await adjustFormRef.value.validate().catch(() => false)
  if (!valid) return
  const amount = Number(adjustment.amount)
  const confirmed = await ElMessageBox.confirm(`确认将用户 ${adjustment.userId} 的钱包余额${amount > 0 ? '增加' : '扣减'} ${formatMoney(Math.abs(amount))}？`, '确认余额调整', { confirmButtonText: '确认调整', cancelButtonText: '暂不调整', type: amount < 0 ? 'warning' : 'info' }).then(() => true).catch(() => false)
  if (!confirmed) return
  saving.value = true
  try {
    const wallet = await adminApi.adjustWallet(adjustment.userId, { amount, remark: adjustment.remark.trim() })
    ElMessage.success(`余额调整成功，当前余额 ${formatMoney(wallet.balance)}`)
    dialogOpen.value = false
    filters.userId = adjustment.userId
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-heading"><div><span class="workspace-label">管理员工作区</span><h1>钱包管理</h1><p>按用户或类型查询全局流水，并对指定用户的钱包余额进行人工调整。</p></div><el-button type="primary" :icon="Plus" @click="dialogOpen = true">调整余额</el-button></header>
    <form class="filters surface" aria-label="筛选钱包流水" @submit.prevent="load"><div class="filter-field filter-field--grow"><label class="field-label" for="wallet-user-id">用户 ID</label><el-input id="wallet-user-id" v-model.trim="filters.userId" inputmode="numeric" clearable placeholder="留空查看全部用户" /></div><div class="filter-field"><label class="field-label" for="wallet-type">流水类型</label><el-select id="wallet-type" v-model="filters.type" clearable placeholder="全部类型"><el-option v-for="item in ['RECHARGE','PAYMENT','REFUND','ADJUSTMENT']" :key="item" :label="labelOf(item)" :value="item" /></el-select></div><el-button type="primary" native-type="submit" :loading="loading">查询流水</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></form>
    <p class="boundary-note">当前后端流水响应不包含用户 ID；如需核对某个用户，请先使用上方用户 ID 进行筛选。</p>
    <el-skeleton v-if="loading" :rows="9" animated />
    <StatePanel v-else-if="error" title="钱包流水加载失败" :description="error" error @retry="load" />
    <StatePanel v-else-if="!transactions.length" title="没有找到钱包流水" description="调整筛选条件，或先为指定用户执行一次余额调整。" />
    <section v-else class="transaction-list surface" aria-label="全局钱包流水">
      <article v-for="item in transactions" :key="item.id"><div class="transaction-main"><el-tag :type="statusType(item.status)" round>{{ labelOf(item.type) }}</el-tag><div><h2 class="data-value">{{ item.transactionNo }}</h2><p>{{ formatDateTime(item.createdAt) }} · {{ item.remark || '无备注' }}</p></div></div><div class="transaction-balance"><strong class="data-value" :class="{ positive: isBalanceIncrease(item) }">{{ formatBalanceChange(item) }}</strong><span class="data-value">{{ formatMoney(item.balanceBefore) }} → {{ formatMoney(item.balanceAfter) }}</span></div><div class="transaction-biz"><span>{{ item.bizType || '—' }}</span><strong class="data-value">{{ item.bizNo || '无业务编号' }}</strong></div></article>
    </section>

    <el-dialog v-model="dialogOpen" title="调整用户余额" width="min(480px, calc(100vw - 32px))">
      <el-form ref="adjustFormRef" :model="adjustment" :rules="rules" label-position="top" @submit.prevent="adjust"><el-form-item label="用户 ID" prop="userId"><el-input-number v-model="adjustment.userId" :min="1" :step="1" controls-position="right" /><p class="form-help">后端暂未提供用户列表，请填写已存在用户的 ID。</p></el-form-item><el-form-item label="调整金额（元）" prop="amount"><el-input-number v-model="adjustment.amount" :min="-100000" :max="100000" :precision="2" :step="10" controls-position="right" /><p class="form-help">正数增加余额，负数扣减余额；不能填写 0。</p></el-form-item><el-form-item label="调整原因" prop="remark"><el-input v-model.trim="adjustment.remark" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="说明本次调整原因" /></el-form-item></el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="adjust">确认调整</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.workspace-label { display: block; margin-bottom: 8px; color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.boundary-note { margin: -10px 0 0; color: var(--qm-ink-500); font-size: 12px; }
.transaction-list { padding: 0 22px; }
.transaction-list article { display: grid; grid-template-columns: minmax(260px, 1.5fr) minmax(200px, .8fr) minmax(180px, .8fr); align-items: center; gap: 24px; padding: 18px 0; border-bottom: 1px solid var(--qm-line-200); }
.transaction-list article:last-child { border-bottom: 0; }
.transaction-main { display: flex; align-items: center; gap: 12px; min-width: 0; }
.transaction-main h2 { margin: 0; overflow: hidden; font-size: 14px; text-overflow: ellipsis; }
.transaction-main p, .transaction-biz span { margin: 4px 0 0; color: var(--qm-ink-500); font-size: 12px; }
.transaction-balance, .transaction-biz { display: grid; gap: 4px; text-align: right; }
.transaction-balance strong { color: var(--qm-danger-700); font-size: 18px; }
.transaction-balance strong.positive { color: var(--qm-success-700); }
.transaction-balance span, .transaction-biz strong { color: var(--qm-ink-500); font-size: 12px; }
@media (max-width: 820px) { .transaction-list article { grid-template-columns: 1fr 1fr; } .transaction-main { grid-column: 1 / -1; } }
@media (max-width: 560px) { .transaction-list article { grid-template-columns: 1fr; gap: 12px; } .transaction-main { grid-column: auto; align-items: flex-start; flex-direction: column; } .transaction-balance, .transaction-biz { text-align: left; } }
</style>
