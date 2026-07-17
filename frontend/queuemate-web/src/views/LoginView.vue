<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../services/api'
import { authState } from '../state/auth'
import { homeForRole } from '../router'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: typeof route.query.username === 'string' ? route.query.username : '', password: '' })
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 50, message: '用户名最多 50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { max: 64, message: '密码最多 64 个字符', trigger: 'blur' },
  ],
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const session = await authApi.login({ username: form.username.trim(), password: form.password })
    authState.setSession(session)
    ElMessage.success('登录成功')
    router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : homeForRole(session.user.role))
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-layout">
    <section class="login-intro" aria-labelledby="login-title">
      <span class="eyebrow">本地生活服务台</span>
      <h1 id="login-title">少等一会，<br />多做一点喜欢的事。</h1>
      <p>查看地点、预约时段、现场取号，并在一个清楚的界面里跟进状态。</p>
      <div class="ticket-line" aria-hidden="true">
        <span>预约</span><i></i><span>排队</span><i></i><span>到店</span>
      </div>
    </section>

    <section class="login-card surface" aria-label="账号登录">
      <div>
        <span class="form-kicker">欢迎回来</span>
        <h2>登录 QueueMate</h2>
        <p>使用本地模拟账号进入对应角色的工作区。</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" :prefix-icon="User" autocomplete="username" placeholder="例如 alice" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password autocomplete="current-password" placeholder="输入密码" @keyup.enter="submit" />
        </el-form-item>
        <el-button class="submit-button" type="primary" native-type="submit" :loading="loading">
          {{ loading ? '正在登录' : '登录并继续' }}
        </el-button>
      </el-form>
      <p class="register-link">还没有账号？<RouterLink :to="{ name: 'register', query: { redirect: route.query.redirect } }">注册普通用户</RouterLink></p>
      <aside class="demo-note">
        <strong>本地演示账号</strong>
        <span>普通用户：alice / User123456</span>
        <span>商家：merchant_tea / Merchant123456</span>
        <span>管理员：admin / Admin123456</span>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.login-layout { display: grid; min-height: calc(100vh - 172px); grid-template-columns: minmax(0, 1.1fr) minmax(360px, .8fr); align-items: center; gap: clamp(40px, 8vw, 112px); }
.login-intro { max-width: 620px; }
.eyebrow, .form-kicker { color: var(--qm-primary-700); font-size: 13px; font-weight: 700; letter-spacing: .08em; }
.login-intro h1 { margin: 18px 0; color: var(--qm-ink-900); font-size: clamp(38px, 6vw, 62px); line-height: 1.16; letter-spacing: -.035em; }
.login-intro p { max-width: 520px; margin: 0; color: var(--qm-ink-700); font-size: 18px; line-height: 1.8; }
.ticket-line { display: flex; max-width: 500px; align-items: center; gap: 14px; margin-top: 40px; color: var(--qm-primary-700); font-size: 13px; font-weight: 700; }
.ticket-line i { flex: 1; border-top: 1px dashed var(--qm-line-300); }
.login-card { display: grid; gap: 26px; padding: clamp(24px, 4vw, 40px); box-shadow: var(--qm-shadow-raised); }
.login-card h2 { margin: 8px 0 6px; font-size: 24px; }
.login-card p { margin: 0; color: var(--qm-ink-500); line-height: 1.6; }
.submit-button { width: 100%; min-height: 46px; margin-top: 4px; }
.register-link { margin: -10px 0 0 !important; text-align: center; }
.register-link a { font-weight: 700; }
.demo-note { display: grid; gap: 5px; border-top: 1px dashed var(--qm-line-300); padding-top: 18px; color: var(--qm-ink-500); font-size: 12px; line-height: 1.6; }
.demo-note strong { color: var(--qm-ink-700); }
@media (max-width: 840px) { .login-layout { grid-template-columns: 1fr; align-content: start; gap: 32px; } .login-intro h1 { font-size: 38px; } }
</style>
