import { createRouter, createWebHistory } from 'vue-router'
import { authState } from '../state/auth'

const routes = [
  { path: '/', redirect: '/venues' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册' } },
  { path: '/venues', name: 'venues', component: () => import('../views/VenuesView.vue'), meta: { title: '发现地点' } },
  { path: '/venues/:id', name: 'venue-detail', component: () => import('../views/VenueDetailView.vue'), meta: { title: '地点详情' } },
  { path: '/bookings', name: 'bookings', component: () => import('../views/BookingsView.vue'), meta: { title: '我的预约', requiresAuth: true, roles: ['USER'] } },
  { path: '/wallet', name: 'wallet', component: () => import('../views/WalletView.vue'), meta: { title: '我的钱包', requiresAuth: true, roles: ['USER'] } },
  { path: '/queue', name: 'queue', component: () => import('../views/QueueView.vue'), meta: { title: '我的排队', requiresAuth: true, roles: ['USER'] } },
  { path: '/manage/venues', name: 'manage-venues', component: () => import('../views/OperatorVenuesView.vue'), meta: { title: '场所工作台', requiresAuth: true, roles: ['MERCHANT', 'ADMIN'] } },
  { path: '/manage/venues/:id', name: 'manage-venue', component: () => import('../views/OperatorVenueView.vue'), meta: { title: '场所运营', requiresAuth: true, roles: ['MERCHANT', 'ADMIN'] } },
  { path: '/admin/wallets', name: 'admin-wallets', component: () => import('../views/AdminWalletView.vue'), meta: { title: '钱包管理', requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/admin/bookings', name: 'admin-bookings', component: () => import('../views/AdminBookingView.vue'), meta: { title: '预约处理', requiresAuth: true, roles: ['ADMIN'] } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面不存在' } },
]

export function homeForRole(role) {
  if (role === 'MERCHANT' || role === 'ADMIN') return '/manage/venues'
  return '/venues'
}

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !authState.isAuthenticated.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.includes(authState.role.value)) {
    return { name: 'venues' }
  }
  if (['login', 'register'].includes(to.name) && authState.isAuthenticated.value) {
    return homeForRole(authState.role.value)
  }
  document.title = `${to.meta.title || 'QueueMate'} · QueueMate`
  return true
})

export default router
