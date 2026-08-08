import { expect } from '@playwright/test'

const API_BASE_URL = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:8080/api/v1'

export function createRunId() {
  return `pw${Date.now().toString(36)}`
}

export function testUserFor(runId) {
  return {
    username: `qm_${runId}`,
    displayName: 'Postman User',
    phone: '13800007777',
    password: 'User123456',
  }
}

export async function preparePaidBookingRun(request, runId, resources) {
  const merchant = await login(request, 'merchant_tea', 'Merchant123456')
  const venue = await postData(request, '/venues', merchant.token, {
    name: `Postman Venue ${runId}`,
    category: 'STUDY_ROOM',
    description: 'created by Postman',
    addressText: 'Playwright 自动化测试地点',
    queueEnabled: false,
    bookingEnabled: true,
    defaultPrice: 20,
  })
  resources.venueId = venue.id
  const slot = await postData(request, `/venues/${venue.id}/slots`, merchant.token, {
    slotDate: localIsoDate(2),
    startTime: '19:00:00',
    endTime: '20:00:00',
    capacity: 3,
    price: 20,
  })
  resources.slotId = slot.id
}

export async function verifyCleanupSupport(request, runId) {
  const admin = await login(request, 'admin', 'Admin123456')
  const cleanup = await postData(
    request,
    '/test-support/postman-runs/cleanup',
    admin.token,
    { runId, venueId: '', slotIds: [] },
  )
  expect(cleanup.remainingArtifacts).toBe(0)
}

export async function cleanupRun(request, runId, resources = {}) {
  const admin = await login(request, 'admin', 'Admin123456')
  const cleanup = await postData(
    request,
    '/test-support/postman-runs/cleanup',
    admin.token,
    {
      runId,
      venueId: resources.venueId || '',
      // The cleanup service discovers and removes every slot owned by a
      // run-scoped venue. Explicit slot IDs are reserved for fixture venue
      // 4002 and would intentionally fail the service's safety validation.
      slotIds: [],
    },
  )
  expect(cleanup.remainingArtifacts).toBe(0)
  return cleanup
}

async function login(request, username, password) {
  return postData(request, '/auth/login', '', { username, password })
}

async function postData(request, path, token, data) {
  const response = await request.post(`${API_BASE_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data,
  })
  const body = await response.json().catch(() => null)

  expect(
    response.ok(),
    `POST ${path} failed with ${response.status()}: ${JSON.stringify(body)}`,
  ).toBeTruthy()
  expect(body?.code, `${path} returned an unexpected envelope`).toBe('0')
  return body.data
}

function localIsoDate(daysFromToday) {
  const value = new Date()
  value.setDate(value.getDate() + daysFromToday)
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
