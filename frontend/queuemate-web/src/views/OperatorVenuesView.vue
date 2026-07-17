<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Edit, Plus, Setting, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatePanel from '../components/StatePanel.vue'
import { venueApi } from '../services/api'
import { authState } from '../state/auth'
import { formatMoney, labelOf, statusType } from '../utils/format'

const loading = ref(true)
const error = ref('')
const venues = ref([])
const keyword = ref('')
const status = ref('')
const dialogOpen = ref(false)
const saving = ref(false)
const actionId = ref('')
const editingId = ref('')
const formRef = ref()
const isAdmin = computed(() => authState.role.value === 'ADMIN')
const form = reactive(emptyForm())

const categories = ['TEA_SHOP', 'STUDY_ROOM', 'BADMINTON_COURT']
const rules = {
  name: [{ required: true, message: '请输入地点名称', trigger: 'blur' }, { max: 100, message: '地点名称最多 100 个字符', trigger: 'blur' }],
  category: [{ required: true, message: '请选择地点类别', trigger: 'change' }],
  description: [{ max: 500, message: '地点介绍最多 500 个字符', trigger: 'blur' }],
  addressText: [{ max: 255, message: '地点地址最多 255 个字符', trigger: 'blur' }],
  defaultPrice: [{ required: true, message: '请输入默认价格', trigger: 'blur' }],
  merchantId: [{ validator: (_rule, value, callback) => !isAdmin.value || Number(value) > 0 ? callback() : callback(new Error('请输入有效的商家用户 ID')), trigger: 'blur' }],
}

const managedVenues = computed(() => {
  const owned = isAdmin.value ? venues.value : venues.value.filter((venue) => String(venue.merchantId) === String(authState.user.value?.id))
  const term = keyword.value.trim().toLowerCase()
  return owned.filter((venue) => (!status.value || venue.status === status.value) && (!term || [venue.name, venue.addressText, venue.description].some((value) => String(value || '').toLowerCase().includes(term))))
})

