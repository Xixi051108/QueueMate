import http from './http'

export const authApi = {
  login: (payload) => http.post('/auth/login', payload),
  me: () => http.get('/auth/me'),
}

export const venueApi = {
  list: (params) => http.get('/venues', { params }),
  get: (id) => http.get(`/venues/${id}`),
  slots: (id, params) => http.get(`/venues/${id}/slots`, { params }),
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
}
