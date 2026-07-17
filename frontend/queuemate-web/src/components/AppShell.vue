<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Calendar, Coin, Location, OfficeBuilding, SwitchButton, Tickets, Wallet } from '@element-plus/icons-vue'
import { authState } from '../state/auth'
import { homeForRole } from '../router'
import { labelOf } from '../utils/format'

const route = useRoute()
const router = useRouter()

const userLinks = [
  { to: '/bookings', label: '我的预约', icon: Calendar },
  { to: '/wallet', label: '钱包', icon: Wallet },
  { to: '/queue', label: '排队', icon: Tickets },
]

const merchantLinks = [
  { to: '/manage/venues', label: '场所工作台', icon: OfficeBuilding },
]

const adminLinks = [
  { to: '/manage/venues', label: '场所管理', icon: OfficeBuilding },
  { to: '/admin/wallets', label: '钱包管理', icon: Coin },
  { to: '/admin/bookings', label: '预约处理', icon: Calendar },
]

const links = computed(() => {
  const base = [{ to: '/venues', label: '发现地点', icon: Location }]
  if (authState.role.value === 'USER') return [...base, ...userLinks]
  if (authState.role.value === 'MERCHANT') return [...base, ...merchantLinks]
  if (authState.role.value === 'ADMIN') return [...base, ...adminLinks]
  return base
})

const brandTarget = computed(() => homeForRole(authState.role.value))

function logout() {
  authState.clearSession()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="service-header">
      <div class="header-inner">
        <RouterLink class="brand" :to="brandTarget" aria-label="QueueMate 首页">
          <span class="brand-mark" aria-hidden="true">Q</span>
          <span>
            <strong>QueueMate</strong>
            <small>预约与排队</small>
          </span>
        </RouterLink>

        <nav class="primary-nav" aria-label="主导航">
          <RouterLink
            v-for="link in links"
            :key="link.to"
            :to="link.to"
            :class="{ active: route.path === link.to || route.path.startsWith(`${link.to}/`) }"
          >
            <el-icon aria-hidden="true"><component :is="link.icon" /></el-icon>
            {{ link.label }}
          </RouterLink>
        </nav>

        <div class="account-area">
          <template v-if="authState.isAuthenticated.value">
            <span class="account-name">
              <small>{{ labelOf(authState.role.value) }}</small>
              {{ authState.user.value?.displayName }}
            </span>
            <el-button text :icon="SwitchButton" aria-label="退出登录" @click="logout">退出</el-button>
          </template>
          <RouterLink v-else class="login-link" :to="{ name: 'login', query: { redirect: route.fullPath } }">
            登录
            <el-icon aria-hidden="true"><ArrowRight /></el-icon>
          </RouterLink>
        </div>
      </div>
    </header>

    <main id="main-content" class="main-content">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; }
.service-header { position: sticky; top: 0; z-index: 20; border-bottom: 1px solid var(--qm-line-200); background: rgba(255, 255, 255, 0.96); backdrop-filter: blur(8px); }
.header-inner { width: min(1200px, calc(100% - 48px)); min-height: 68px; margin: 0 auto; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 28px; }
.brand { display: inline-flex; align-items: center; gap: 10px; color: var(--qm-ink-900); }
.brand-mark { display: grid; width: 36px; height: 36px; place-items: center; border: 2px solid var(--qm-primary-600); border-radius: 6px; color: var(--qm-primary-700); font-family: var(--qm-font-data); font-size: 20px; font-weight: 700; }
.brand strong, .brand small { display: block; }
.brand strong { font-size: 16px; letter-spacing: .01em; }
.brand small { margin-top: 1px; color: var(--qm-ink-500); font-size: 11px; }
.primary-nav { display: flex; align-items: stretch; gap: 4px; height: 68px; }
.primary-nav a { display: inline-flex; align-items: center; gap: 6px; padding: 0 14px; border-bottom: 3px solid transparent; color: var(--qm-ink-700); font-size: 14px; font-weight: 600; }
.primary-nav a:hover { background: var(--qm-primary-050); color: var(--qm-primary-700); }
.primary-nav a.active { border-bottom-color: var(--qm-primary-600); color: var(--qm-primary-700); }
.account-area { display: flex; align-items: center; gap: 8px; }
.account-name { display: grid; max-width: 140px; overflow: hidden; color: var(--qm-ink-700); font-size: 14px; line-height: 1.2; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.account-name small { color: var(--qm-ink-500); font-size: 10px; }
.login-link { display: inline-flex; min-height: 44px; align-items: center; gap: 4px; font-weight: 600; }
.main-content { width: min(1200px, calc(100% - 48px)); margin: 0 auto; padding: 40px 0 64px; }
@media (max-width: 900px) {
  .header-inner { grid-template-columns: auto 1fr; padding-top: 8px; }
  .primary-nav { grid-column: 1 / -1; grid-row: 2; height: 48px; overflow-x: auto; }
  .primary-nav a { flex: 0 0 auto; padding: 0 12px; }
  .account-area { justify-self: end; }
}
@media (max-width: 767px) {
  .header-inner, .main-content { width: calc(100% - 32px); }
  .service-header { position: static; }
  .header-inner { gap: 8px; }
  .brand small, .account-name { display: none; }
  .main-content { padding: 24px 0 48px; }
}
</style>
