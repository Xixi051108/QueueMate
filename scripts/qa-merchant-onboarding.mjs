import { createRequire } from 'node:module'
import path from 'node:path'
import process from 'node:process'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.QM_PLAYWRIGHT_PATH || 'playwright')

const baseUrl = process.env.QM_QA_BASE_URL || 'http://127.0.0.1:5173'
const outputDir = process.env.TEMP || process.cwd()
const browser = await chromium.launch({
  headless: true,
  ...(process.env.QM_QA_BROWSER ? { executablePath: process.env.QM_QA_BROWSER } : {}),
})

function api(data) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0', message: 'success', data }) }
}

async function setSession(page, user, activeRole = user.role) {
  await page.addInitScript(({ user, activeRole }) => {
    localStorage.setItem('queuemate.token', 'qa-token')
    localStorage.setItem('queuemate.user', JSON.stringify(user))
    localStorage.setItem('queuemate.activeRole', activeRole)
  }, { user, activeRole })
}

async function applicantFlow() {
  const context = await browser.newContext({ viewport: { width: 375, height: 812 } })
  const page = await context.newPage()
  const user = { id: '3001', username: 'alice', displayName: 'Alice', phone: '13800003001', role: 'USER', roles: ['USER'], status: 'ACTIVE' }
  await setSession(page, user)
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url()
    if (url.endsWith('/merchant-applications/my')) return route.fulfill(api([]))
    if (url.endsWith('/merchant-applications') && route.request().method() === 'POST') {
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0', message: 'success', data: {
          id: '9901', applicantId: '3001', applicantUsername: 'alice', applicantDisplayName: 'Alice',
          businessName: '青禾空间', contactName: '张三', contactPhone: '13800003001',
          venueName: '青禾自习室·滨江店', venueCategory: 'STUDY_ROOM', addressText: '滨江新区云帆路88号3层',
          description: '提供安静自习座位', status: 'PENDING', reviewNote: null, reviewerId: null,
          submittedAt: '2026-07-20T23:00:00', reviewedAt: null,
        } }),
      })
    }
    return route.fulfill(api([]))
  })

  await page.goto(`${baseUrl}/merchant/application`)
  await page.getByRole('heading', { name: '申请门店入驻' }).waitFor()
  await page.getByLabel('商家或品牌名称').fill('青禾空间')
  await page.getByLabel('联系人姓名').fill('张三')
  await page.getByLabel('联系电话').fill('13800003001')
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('拟入驻门店名称').fill('青禾自习室·滨江店')
  await page.getByLabel('门店详细地址').fill('滨江新区云帆路88号3层')
  await page.getByLabel('经营与服务介绍（选填）').fill('提供安静自习座位')
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '提交入驻申请' }).click()
  await page.getByRole('heading', { name: '资料已进入审核' }).waitFor()
  await page.waitForTimeout(3400)

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  if (overflow !== 0) throw new Error(`Applicant page horizontal overflow: ${overflow}px`)
  const focusState = await page.evaluate(() => ({
    activeTag: document.activeElement?.tagName,
    activeText: document.activeElement?.textContent?.trim(),
    activeClass: document.activeElement?.className,
    skipTransform: getComputedStyle(document.querySelector('.skip-link')).transform,
  }))
  const screenshot = path.join(outputDir, 'queuemate-merchant-application-mobile.png')
  await page.screenshot({ path: screenshot, fullPage: true })
  await context.close()
  return { screenshot, focusState }
}

async function adminFlow() {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const page = await context.newPage()
  const admin = { id: '1001', username: 'admin', displayName: '平台管理员', phone: '13800000000', role: 'ADMIN', roles: ['ADMIN'], status: 'ACTIVE' }
  await setSession(page, admin)
  const application = {
    id: '9901', applicantId: '3001', applicantUsername: 'alice', applicantDisplayName: 'Alice',
    businessName: '青禾空间', contactName: '张三', contactPhone: '13800003001',
    venueName: '青禾自习室·滨江店', venueCategory: 'STUDY_ROOM', addressText: '滨江新区云帆路88号3层',
    description: '提供安静自习座位', status: 'PENDING', reviewNote: null, reviewerId: null,
    submittedAt: '2026-07-20T23:00:00', reviewedAt: null,
  }
  await page.route('**/api/v1/admin/merchant-applications', (route) => route.fulfill(api([application])))
  await page.goto(`${baseUrl}/admin/merchant-applications`)
  await page.getByRole('heading', { name: '商家入驻审核' }).waitFor()
  await page.getByRole('heading', { name: '青禾自习室·滨江店' }).waitFor()
  if (!await page.getByRole('button', { name: '通过申请' }).isVisible()) throw new Error('Approve action is not visible')
  const screenshot = path.join(outputDir, 'queuemate-merchant-review-desktop.png')
  await page.screenshot({ path: screenshot, fullPage: true })
  await context.close()
  return screenshot
}

try {
  const applicantResult = await applicantFlow()
  const adminScreenshot = await adminFlow()
  console.log(JSON.stringify({ applicantScreenshot: applicantResult.screenshot, focusState: applicantResult.focusState, adminScreenshot, mobileOverflow: 0 }))
} finally {
  await browser.close()
}
