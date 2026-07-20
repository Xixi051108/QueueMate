import http from './http'

export const authApi = {
  register: (payload) => http.post('/auth/register', payload),
  login: (payload) => http.post('/auth/login', payload),
  me: () => http.get('/auth/me'),
}

export const venueApi = {
  list: (params) => http.get('/venues', { params }),
  get: (id) => http.get(`/venues/${id}`),
  create: (payload) => http.post('/venues', payload),
  update: (id, payload) => http.put(`/venues/${id}`, payload),
  updateStatus: (id, status) => http.patch(`/venues/${id}/status`, { status }),
  slots: (id, params) => http.get(`/venues/${id}/slots`, { params }),
}

export const slotApi = {
  list: (venueId, params) => http.get(`/venues/${venueId}/slots`, { params }),
  create: (venueId, payload) => http.post(`/venues/${venueId}/slots`, payload),
  updateStatus: (venueId, slotId, status) => http.patch(`/venues/${venueId}/slots/${slotId}/status`, { status }),
}

export const bookingApi = {
  create: (slotId) => http.post('/bookings', { slotId }),
  mine: (params) => http.get('/bookings/my', { params }),
  cancel: (id, reason) => http.patch(`/bookings/${id}/cancel`, { reason }),
}

export const walletApi = {
  mine: () => http.get('/wallets/my'),
  recharge: (amount, remark) => http.post('/wallets/my/recharge', { amount, remark }),
  transactions: (params) => http.get('/wallets/my/transactions', { params }),
}

export const queueApi = {
  take: (venueId) => http.post(`/venues/${venueId}/queue/tickets`),
  current: (venueId, params) => http.get(`/venues/${venueId}/queue/tickets/current`, { params }),
  mine: (params) => http.get('/queue/tickets/my', { params }),
  call: (ticketId) => http.patch(`/queue/tickets/${ticketId}/call`),
  complete: (ticketId) => http.patch(`/queue/tickets/${ticketId}/complete`),
  miss: (ticketId) => http.patch(`/queue/tickets/${ticketId}/miss`),
}

export const voucherApi = {
  redeem: (venueId, consumptionCode) => http.post(`/venues/${venueId}/booking-vouchers/redeem`, { consumptionCode }),
}

export const statsApi = {
  busyHours: (venueId, params) => http.get(`/stats/venues/${venueId}/busy-hours`, { params }),
}

export const adminApi = {
  walletTransactions: (params) => http.get('/admin/wallet-transactions', { params }),
  adjustWallet: (userId, payload) => http.post(`/admin/wallets/${userId}/adjust`, payload),
}

export const merchantApplicationApi = {
  submit: (payload) => http.post('/merchant-applications', payload),
  mine: () => http.get('/merchant-applications/my'),
  list: (params) => http.get('/admin/merchant-applications', { params }),
  get: (id) => http.get(`/admin/merchant-applications/${id}`),
  approve: (id, reviewNote = '') => http.patch(`/admin/merchant-applications/${id}/approve`, { reviewNote }),
  reject: (id, reviewNote) => http.patch(`/admin/merchant-applications/${id}/reject`, { reviewNote }),
  merchants: () => http.get('/admin/merchants'),
}
