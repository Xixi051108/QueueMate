import { expect, test } from '@playwright/test'
import {
  cleanupRun,
  createRunId,
  prepareVoucherRedeemRun,
  testUserFor,
  verifyCleanupSupport,
} from '../support/api-fixture.js'
import { loginAsMerchant, registerAndLogin } from '../support/ui-actions.js'

test('商家核销消费码后预约完成且不能重复核销', async ({ browser, page, request }) => {
  const runId = createRunId()
  const user = testUserFor(runId)
  const resources = {}
  let merchantContext

  try {
    await verifyCleanupSupport(request, runId)
    await prepareVoucherRedeemRun(request, runId, resources)

    await test.step('用户注册登录、充值并完成付费预约', async () => {
      await registerAndLogin(page, user)

      await page.goto('/wallet')
      await page.getByRole('button', { name: '模拟充值' }).click()
      const rechargeDialog = page.getByRole('dialog', { name: '模拟充值' })
      await rechargeDialog.getByRole('spinbutton').fill('50')
      await rechargeDialog.getByPlaceholder('例如：本地演示充值').fill(`Playwright ${runId}`)
      await rechargeDialog.getByRole('button', { name: '确认充值' }).click()
      await expect(page.locator('.el-message--success')).toContainText('充值成功')

      await page.goto(`/venues/${resources.venueId}`)
      const slot = page.locator('.slot-row').filter({ hasText: resources.slotTimeLabel })
      await expect(slot).toContainText('¥20.00')
      await slot.getByRole('button', { name: '确认预约' }).click()
      await page.getByRole('dialog', { name: '确认预约' })
        .getByRole('button', { name: '确认预约' })
        .click()
      await expect(page.locator('.el-message--success')).toContainText('预约成功')
    })

    let consumptionCode
    await test.step('用户取得可使用的消费码', async () => {
      await page.goto('/bookings')
      const booking = page.locator('.booking-ticket').filter({
        hasText: `Postman Venue ${runId}`,
      })
      await expect(booking).toContainText('已预约')
      await expect(booking).toContainText('可使用')
      consumptionCode = (await booking.locator('.voucher strong').textContent())?.trim()
      expect(consumptionCode).toMatch(/^[A-Za-z0-9]{8,32}$/)
    })

    await test.step('商家在独立会话中核销消费码并拒绝重复核销', async () => {
      merchantContext = await browser.newContext()
      const merchantPage = await merchantContext.newPage()
      await loginAsMerchant(merchantPage)
      await merchantPage.goto(`/manage/venues/${resources.venueId}`)

      const consumptionCodeInput = merchantPage.getByRole('textbox', { name: '消费码' })
      await consumptionCodeInput.fill(consumptionCode)
      await merchantPage.getByRole('button', { name: '确认核销' }).click()
      await expect(merchantPage.locator('.el-message--success')).toContainText('消费码核销成功')
      await expect(merchantPage.locator('.redeem-result')).toContainText('已完成')

      await consumptionCodeInput.fill(consumptionCode)
      await merchantPage.getByRole('button', { name: '确认核销' }).click()
      await expect(merchantPage.locator('.el-message--error')).toContainText('消费码当前状态不可用')
    })

    await test.step('用户看到预约和消费码的最终状态', async () => {
      await page.reload()
      const booking = page.locator('.booking-ticket').filter({
        hasText: `Postman Venue ${runId}`,
      })
      await expect(booking).toContainText('已完成')
      await expect(booking).toContainText('已核销')
      await expect(booking.getByRole('button', { name: '取消预约' })).toHaveCount(0)
    })
  } finally {
    await merchantContext?.close()
    await cleanupRun(request, runId, resources)
  }
})
