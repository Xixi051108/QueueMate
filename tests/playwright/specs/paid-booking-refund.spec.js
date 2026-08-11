import { expect, test } from '@playwright/test'
import {
  cleanupRun,
  createRunId,
  preparePaidBookingRun,
  testUserFor,
  verifyCleanupSupport,
} from '../support/api-fixture.js'
import { registerAndLogin } from '../support/ui-actions.js'

test('用户付费预约取消后获得全额退款', async ({ page, request }) => {
  const runId = createRunId()
  const user = testUserFor(runId)
  const resources = {}

  try {
    await verifyCleanupSupport(request, runId)
    await preparePaidBookingRun(request, runId, resources)

    await test.step('注册并登录普通用户', async () => {
      await registerAndLogin(page, user)
    })

    await test.step('充值 50 元', async () => {
      await page.goto('/wallet')
      await page.getByRole('button', { name: '模拟充值' }).click()

      const dialog = page.getByRole('dialog', { name: '模拟充值' })
      await dialog.getByRole('spinbutton').fill('50')
      await dialog.getByPlaceholder('例如：本地演示充值').fill(`Playwright ${runId}`)
      await dialog.getByRole('button', { name: '确认充值' }).click()

      await expect(page.locator('.el-message--success')).toContainText('充值成功')
      await expect(page.locator('.wallet-card .data-value')).toHaveText('¥50.00')
    })

    await test.step('预约收费时段并验证扣款', async () => {
      await page.goto(`/venues/${resources.venueId}`)
      const slot = page.locator('.slot-row').filter({ hasText: '19:00–20:00' })

      await expect(slot).toContainText('¥20.00')
      await slot.getByRole('button', { name: '确认预约' }).click()
      await page.getByRole('dialog', { name: '确认预约' })
        .getByRole('button', { name: '确认预约' })
        .click()
      await expect(page.locator('.el-message--success')).toContainText('预约成功')

      await page.goto('/wallet')
      await expect(page.locator('.wallet-card .data-value')).toHaveText('¥30.00')
    })

    await test.step('取消预约并验证退款和凭证作废', async () => {
      await page.goto('/bookings')
      const booking = page.locator('.booking-ticket').filter({
        hasText: `Postman Venue ${runId}`,
      })

      await expect(booking).toContainText('已支付')
      await expect(booking).toContainText('¥20.00')
      await expect(booking).toContainText('可使用')
      await booking.getByRole('button', { name: '取消预约' }).click()

      const dialog = page.getByRole('dialog', { name: '取消预约' })
      await dialog.getByRole('textbox').fill('Playwright 自动取消')
      await dialog.getByRole('button', { name: '确认取消' }).click()

      await expect(page.locator('.el-message--success')).toContainText('已取消预约')
      await expect(booking).toContainText('已取消')
      await expect(booking).toContainText('已退款')
      await expect(booking).toContainText('已作废')

      await page.goto('/wallet')
      await expect(page.locator('.wallet-card .data-value')).toHaveText('¥50.00')
      await expect(page.locator('.transaction-list')).toContainText('退款')
    })
  } finally {
    await cleanupRun(request, runId, resources)
  }
})
