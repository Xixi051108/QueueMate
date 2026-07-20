<script setup>
import { computed, onMounted, ref } from 'vue'
import { Check, Close, DocumentChecked, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatePanel from '../components/StatePanel.vue'
import { merchantApplicationApi } from '../services/api'
import { formatDateTime, labelOf, statusType } from '../utils/format'

const loading = ref(true)
const error = ref('')
const applications = ref([])
const status = ref('PENDING')
const reviewingId = ref('')

const filtered = computed(() => status.value ? applications.value.filter((item) => item.status === status.value) : applications.value)
const counts = computed(() => ({
  PENDING: applications.value.filter((item) => item.status === 'PENDING').length,
  APPROVED: applications.value.filter((item) => item.status === 'APPROVED').length,
  REJECTED: applications.value.filter((item) => item.status === 'REJECTED').length,
}))

async function load() {
  loading.value = true
  error.value = ''
  try {
    applications.value = await merchantApplicationApi.list()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function approve(application) {
  const result = await ElMessageBox.prompt(
    `确认通过“${application.venueName}”的入驻申请？通过后，${application.applicantDisplayName} 将获得商家身份。`,
    '通过入驻申请',
    {
      confirmButtonText: '确认通过',
      cancelButtonText: '暂不处理',
      inputType: 'textarea',
      inputPlaceholder: '审核备注（选填）',
      inputValidator: (value) => !value || value.length <= 500 || '审核备注最多500个字符',
    },
  ).catch(() => null)
  if (!result) return
  reviewingId.value = application.id
  try {
    await merchantApplicationApi.approve(application.id, result.value || '')
    ElMessage.success('入驻申请已通过')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    reviewingId.value = ''
  }
}

async function reject(application) {
  const result = await ElMessageBox.prompt(
    `说明“${application.venueName}”需要补充或修改的内容。申请人会在入驻页看到这段说明。`,
    '驳回入驻申请',
    {
      confirmButtonText: '确认驳回',
      cancelButtonText: '暂不处理',
      type: 'warning',
      inputType: 'textarea',
      inputPlaceholder: '例如：请补充具体门牌号和楼层信息',
      inputValidator: (value) => {
        if (!value?.trim()) return '请填写驳回原因'
        return value.trim().length <= 500 || '驳回原因最多500个字符'
      },
    },
  ).catch(() => null)
  if (!result) return
  reviewingId.value = application.id
  try {
    await merchantApplicationApi.reject(application.id, result.value.trim())
    ElMessage.success('入驻申请已驳回')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    reviewingId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="page review-page">
    <header class="page-heading">
      <div>
        <span class="eyebrow">管理员工作区</span>
        <h1>商家入驻审核</h1>
        <p>核对申请账号、联系人和拟入驻门店信息。通过后仅开通商家身份，不会自动发布门店。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </header>

    <section class="review-metrics" aria-label="入驻申请统计">
      <button :class="['metric-button', { active: status === 'PENDING' }]" type="button" @click="status = 'PENDING'">
        <span>待审核</span><strong class="data-value">{{ counts.PENDING }}</strong>
      </button>
      <button :class="['metric-button', { active: status === 'APPROVED' }]" type="button" @click="status = 'APPROVED'">
        <span>已通过</span><strong class="data-value">{{ counts.APPROVED }}</strong>
      </button>
      <button :class="['metric-button', { active: status === 'REJECTED' }]" type="button" @click="status = 'REJECTED'">
        <span>已驳回</span><strong class="data-value">{{ counts.REJECTED }}</strong>
      </button>
      <button :class="['metric-button', { active: status === '' }]" type="button" @click="status = ''">
        <span>全部申请</span><strong class="data-value">{{ applications.length }}</strong>
      </button>
    </section>

    <el-skeleton v-if="loading" :rows="8" animated />
    <StatePanel v-else-if="error" title="入驻申请加载失败" :description="error" error @retry="load" />
    <StatePanel v-else-if="!filtered.length" :title="`没有${status ? labelOf(status) : ''}的入驻申请`" description="新的申请提交后会显示在这里。" />

    <section v-else class="application-list" aria-label="商家入驻申请列表">
      <article v-for="application in filtered" :key="application.id" class="review-card surface">
        <div class="review-stamp">
          <span class="data-label">申请编号</span>
          <strong class="data-value">{{ application.id }}</strong>
          <el-tag :type="statusType(application.status)" round>{{ labelOf(application.status) }}</el-tag>
        </div>
        <div class="review-body">
          <header>
            <div><span>{{ labelOf(application.venueCategory) }}</span><h2>{{ application.venueName }}</h2></div>
            <time>{{ formatDateTime(application.submittedAt) }}</time>
          </header>
          <dl class="review-details">
            <div><dt>商家或品牌</dt><dd>{{ application.businessName }}</dd></div>
            <div><dt>申请账号</dt><dd>{{ application.applicantDisplayName }}（{{ application.applicantUsername }}）</dd></div>
            <div><dt>联系人</dt><dd>{{ application.contactName }} · {{ application.contactPhone }}</dd></div>
            <div><dt>拟入驻地址</dt><dd>{{ application.addressText }}</dd></div>
          </dl>
          <div v-if="application.description" class="description"><strong>经营与服务介绍</strong><p>{{ application.description }}</p></div>
          <div v-if="application.reviewNote" class="reviewed-note"><strong>审核说明</strong><p>{{ application.reviewNote }}</p></div>
          <div v-if="application.status === 'PENDING'" class="review-actions">
            <el-button type="primary" :icon="Check" :loading="reviewingId === application.id" @click="approve(application)">通过申请</el-button>
            <el-button :icon="Close" :disabled="reviewingId === application.id" @click="reject(application)">驳回并说明原因</el-button>
          </div>
          <div v-else class="review-complete"><DocumentChecked /><span>由管理员 {{ application.reviewerId }} 于 {{ formatDateTime(application.reviewedAt) }} 完成审核</span></div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.review-page { max-width: 1040px; margin: 0 auto; }
.eyebrow { display: block; margin-bottom: 8px; color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.review-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.metric-button { display: grid; min-height: 88px; align-content: center; gap: 6px; padding: 14px 18px; border: 1px solid var(--qm-line-200); border-radius: var(--qm-radius-md); background: var(--qm-surface); color: var(--qm-ink-500); text-align: left; cursor: pointer; transition: border-color 160ms, background-color 160ms; }
.metric-button:hover, .metric-button.active { border-color: var(--qm-primary-600); background: var(--qm-primary-050); color: var(--qm-primary-700); }
.metric-button strong { color: var(--qm-ink-900); font-size: 26px; }
.metric-button.active strong { color: var(--qm-primary-700); }
.application-list { display: grid; gap: 16px; }
.review-card { display: grid; grid-template-columns: 170px 1fr; overflow: hidden; }
.review-stamp { display: grid; align-content: start; gap: 10px; padding: 24px; border-right: 1px dashed var(--qm-line-300); background: var(--qm-primary-050); }
.data-label { color: var(--qm-ink-500); font-size: 12px; }
.review-stamp strong { overflow-wrap: anywhere; color: var(--qm-primary-700); font-size: 16px; }
.review-stamp .el-tag { justify-self: start; }
.review-body { display: grid; gap: 18px; padding: 24px; }
.review-body header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.review-body header span, .review-body time { color: var(--qm-ink-500); font-size: 12px; }
.review-body h2 { margin: 5px 0 0; font-size: 21px; }
.review-details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 20px; margin: 0; padding: 16px 0; border-top: 1px dashed var(--qm-line-300); border-bottom: 1px dashed var(--qm-line-300); }
.review-details dt { color: var(--qm-ink-500); font-size: 12px; }
.review-details dd { margin: 4px 0 0; color: var(--qm-ink-900); line-height: 1.5; }
.description, .reviewed-note { padding: 14px; border-radius: 6px; background: var(--qm-canvas); }
.description strong, .reviewed-note strong { font-size: 13px; }
.description p, .reviewed-note p { margin: 6px 0 0; color: var(--qm-ink-700); line-height: 1.65; }
.reviewed-note { border-left: 3px solid var(--qm-line-300); }
.review-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.review-actions .el-button + .el-button { margin-left: 0; }
.review-complete { display: flex; align-items: center; gap: 8px; color: var(--qm-ink-500); font-size: 13px; }
.review-complete svg { width: 20px; flex: 0 0 20px; color: var(--qm-success-700); }
@media (max-width: 760px) {
  .review-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .review-card { grid-template-columns: 1fr; }
  .review-stamp { grid-template-columns: 1fr auto; padding: 16px; border-right: 0; border-bottom: 1px dashed var(--qm-line-300); }
  .review-stamp strong { grid-column: 1; }
  .review-stamp .el-tag { grid-column: 2; grid-row: 1 / 3; }
  .review-body { padding: 18px 16px; }
  .review-details { grid-template-columns: 1fr; }
  .review-actions, .review-actions .el-button { width: 100%; }
}
</style>
