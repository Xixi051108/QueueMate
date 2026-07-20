import { computed, ref } from 'vue'

const TOKEN_KEY = 'queuemate.token'
const USER_KEY = 'queuemate.user'
const ACTIVE_ROLE_KEY = 'queuemate.activeRole'
const ROLE_ORDER = { USER: 0, MERCHANT: 1, ADMIN: 2 }

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

function rolesOf(user) {
  const roles = Array.isArray(user?.roles) && user.roles.length ? user.roles : [user?.role]
  return [...new Set(roles.filter(Boolean))].sort((a, b) => (ROLE_ORDER[a] ?? 99) - (ROLE_ORDER[b] ?? 99))
}

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const user = ref(readStoredUser())
const activeRole = ref(localStorage.getItem(ACTIVE_ROLE_KEY) || '')
const availableRoles = computed(() => rolesOf(user.value))
const role = computed(() => availableRoles.value.includes(activeRole.value) ? activeRole.value : (user.value?.role || ''))

function storeActiveRole(nextRole) {
  activeRole.value = nextRole
  if (nextRole) localStorage.setItem(ACTIVE_ROLE_KEY, nextRole)
  else localStorage.removeItem(ACTIVE_ROLE_KEY)
}

export const authState = {
  token,
  user,
  role,
  activeRole,
  availableRoles,
  isAuthenticated: computed(() => Boolean(token.value && user.value)),
  hasRole: (expectedRole) => availableRoles.value.includes(expectedRole),
  setSession(session) {
    token.value = session.token
    user.value = session.user
    localStorage.setItem(TOKEN_KEY, session.token)
    localStorage.setItem(USER_KEY, JSON.stringify(session.user))
    storeActiveRole(session.user?.role || rolesOf(session.user)[0] || '')
  },
  updateUser(nextUser) {
    user.value = nextUser
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    if (!rolesOf(nextUser).includes(activeRole.value)) {
      storeActiveRole(nextUser?.role || rolesOf(nextUser)[0] || '')
    }
  },
  switchRole(nextRole) {
    if (!availableRoles.value.includes(nextRole)) return false
    storeActiveRole(nextRole)
    return true
  },
  clearSession() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    storeActiveRole('')
  },
}
