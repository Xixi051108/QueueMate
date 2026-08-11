import { expect, test } from '@playwright/test'
import {
  cleanupRun,
  createRunId,
  testUserFor,
  verifyCleanupSupport,
} from '../support/api-fixture.js'
import { loginAsAdmin, registerAndLogin } from '../support/ui-actions.js'

test('用户提交商家入驻申请后由管理员审核开通商家身份', async ({ browser, page, request }) => {
  const runId = createRunId()
  const user = testUserFor(runId)
  const businessName = `Playwright Brand ${runId}`
  const venueName = `Playwright Shop ${runId}`
  let adminContext

  try {
    await verifyCleanupSupport(request, runId)

    await test.step('普通用户注册并提交三步入驻申请', async () => {
      await registerAndLogin(page, user)
      await page.goto('/merchant/application')

      await page.getByLabel('商家或品牌名称').fill(businessName)
      await expect(page.getByLabel('联系人姓名')).toHaveValue(user.displayName)
      await expect(page.getByLabel('联系电话')).toHaveValue(user.phone)
      await page.getByRole('button', { name: '下一步' }).click()

      await page.getByLabel('拟入驻门店名称').fill(venueName)
      await page.getByLabel('门店详细地址').fill('Playwright 自动化测试路 11 号')
      await page.getByLabel('经营与服务介绍（选填）').fill(`Playwright onboarding ${runId}`)
      await page.getByRole('button', { name: '下一步' }).click()

      await expect(page.locator('.confirm-summary')).toContainText(businessName)
      await expect(page.locator('.confirm-summary')).toContainText(venueName)
      await page.getByRole('button', { name: '提交入驻申请' }).click()

      await expect(page.locator('.el-message--success')).toContainText('入驻申请已提交')
      await expect(page.locator('.status-card')).toContainText('资料已进入审核')
      await expect(page.locator('.status-card')).toContainText('待审核')
    })

    await test.step('管理员在独立会话中审核通过申请', async () => {
      adminContext = await browser.newContext()
      const adminPage = await adminContext.newPage()
      await loginAsAdmin(adminPage)
      await adminPage.goto('/admin/merchant-applications')

      const application = adminPage.locator('.review-card').filter({ hasText: venueName })
      await expect(application).toContainText(user.username)
      await expect(application).toContainText('待审核')
      await application.getByRole('button', { name: '通过申请' }).click()

      const dialog = adminPage.getByRole('dialog', { name: '通过入驻申请' })
      await dialog.getByRole('textbox').fill(`Playwright approved ${runId}`)
      await dialog.getByRole('button', { name: '确认通过' }).click()

      await expect(adminPage.locator('.el-message--success')).toContainText('入驻申请已通过')
      await expect(application).toHaveCount(0)
    })

    await test.step('用户同步商家身份并进入商家工作台', async () => {
      await page.reload()
      const statusCard = page.locator('.status-card')
      await expect(statusCard).toContainText('商家身份已开通')
      await expect(statusCard).toContainText('已通过')
      await statusCard.getByRole('button', { name: '进入商家工作台' }).click()

      await expect(page).toHaveURL(/\/manage\/venues/)
      await expect(page.getByRole('heading', { name: '我的场所' })).toBeVisible()
      await expect(page.getByRole('button', { name: '创建地点' }).first()).toBeVisible()
    })
  } finally {
    await adminContext?.close()
    await cleanupRun(request, runId)
  }
})
