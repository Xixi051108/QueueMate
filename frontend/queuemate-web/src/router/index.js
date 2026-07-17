import { createRouter, createWebHistory } from 'vue-router'
import { authState } from '../state/auth'

const routes = [
  { path: '/', redirect: '/venues' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/venues', name: 'venues', component: () => import('../views/VenuesView.vue'), meta: { title: '发现地点' } },
  { path: '/venues/:id', name: 'venue-detail', component: () => import('../views/VenueDetailView.vue'), meta: { title: '地点详情' } },
  { path: '/bookings', name: 'bookings', component: () => import('../views/BookingsView.vue'), meta: { title: '我的预约', requiresAuth: true, roles: ['USER'] } },
  { path: '/wallet', name: 'wallet', component: () => import('../views/WalletView.vue'), meta: { title: '我的钱包', requiresAuth: true, roles: ['USER'] } },
  { path: '/queue', name: 'queue', component: () => import('../views/QueueView.vue'), meta: { title: '我的排队', requiresAuth: true, roles: ['USER'] } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面不存在' } },
]

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
  if (to.name === 'login' && authState.isAuthenticated.value) {
    return { name: 'venues' }
  }
  document.title = `${to.meta.title || 'QueueMate'} · QueueMate`
  return true
})

export default router
