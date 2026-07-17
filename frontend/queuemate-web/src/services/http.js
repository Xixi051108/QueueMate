import axios from 'axios'
import { authState } from '../state/auth'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 12_000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  if (authState.token.value) {
    config.headers.Authorization = `Bearer ${authState.token.value}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data?.data,
  (error) => {
    const status = error.response?.status
    const payload = error.response?.data
    const requestPath = error.config?.url || ''

    if (status === 401 && !requestPath.endsWith('/auth/login')) {
      authState.clearSession()
      const current = `${window.location.pathname}${window.location.search}`
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign(`/login?redirect=${encodeURIComponent(current)}`)
      }
    }

    const normalized = new Error(
      payload?.message || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '暂时无法连接服务，请检查后端是否已启动'),
    )
    normalized.code = payload?.code || 'NETWORK_ERROR'
    normalized.status = status || 0
    return Promise.reject(normalized)
  },
)

export default http