function emptyForm() {
  return { name: '', category: 'STUDY_ROOM', description: '', addressText: '', queueEnabled: true, bookingEnabled: true, defaultPrice: 0, merchantId: null }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    venues.value = await venueApi.list()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  editingId.value = ''
  Object.assign(form, emptyForm())
  dialogOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(venue) {
  editingId.value = venue.id
  Object.assign(form, {
    name: venue.name,
    category: venue.category,
    description: venue.description || '',
    addressText: venue.addressText || '',
    queueEnabled: venue.queueEnabled,
    bookingEnabled: venue.bookingEnabled,
    defaultPrice: Number(venue.defaultPrice || 0),
    merchantId: venue.merchantId,
  })
  dialogOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  const payload = {
    name: form.name.trim(), category: form.category, description: form.description.trim(), addressText: form.addressText.trim(),
    queueEnabled: form.queueEnabled, bookingEnabled: form.bookingEnabled, defaultPrice: Number(form.defaultPrice),
  }
  try {
    if (editingId.value) {
      await venueApi.update(editingId.value, payload)
      ElMessage.success('地点信息已保存')
    } else {
      await venueApi.create(isAdmin.value ? { ...payload, merchantId: Number(form.merchantId) } : payload)
      ElMessage.success('地点已创建')
    }
    dialogOpen.value = false
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(venue) {
  const target = venue.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const verb = target === 'ACTIVE' ? '启用' : '停用'
  const confirmed = await ElMessageBox.confirm(`${verb}“${venue.name}”？${target === 'INACTIVE' ? '停用后不能继续预约或取号。' : ''}`, `${verb}地点`, { confirmButtonText: `确认${verb}`, cancelButtonText: '暂不操作', type: target === 'INACTIVE' ? 'warning' : 'info' }).then(() => true).catch(() => false)
  if (!confirmed) return
  actionId.value = venue.id
  try {
    await venueApi.updateStatus(venue.id, target)
    ElMessage.success(`地点已${verb}`)
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
    <header class="page-heading">
      <div><span class="workspace-label">{{ isAdmin ? '管理员工作区' : '商家工作区' }}</span><h1>{{ isAdmin ? '场所管理' : '我的场所' }}</h1><p>维护地点资料，并进入场所运营页管理时段、排队、核销和统计。</p></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">创建地点</el-button>
    </header>

    <form class="filters surface" aria-label="筛选场所" @submit.prevent>
      <div class="filter-field filter-field--grow"><label class="field-label" for="managed-keyword">搜索地点</label><el-input id="managed-keyword" v-model="keyword" clearable placeholder="名称、地址或介绍" /></div>
      <div class="filter-field"><label class="field-label" for="managed-status">营业状态</label><el-select id="managed-status" v-model="status" clearable placeholder="全部状态"><el-option label="营业中" value="ACTIVE" /><el-option label="已停用" value="INACTIVE" /></el-select></div>
    </form>

    <el-skeleton v-if="loading" :rows="8" animated />
    <StatePanel v-else-if="error" title="场所列表加载失败" :description="error" error @retry="load" />
    <StatePanel v-else-if="!managedVenues.length" title="没有找到可管理的地点" :description="isAdmin ? '创建地点时需要填写有效的商家用户 ID。' : '创建第一个地点后，可以继续配置预约时段和排队服务。'"><el-button type="primary" :icon="Plus" @click="openCreate">创建地点</el-button></StatePanel>
    <section v-else class="managed-grid" aria-label="可管理场所">
      <article v-for="venue in managedVenues" :key="venue.id" class="managed-card surface">
        <div class="card-heading"><div><span>{{ labelOf(venue.category) }} · ID {{ venue.id }}</span><h2>{{ venue.name }}</h2></div><el-tag :type="statusType(venue.status)" round>{{ labelOf(venue.status) }}</el-tag></div>
        <p class="address">{{ venue.addressText || '未填写地址' }}</p>
        <div class="service-flags"><span :class="{ enabled: venue.bookingEnabled }">预约 {{ venue.bookingEnabled ? '开放' : '关闭' }}</span><span :class="{ enabled: venue.queueEnabled }">排队 {{ venue.queueEnabled ? '开放' : '关闭' }}</span><span class="data-value">默认 {{ formatMoney(venue.defaultPrice) }}</span></div>
        <p v-if="isAdmin" class="owner">商家用户 ID：<span class="data-value">{{ venue.merchantId }}</span></p>
        <div class="card-actions">
          <RouterLink :to="`/manage/venues/${venue.id}`"><el-button type="primary" :icon="Setting">进入运营</el-button></RouterLink>
          <el-button :icon="Edit" @click="openEdit(venue)">编辑资料</el-button>
          <el-button :icon="SwitchButton" :loading="actionId === venue.id" @click="toggleStatus(venue)">{{ venue.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
        </div>
      </article>
    </section>

    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑地点' : '创建地点'" width="min(680px, calc(100vw - 32px))" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <div class="form-grid"><el-form-item label="地点名称" prop="name"><el-input v-model.trim="form.name" maxlength="100" show-word-limit placeholder="例如：南山自习室" /></el-form-item><el-form-item label="地点类别" prop="category"><el-select v-model="form.category"><el-option v-for="item in categories" :key="item" :label="labelOf(item)" :value="item" /></el-select></el-form-item></div>
        <el-form-item v-if="!editingId && isAdmin" label="商家用户 ID" prop="merchantId"><el-input-number v-model="form.merchantId" :min="1" :step="1" controls-position="right" /><p class="form-help">后端暂未提供商家用户列表，请填写已存在且启用的商家账号 ID。</p></el-form-item>
        <el-form-item label="地址" prop="addressText"><el-input v-model.trim="form.addressText" maxlength="255" show-word-limit placeholder="线下地点的文字地址" /></el-form-item>
        <el-form-item label="地点介绍" prop="description"><el-input v-model.trim="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明环境、服务方式或注意事项" /></el-form-item>
        <div class="form-grid"><el-form-item label="默认价格（元）" prop="defaultPrice"><el-input-number v-model="form.defaultPrice" :min="0" :max="99999999" :precision="2" :step="5" controls-position="right" /></el-form-item><div class="service-switches"><label><span>预约服务</span><el-switch v-model="form.bookingEnabled" /></label><label><span>现场排队</span><el-switch v-model="form.queueEnabled" /></label></div></div>
      </el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存修改' : '创建地点' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.workspace-label { display: block; margin-bottom: 8px; color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.managed-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.managed-card { display: grid; gap: 16px; padding: 22px; }
.card-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-heading span, .owner { color: var(--qm-ink-500); font-size: 12px; }
.card-heading h2 { margin: 6px 0 0; font-size: 20px; }
.address { min-height: 24px; margin: 0; color: var(--qm-ink-700); }
.service-flags { display: flex; flex-wrap: wrap; gap: 8px 16px; padding: 14px 0; border-top: 1px dashed var(--qm-line-300); border-bottom: 1px dashed var(--qm-line-300); color: var(--qm-ink-500); font-size: 13px; }
.service-flags span.enabled { color: var(--qm-success-700); font-weight: 700; }
.owner { margin: -4px 0 0; }
.card-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.card-actions .el-button + .el-button { margin-left: 0; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.service-switches { display: grid; align-content: start; gap: 12px; padding-top: 30px; }
.service-switches label { display: flex; align-items: center; justify-content: space-between; gap: 16px; color: var(--qm-ink-700); font-size: 14px; }
@media (max-width: 860px) { .managed-grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .form-grid { grid-template-columns: 1fr; gap: 0; } .service-switches { padding: 0 0 20px; } .card-actions, .card-actions a, .card-actions .el-button { width: 100%; } }
</style>
