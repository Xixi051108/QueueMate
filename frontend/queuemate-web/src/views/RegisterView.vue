<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, Phone, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../services/api'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', displayName: '', phone: '', password: '', confirmPassword: '' })
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度应为 3–50 个字符', trigger: 'blur' },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { max: 100, message: '显示名称最多 100 个字符', trigger: 'blur' },
  ],
  phone: [
    { pattern: /^$|^1\d{10}$/, message: '请输入 11 位中国大陆手机号，或留空', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度应为 8–64 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_rule, value, callback) => value === form.password ? callback() : callback(new Error('两次输入的密码不一致')), trigger: ['blur', 'change'] },
  ],
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const result = await authApi.register({
      username: form.username.trim(),
      password: form.password,
      displayName: form.displayName.trim(),
      phone: form.phone.trim(),
    })
    ElMessage.success('注册成功，请登录')
    router.replace({ name: 'login', query: { username: result.username, redirect: route.query.redirect } })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-layout">
    <section class="register-intro" aria-labelledby="register-title">
      <span class="eyebrow">普通用户注册</span>
      <h1 id="register-title">一张账号，<br />管好每一次预约。</h1>
      <p>注册后会自动创建站内钱包。你可以预约时段、查看消费码，也可以领取现场排队号码。</p>
      <div class="account-steps" aria-label="注册后的服务">
        <span>创建账号</span><i></i><span>选择地点</span><i></i><span>预约或取号</span>
      </div>
    </section>

    <section class="register-card surface" aria-label="注册账号">
      <div><span class="form-kicker">开始使用</span><h2>注册 QueueMate</h2><p>注册入口只创建普通用户账号，商家和管理员账号由本地数据初始化。</p></div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
        <div class="form-grid">
          <el-form-item label="用户名" prop="username"><el-input v-model.trim="form.username" :prefix-icon="User" autocomplete="username" placeholder="3–50 个字符" /></el-form-item>
          <el-form-item label="显示名称" prop="displayName"><el-input v-model.trim="form.displayName" :prefix-icon="User" autocomplete="name" placeholder="页面中显示的称呼" /></el-form-item>
        </div>
        <el-form-item label="手机号（可选）" prop="phone"><el-input v-model.trim="form.phone" :prefix-icon="Phone" inputmode="tel" autocomplete="tel" maxlength="11" placeholder="例如 13800000000" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="密码" prop="password"><el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password autocomplete="new-password" placeholder="8–64 个字符" /></el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" :prefix-icon="Lock" type="password" show-password autocomplete="new-password" placeholder="再次输入密码" @keyup.enter="submit" /></el-form-item>
        </div>
        <el-button class="submit-button" type="primary" native-type="submit" :loading="loading">{{ loading ? '正在创建账号' : '创建账号' }}</el-button>
      </el-form>
      <p class="login-link">已经有账号？<RouterLink :to="{ name: 'login', query: { redirect: route.query.redirect } }">返回登录</RouterLink></p>
    </section>
  </div>
</template>

<style scoped>
.register-layout { display: grid; min-height: calc(100vh - 172px); grid-template-columns: minmax(0, .9fr) minmax(480px, 1.1fr); align-items: center; gap: clamp(40px, 7vw, 96px); }
.register-intro { max-width: 560px; }
.eyebrow, .form-kicker { color: var(--qm-primary-700); font-size: 13px; font-weight: 700; letter-spacing: .08em; }
.register-intro h1 { margin: 18px 0; font-size: clamp(36px, 5vw, 56px); line-height: 1.18; letter-spacing: -.03em; }
.register-intro p { margin: 0; color: var(--qm-ink-700); font-size: 17px; line-height: 1.8; }
.account-steps { display: flex; align-items: center; gap: 12px; margin-top: 36px; color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.account-steps i { flex: 1; border-top: 1px dashed var(--qm-line-300); }
.register-card { display: grid; gap: 22px; padding: clamp(24px, 4vw, 36px); box-shadow: var(--qm-shadow-raised); }
.register-card h2 { margin: 8px 0 6px; font-size: 24px; }
.register-card p { margin: 0; color: var(--qm-ink-500); line-height: 1.6; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.submit-button { width: 100%; min-height: 46px; }
.login-link { text-align: center; }
.login-link a { font-weight: 700; }
@media (max-width: 900px) { .register-layout { grid-template-columns: 1fr; align-content: start; } }
@media (max-width: 600px) { .form-grid { grid-template-columns: 1fr; gap: 0; } .account-steps { align-items: flex-start; flex-direction: column; } .account-steps i { width: 1px; height: 12px; margin-left: 20px; border-top: 0; border-left: 1px dashed var(--qm-line-300); } }
</style>
