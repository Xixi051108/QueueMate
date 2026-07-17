<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bookingApi } from '../services/api'
import { formatDateTime, formatMoney, labelOf, statusType } from '../utils/format'

const formRef = ref()
const loading = ref(false)
const result = ref(null)
const form = reactive({ bookingId: null, reason: '' })
const rules = {
  bookingId: [{ required: true, message: '请输入预约 ID', trigger: 'blur' }],
  reason: [{ max: 255, message: '取消原因最多 255 个字符', trigger: 'blur' }],
}

async function cancelBooking() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const confirmed = await ElMessageBox.confirm(`确认取消预约 ID ${form.bookingId}？取消成功后会释放名额，符合退款条件的收费预约会同时退款。`, '管理员取消预约', { confirmButtonText: '确认取消预约', cancelButtonText: '暂不取消', type: 'warning' }).then(() => true).catch(() => false)
  if (!confirmed) return
  loading.value = true
  result.value = null
  try {
    result.value = await bookingApi.cancel(form.bookingId, form.reason.trim())
    ElMessage.success('预约已取消')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="page-heading"><div><span class="workspace-label">管理员工作区</span><h1>预约处理</h1><p>对明确的预约记录执行管理员取消。该操作会保留历史记录并按后端规则处理退款。</p></div></header>
    <div class="admin-grid">
      <section class="cancel-panel surface" aria-labelledby="cancel-title"><div><h2 id="cancel-title">按预约 ID 取消</h2><p>当前后端未提供全部预约查询接口，因此需要先从用户或测试记录中取得预约 ID。</p></div><el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="cancelBooking"><el-form-item label="预约 ID" prop="bookingId"><el-input-number v-model="form.bookingId" :min="1" :step="1" controls-position="right" /></el-form-item><el-form-item label="取消原因（可选）" prop="reason"><el-input v-model.trim="form.reason" type="textarea" :rows="4" maxlength="255" show-word-limit placeholder="例如：管理员协助用户取消" /></el-form-item><el-button type="danger" plain native-type="submit" :loading="loading">确认取消预约</el-button></el-form></section>
      <section class="guidance surface" aria-labelledby="guidance-title"><h2 id="guidance-title">操作影响</h2><dl><div><dt>预约状态</dt><dd><code>BOOKED → CANCELLED</code></dd></div><div><dt>场所名额</dt><dd>成功取消后自动回补 1 个名额</dd></div><div><dt>收费预约</dt><dd>未核销且符合时间窗口时自动退款</dd></div><div><dt>消费码</dt><dd>退款成功后变为已作废</dd></div><div><dt>重新预约</dt><dd>取消后用户可以重新预约同一时段</dd></div></dl></section>
    </div>
    <section v-if="result" class="result-panel surface" aria-live="polite"><div><span>最近处理结果</span><h2 class="data-value">{{ result.bookingNo }}</h2></div><el-tag :type="statusType(result.status)" round>{{ labelOf(result.status) }}</el-tag><dl><div><dt>预约 ID</dt><dd class="data-value">{{ result.id }}</dd></div><div><dt>支付状态</dt><dd>{{ labelOf(result.payStatus) }}</dd></div><div><dt>实付金额</dt><dd class="data-value">{{ formatMoney(result.paidAmount) }}</dd></div><div><dt>取消时间</dt><dd>{{ formatDateTime(result.cancelledAt) }}</dd></div></dl></section>
  </div>
</template>

<style scoped>
.workspace-label { display: block; margin-bottom: 8px; color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.admin-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.cancel-panel, .guidance, .result-panel { padding: 24px; }
.cancel-panel { display: grid; gap: 24px; }
.cancel-panel h2, .guidance h2 { margin: 0; font-size: 21px; }
.cancel-panel p { margin: 6px 0 0; color: var(--qm-ink-500); font-size: 13px; line-height: 1.7; }
.cancel-panel .el-button { width: 100%; }
.guidance dl { display: grid; gap: 0; margin: 18px 0 0; }
.guidance dl div { display: grid; grid-template-columns: 100px 1fr; gap: 14px; padding: 13px 0; border-top: 1px solid var(--qm-line-200); }
.guidance dt { color: var(--qm-ink-500); font-size: 13px; }
.guidance dd { margin: 0; color: var(--qm-ink-700); font-size: 14px; }
.result-panel { display: grid; grid-template-columns: 1fr auto; align-items: start; gap: 16px; border-left: 4px solid var(--qm-success-700); }
.result-panel span { color: var(--qm-success-700); font-size: 12px; font-weight: 700; }
.result-panel h2 { margin: 6px 0 0; font-size: 18px; }
.result-panel dl { display: grid; grid-column: 1 / -1; grid-template-columns: repeat(4, 1fr); margin: 0; padding-top: 16px; border-top: 1px dashed var(--qm-line-300); }
.result-panel dl div { padding: 0 14px; border-right: 1px solid var(--qm-line-200); }
.result-panel dl div:first-child { padding-left: 0; }.result-panel dl div:last-child { border: 0; }
.result-panel dt { color: var(--qm-ink-500); font-size: 12px; }.result-panel dd { margin: 5px 0 0; font-size: 14px; font-weight: 600; }
@media (max-width: 760px) { .admin-grid { grid-template-columns: 1fr; } .result-panel dl { grid-template-columns: 1fr 1fr; gap: 16px; } .result-panel dl div { padding: 0; border: 0; } }
</style>
