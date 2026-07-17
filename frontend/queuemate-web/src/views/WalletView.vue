<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import StatePanel from '../components/StatePanel.vue'
import { walletApi } from '../services/api'
import { formatBalanceChange, formatDateTime, formatMoney, isBalanceIncrease, labelOf } from '../utils/format'

const loading = ref(true)
const error = ref('')
const wallet = ref(null)
const transactions = ref([])
const type = ref('')
const dialogOpen = ref(false)
const recharging = ref(false)
const recharge = reactive({ amount: 50, remark: '' })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [walletData, transactionData] = await Promise.all([walletApi.mine(), walletApi.transactions({ type: type.value || undefined })])
    wallet.value = walletData
    transactions.value = transactionData
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function submitRecharge() {
  if (!recharge.amount || recharge.amount < 0.01 || recharge.amount > 100000) {
    ElMessage.warning('充值金额应为 0.01 至 100000 元')
    return
  }
  recharging.value = true
  try {
    await walletApi.recharge(Number(recharge.amount), recharge.remark.trim())
    ElMessage.success('充值成功')
    dialogOpen.value = false
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    recharging.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-heading"><div><h1>我的钱包</h1><p>站内模拟余额用于支付收费预约时段。</p></div></header>
    <el-skeleton v-if="loading" :rows="8" animated />
    <StatePanel v-else-if="error" title="钱包加载失败" :description="error" error @retry="load" />
    <template v-else>
      <section class="wallet-card surface"><div><span>可用余额</span><strong class="data-value">{{ formatMoney(wallet?.balance) }}</strong><small>钱包状态：{{ wallet?.status === 'ACTIVE' ? '正常' : labelOf(wallet?.status) }}</small></div><el-button type="primary" @click="dialogOpen = true">模拟充值</el-button></section>
      <section class="surface transactions"><div class="transactions__header"><div><h2 class="section-title">余额记录</h2><p>充值、支付、退款与调整均保留变动前后余额。</p></div><el-select v-model="type" clearable placeholder="全部类型" aria-label="流水类型" @change="load"><el-option v-for="item in ['RECHARGE','PAYMENT','REFUND','ADJUSTMENT']" :key="item" :label="labelOf(item)" :value="item" /></el-select></div>
        <StatePanel v-if="!transactions.length" title="暂无余额记录" description="充值或完成收费预约后，记录会出现在这里。" />
        <div v-else class="transaction-list"><article v-for="item in transactions" :key="item.id"><div><strong>{{ labelOf(item.type) }}</strong><span>{{ formatDateTime(item.createdAt) }} · {{ item.remark || '无备注' }}</span></div><div class="transaction-amount"><strong class="data-value" :class="{ positive: isBalanceIncrease(item) }">{{ formatBalanceChange(item) }}</strong><span class="data-value">余额 {{ formatMoney(item.balanceAfter) }}</span></div></article></div>
      </section>
    </template>
    <el-dialog v-model="dialogOpen" title="模拟充值" width="min(440px, calc(100vw - 32px))">
      <el-form label-position="top" @submit.prevent="submitRecharge"><el-form-item label="充值金额（元）" required><el-input-number v-model="recharge.amount" :min="0.01" :max="100000" :precision="2" :step="10" controls-position="right" /></el-form-item><el-form-item label="备注（可选）"><el-input v-model="recharge.remark" maxlength="255" show-word-limit placeholder="例如：本地演示充值" /></el-form-item></el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="recharging" @click="submitRecharge">确认充值</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wallet-card { display: flex; min-height: 190px; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 28px; border-left: 4px solid var(--qm-primary-600); }
.wallet-card > div { display: grid; gap: 8px; } .wallet-card span, .wallet-card small { color: var(--qm-ink-500); } .wallet-card strong { font-size: clamp(34px, 7vw, 48px); color: var(--qm-ink-900); }
.transactions { padding: 24px; }
.transactions__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.transactions__header p { margin: 6px 0 0; color: var(--qm-ink-500); font-size: 13px; }
.transaction-list article { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 17px 0; border-top: 1px solid var(--qm-line-200); }
.transaction-list article > div { display: grid; gap: 4px; } .transaction-list span { color: var(--qm-ink-500); font-size: 12px; }
.transaction-amount { text-align: right; } .transaction-amount strong { color: var(--qm-danger-700); } .transaction-amount strong.positive { color: var(--qm-success-700); }
@media (max-width: 600px) { .wallet-card, .transactions__header { align-items: stretch; flex-direction: column; } .wallet-card .el-button { width: 100%; } .transaction-list article { align-items: flex-start; } }
</style>
