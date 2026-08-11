import { expect, test } from '@playwright/test'
import {
  cleanupRun,
  createRunId,
  verifyCleanupSupport,
} from '../support/api-fixture.js'
import { loginAsAdmin } from '../support/ui-actions.js'

const ADJUSTMENT_USER_ID = 3001

test('管理员增加并扣减用户余额后流水正确且余额恢复', async ({ page, request }) => {
  const runId = createRunId()
  const addRemark = `Postman admin adjustment ${runId}`
  const restoreRemark = `Postman admin adjustment cleanup ${runId}`
  let startingBalance
  let increasedBalance

  try {
    await verifyCleanupSupport(request, runId)
    await loginAsAdmin(page)
    await page.goto('/admin/wallets')

    await test.step('管理员为 Alice 增加 5 元', async () => {
      await adjustBalance(page, 5, addRemark)
      await expect(page.locator('.el-message--success')).toContainText('余额调整成功')

      const transaction = page.locator('.transaction-list article').filter({ hasText: addRemark })
      await expect(transaction).toContainText('余额调整')
      await expect(transaction).toContainText('+¥5.00')
      const balances = await balanceTransition(transaction)
      startingBalance = balances[0]
      increasedBalance = balances[1]
      expect(increasedBalance - startingBalance).toBe(5)
    })

    await test.step('管理员扣减 5 元并将余额恢复为原值', async () => {
      await adjustBalance(page, -5, restoreRemark)
      await expect(page.locator('.el-message--success')).toContainText('余额调整成功')

      const transaction = page.locator('.transaction-list article').filter({ hasText: restoreRemark })
      await expect(transaction).toContainText('余额调整')
      await expect(transaction).toContainText('-¥5.00')
      const [beforeRestore, restoredBalance] = await balanceTransition(transaction)
      expect(beforeRestore).toBe(increasedBalance)
      expect(restoredBalance).toBe(startingBalance)
      await expect(page.locator('.transaction-list article').filter({ hasText: addRemark })).toHaveCount(1)
    })
  } finally {
    await cleanupRun(request, runId)
  }
})

async function balanceTransition(transaction) {
  const text = await transaction.locator('.transaction-balance span').textContent()
  const match = text?.match(/¥([\d,.]+)\s*→\s*¥([\d,.]+)/)
  expect(match, `无法解析余额变化：${text}`).toBeTruthy()
  return match.slice(1).map((value) => Number(value.replaceAll(',', '')))
}

async function adjustBalance(page, amount, remark) {
  await page.getByRole('button', { name: '调整余额' }).click()
  const dialog = page.getByRole('dialog', { name: '调整用户余额' })
  const spinbuttons = dialog.getByRole('spinbutton')
  await spinbuttons.nth(0).fill(String(ADJUSTMENT_USER_ID))
  await spinbuttons.nth(1).fill(String(amount))
  await dialog.getByRole('textbox', { name: '调整原因' }).fill(remark)
  await dialog.getByRole('button', { name: '确认调整' }).click()

  const confirmation = page.getByRole('dialog', { name: '确认余额调整' })
  await confirmation.getByRole('button', { name: '确认调整' }).click()
}
