<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Check, DocumentChecked, OfficeBuilding, Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import StatePanel from '../components/StatePanel.vue'
import { VENUE_CATEGORIES } from '../constants/venue'
import { authApi, merchantApplicationApi } from '../services/api'
import { authState } from '../state/auth'
import { formatDateTime, labelOf, statusType } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const saving = ref(false)
const history = ref([])
const showForm = ref(false)
const step = ref(0)
const formRef = ref()
const statusHeading = ref()
const form = reactive(emptyForm())

const categories = VENUE_CATEGORIES
const latest = computed(() => history.value[0] || null)
const canApply = computed(() => !latest.value || latest.value.status === 'REJECTED')
const stepFields = [
  ['businessName', 'contactName', 'contactPhone'],
  ['venueName', 'venueCategory', 'addressText', 'description'],
]

const rules = {
  businessName: [{ required: true, message: '请填写商家或品牌名称', trigger: 'blur' }, { max: 100, message: '最多100个字符', trigger: 'blur' }],
  contactName: [{ required: true, message: '请填写联系人姓名', trigger: 'blur' }, { max: 100, message: '最多100个字符', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请填写联系电话', trigger: 'blur' }, { pattern: /^1\d{10}$/, message: '请输入11位手机号', trigger: 'blur' }],
  venueName: [{ required: true, message: '请填写拟入驻门店名称', trigger: 'blur' }, { max: 100, message: '最多100个字符', trigger: 'blur' }],
  venueCategory: [{ required: true, message: '请选择门店类别', trigger: 'change' }],
  addressText: [{ required: true, message: '请填写门店详细地址', trigger: 'blur' }, { max: 255, message: '最多255个字符', trigger: 'blur' }],
  description: [{ max: 500, message: '最多500个字符', trigger: 'blur' }],
}

function emptyForm() {
  return {
    businessName: '',
    contactName: authState.user.value?.displayName || '',
    contactPhone: authState.user.value?.phone || '',
    venueName: '',
    venueCategory: 'TEA_SHOP',
    addressText: '',
    description: '',
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    history.value = await merchantApplicationApi.mine()
    if (latest.value?.status === 'APPROVED') {
      const user = await authApi.me()
      authState.updateUser(user)
    }
    showForm.value = !latest.value
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function nextStep() {
  const fields = stepFields[step.value] || []
  const valid = await formRef.value?.validateField(fields).then(() => true).catch(() => false)
  if (valid) step.value += 1
}

function startAgain() {
  Object.assign(form, emptyForm())
  step.value = 0
  showForm.value = true
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const application = await merchantApplicationApi.submit({
      businessName: form.businessName.trim(),
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim(),
      venueName: form.venueName.trim(),
      venueCategory: form.venueCategory,
      addressText: form.addressText.trim(),
      description: form.description.trim(),
    })
    history.value.unshift(application)
    showForm.value = false
    step.value = 0
    await nextTick()
    statusHeading.value?.focus()
    ElMessage.success('入驻申请已提交')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

async function enterMerchant() {
  try {
    const user = await authApi.me()
    authState.updateUser(user)
    if (!authState.switchRole('MERCHANT')) {
      ElMessage.warning('商家身份尚未同步，请重新登录后再试')
      return
    }
    router.push('/manage/venues')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="page onboarding-page">
    <header class="page-heading">
      <div>
        <span class="eyebrow">商家服务</span>
        <h1>申请门店入驻</h1>
        <p>使用当前账号提交经营信息。管理员审核通过后，可在同一账号中切换到商家端并创建门店。</p>
      </div>
    </header>

    <el-skeleton v-if="loading" :rows="8" animated />
    <StatePanel v-else-if="error" title="入驻信息加载失败" :description="error" error @retry="load" />

    <template v-else>
      <section v-if="latest && !showForm" class="status-card surface" :aria-label="`申请状态：${labelOf(latest.status)}`">
        <div class="status-ticket">
          <span>申请编号</span>
          <strong class="data-value">{{ latest.id }}</strong>
          <el-tag :type="statusType(latest.status)" effect="light" round>{{ labelOf(latest.status) }}</el-tag>
        </div>
        <div class="status-content">
          <template v-if="latest.status === 'PENDING'">
            <h2 ref="statusHeading" tabindex="-1">资料已进入审核</h2>
            <p>管理员将核对联系人、门店名称和经营地址。审核结果会显示在本页，当前无需重复提交。</p>
            <div class="next-action"><DocumentChecked /><span><strong>接下来</strong> 等待管理员处理；如需修改资料，请在审核结果出来后重新申请。</span></div>
          </template>
          <template v-else-if="latest.status === 'APPROVED'">
            <h2 ref="statusHeading" tabindex="-1">商家身份已开通</h2>
            <p>你的顾客功能仍然保留。进入商家端后，可以创建门店、配置预约时段并处理现场队列。</p>
            <el-button type="primary" :icon="OfficeBuilding" @click="enterMerchant">进入商家工作台</el-button>
          </template>
          <template v-else>
            <h2 ref="statusHeading" tabindex="-1">申请需要补充资料</h2>
            <div class="review-note" role="alert">
              <strong>审核说明</strong>
              <p>{{ latest.reviewNote }}</p>
            </div>
            <el-button type="primary" :icon="Promotion" @click="startAgain">修改后重新申请</el-button>
          </template>
          <dl class="application-summary">
            <div><dt>商家名称</dt><dd>{{ latest.businessName }}</dd></div>
            <div><dt>拟入驻门店</dt><dd>{{ latest.venueName }}</dd></div>
            <div><dt>门店类别</dt><dd>{{ labelOf(latest.venueCategory) }}</dd></div>
            <div><dt>提交时间</dt><dd>{{ formatDateTime(latest.submittedAt) }}</dd></div>
          </dl>
        </div>
      </section>

      <section v-if="showForm && canApply" class="application-form surface" aria-labelledby="application-form-title">
        <div class="form-heading">
          <div><span class="eyebrow">资料填写</span><h2 id="application-form-title">完成三步申请</h2></div>
          <span class="step-count">第 {{ step + 1 }} 步，共 3 步</span>
        </div>
        <el-steps :active="step" finish-status="success" align-center>
          <el-step title="经营主体" />
          <el-step title="拟入驻门店" />
          <el-step title="确认提交" />
        </el-steps>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <div v-show="step === 0" class="step-panel">
            <div class="form-grid">
              <el-form-item label="商家或品牌名称" prop="businessName"><el-input v-model.trim="form.businessName" maxlength="100" show-word-limit placeholder="例如：青禾空间" /></el-form-item>
              <el-form-item label="联系人姓名" prop="contactName"><el-input v-model.trim="form.contactName" maxlength="100" /></el-form-item>
            </div>
            <el-form-item label="联系电话" prop="contactPhone"><el-input v-model.trim="form.contactPhone" inputmode="tel" maxlength="11" placeholder="用于管理员核对入驻信息" /></el-form-item>
            <p class="privacy-note">联系人信息仅用于本次入驻审核，不会展示在公开门店页面。</p>
          </div>

          <div v-show="step === 1" class="step-panel">
            <div class="form-grid">
              <el-form-item label="拟入驻门店名称" prop="venueName"><el-input v-model.trim="form.venueName" maxlength="100" show-word-limit placeholder="例如：青禾自习室·滨江店" /></el-form-item>
              <el-form-item label="门店类别" prop="venueCategory"><el-select v-model="form.venueCategory"><el-option v-for="item in categories" :key="item" :label="labelOf(item)" :value="item" /></el-select></el-form-item>
            </div>
            <el-form-item label="门店详细地址" prop="addressText"><el-input v-model.trim="form.addressText" maxlength="255" show-word-limit placeholder="填写区、街道、门牌号和楼层" /></el-form-item>
            <el-form-item label="经营与服务介绍（选填）" prop="description"><el-input v-model.trim="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="介绍营业场景、服务内容或设施情况" /></el-form-item>
          </div>

          <div v-show="step === 2" class="step-panel confirm-panel">
            <div class="confirm-mark"><el-icon aria-hidden="true"><Check /></el-icon></div>
            <div><h3>确认申请资料</h3><p>提交后，在管理员审核完成前不能再次提交。审核通过不会自动公开门店，你需要进入商家端完成价格、预约和排队设置。</p></div>
            <dl class="application-summary confirm-summary">
              <div><dt>商家名称</dt><dd>{{ form.businessName }}</dd></div>
              <div><dt>联系人</dt><dd>{{ form.contactName }} · {{ form.contactPhone }}</dd></div>
              <div><dt>门店</dt><dd>{{ form.venueName }} · {{ labelOf(form.venueCategory) }}</dd></div>
              <div><dt>地址</dt><dd>{{ form.addressText }}</dd></div>
            </dl>
          </div>
        </el-form>

        <div class="form-actions">
          <el-button v-if="step > 0" :icon="ArrowLeft" @click="step -= 1">上一步</el-button>
          <el-button v-if="step < 2" type="primary" :icon="ArrowRight" @click="nextStep">下一步</el-button>
          <el-button v-else type="primary" :loading="saving" :icon="Promotion" @click="submit">提交入驻申请</el-button>
        </div>
      </section>

      <section v-if="history.length > 1" class="history surface">
        <h2>历史申请</h2>
        <div v-for="item in history.slice(1)" :key="item.id" class="history-row">
          <div><strong>{{ item.venueName }}</strong><span>{{ formatDateTime(item.submittedAt) }}</span></div>
          <el-tag :type="statusType(item.status)" round>{{ labelOf(item.status) }}</el-tag>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.onboarding-page { max-width: 920px; margin: 0 auto; }
.eyebrow { display: block; margin-bottom: 8px; color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.status-card { display: grid; grid-template-columns: 210px 1fr; overflow: hidden; }
.status-ticket { display: grid; align-content: start; gap: 10px; padding: 28px; border-right: 1px dashed var(--qm-line-300); background: var(--qm-primary-050); }
.status-ticket span { color: var(--qm-ink-500); font-size: 13px; }
.status-ticket strong { overflow-wrap: anywhere; color: var(--qm-primary-700); font-size: 18px; }
.status-ticket .el-tag { justify-self: start; }
.status-content { display: grid; gap: 18px; padding: 28px; }
.status-content h2, .form-heading h2, .history h2 { margin: 0; font-size: 22px; }
.status-content > p { margin: -8px 0 0; color: var(--qm-ink-700); line-height: 1.7; }
.next-action { display: flex; gap: 12px; padding: 14px; border-left: 3px solid var(--qm-primary-600); background: var(--qm-primary-050); color: var(--qm-ink-700); line-height: 1.6; }
.next-action svg { width: 22px; flex: 0 0 22px; color: var(--qm-primary-700); }
.review-note { padding: 14px 16px; border: 1px solid var(--qm-danger-100); border-radius: 6px; background: var(--qm-danger-100); color: var(--qm-danger-700); }
.review-note p { margin: 6px 0 0; line-height: 1.6; }
.application-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0; margin: 0; border-top: 1px dashed var(--qm-line-300); }
.application-summary div { padding: 14px 12px 0 0; }
.application-summary dt { color: var(--qm-ink-500); font-size: 12px; }
.application-summary dd { margin: 4px 0 0; color: var(--qm-ink-900); line-height: 1.5; }
.application-form { padding: 28px; }
.form-heading { display: flex; align-items: start; justify-content: space-between; gap: 16px; margin-bottom: 28px; }
.step-count { color: var(--qm-ink-500); font-size: 13px; }
.application-form :deep(.el-steps) { margin-bottom: 32px; }
.step-panel { min-height: 250px; padding-top: 8px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.privacy-note { margin: -4px 0 0; padding: 12px; border-left: 3px solid var(--qm-line-300); color: var(--qm-ink-500); font-size: 13px; line-height: 1.6; }
.confirm-panel { display: grid; grid-template-columns: auto 1fr; align-content: start; gap: 16px; }
.confirm-mark { display: grid; width: 44px; height: 44px; place-items: center; border-radius: 50%; background: var(--qm-success-100); color: var(--qm-success-700); font-size: 24px; }
.confirm-panel h3 { margin: 0 0 8px; font-size: 18px; }
.confirm-panel p { margin: 0; color: var(--qm-ink-700); line-height: 1.7; }
.confirm-summary { grid-column: 1 / -1; margin-top: 8px; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 20px; border-top: 1px solid var(--qm-line-200); }
.form-actions .el-button + .el-button { margin-left: 0; }
.history { padding: 24px; }
.history h2 { margin-bottom: 16px; }
.history-row { display: flex; min-height: 58px; align-items: center; justify-content: space-between; gap: 16px; border-top: 1px solid var(--qm-line-200); }
.history-row div { display: grid; gap: 4px; }
.history-row span { color: var(--qm-ink-500); font-size: 12px; }
@media (max-width: 680px) {
  .status-card { grid-template-columns: 1fr; }
  .status-ticket { grid-template-columns: 1fr auto; align-items: center; padding: 18px; border-right: 0; border-bottom: 1px dashed var(--qm-line-300); }
  .status-ticket strong { grid-column: 1; }
  .status-ticket .el-tag { grid-column: 2; grid-row: 1 / 3; }
  .status-content, .application-form { padding: 20px 16px; }
  .form-grid, .application-summary { grid-template-columns: 1fr; }
  .step-panel { min-height: 0; }
  .application-form :deep(.el-step__title) { font-size: 12px; }
  .form-actions { flex-direction: column-reverse; }
  .form-actions .el-button { width: 100%; }
}
</style>
