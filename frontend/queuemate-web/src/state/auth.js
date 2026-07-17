import { computed, ref } from 'vue'

const TOKEN_KEY = 'queuemate.token'
const USER_KEY = 'queuemate.user'

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const user = ref(readStoredUser())

export const authState = {
  token,
  user,
  isAuthenticated: computed(() => Boolean(token.value && user.value)),
  role: computed(() => user.value?.role || ''),
  setSession(session) {
    token.value = session.token
    user.value = session.user
    localStorage.setItem(TOKEN_KEY, session.token)
    localStorage.setItem(USER_KEY, JSON.stringify(session.user))
  },
  updateUser(nextUser) {
    user.value = nextUser
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
  },
  clearSession() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}
