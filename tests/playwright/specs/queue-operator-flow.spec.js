import { expect, test } from '@playwright/test'
import {
  cleanupRun,
  createRunId,
  prepareQueueRun,
  testUserFor,
  verifyCleanupSupport,
} from '../support/api-fixture.js'
import { loginAsMerchant, registerAndLogin } from '../support/ui-actions.js'

const terminalScenarios = [
  {
    name: '完成服务',
    buttonName: '完成服务',
    successMessage: '完成服务成功',
    expectedStatus: '已完成',
    expectedTime: '完成时间',
  },
  {
    name: '标记过号',
    buttonName: '标记过号',
    successMessage: '标记过号成功',
    expectedStatus: '已过号',
    expectedTime: '过号时间',
  },
]

for (const scenario of terminalScenarios) {
  test(`用户取号后商家叫号并${scenario.name}`, async ({ page, browser, request }) => {
    const runId = createRunId()
    const user = testUserFor(runId)
    const resources = {}
    let merchantContext

    try {
      await verifyCleanupSupport(request, runId)
      await prepareQueueRun(request, runId, resources)

      await test.step('用户注册登录并领取现场号码', async () => {
        await registerAndLogin(page, user)
        await page.goto(`/venues/${resources.venueId}`)
        await page.getByRole('button', { name: '领取现场号码' }).click()
        await page.getByRole('dialog', { name: '现场取号' })
          .getByRole('button', { name: '确认取号' })
          .click()
        await expect(page.getByRole('alert').filter({ hasText: '取号成功' })).toBeVisible()

        await page.goto('/queue')
        const userTicket = page.locator('.queue-ticket').filter({
          hasText: `Postman Venue ${runId}`,
        })
        await expect(userTicket).toContainText('等待中')
      })

      merchantContext = await browser.newContext()
      const merchantPage = await merchantContext.newPage()

      await test.step('商家登录并叫号', async () => {
        await loginAsMerchant(merchantPage)
        await merchantPage.goto(`/manage/venues/${resources.venueId}`)

        const operatorTicket = merchantPage.locator('.queue-record')
        await expect(operatorTicket).toHaveCount(1)
        await expect(operatorTicket).toContainText('等待中')
        await operatorTicket.getByRole('button', { name: '叫号' }).click()
        await expect(merchantPage.getByRole('alert').filter({ hasText: '叫号成功' }))
          .toBeVisible()
        await expect(operatorTicket).toContainText('已叫号')

        await page.reload()
        const userTicket = page.locator('.queue-ticket').filter({
          hasText: `Postman Venue ${runId}`,
        })
        await expect(userTicket).toContainText('已叫号')
        await expect(userTicket).toContainText('叫号时间')
      })

      await test.step(`商家${scenario.name}并同步到用户端`, async () => {
        const operatorTicket = merchantPage.locator('.queue-record')
        await operatorTicket.getByRole('button', { name: scenario.buttonName }).click()
        await expect(merchantPage.getByRole('alert').filter({
          hasText: scenario.successMessage,
        })).toBeVisible()
        await expect(operatorTicket).toHaveCount(0)
        await expect(merchantPage.getByText('当天没有排队号码')).toBeVisible()

        await page.reload()
        const userTicket = page.locator('.queue-ticket').filter({
          hasText: `Postman Venue ${runId}`,
        })
        await expect(userTicket).toContainText(scenario.expectedStatus)
        await expect(userTicket).toContainText(scenario.expectedTime)
      })
    } finally {
      await merchantContext?.close()
      await cleanupRun(request, runId, resources)
    }
  })
}
