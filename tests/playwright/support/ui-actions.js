import { expect } from '@playwright/test'

export async function registerAndLogin(page, user) {
  await page.goto('/register')
  await page.getByLabel('用户名').fill(user.username)
  await page.getByLabel('显示名称').fill(user.displayName)
  await page.getByLabel('手机号（可选）').fill(user.phone)
  await page.getByLabel('密码', { exact: true }).fill(user.password)
  await page.getByLabel('确认密码').fill(user.password)
  await page.getByRole('button', { name: '创建账号' }).click()

  await expect(page).toHaveURL(/\/login/)
  await expect(page.locator('.el-message--success')).toContainText('注册成功，请登录')
  await page.getByLabel('密码').fill(user.password)
  await page.getByRole('button', { name: '登录并继续' }).click()
  await expect(page).toHaveURL(/\/venues/)
}

export async function loginAsMerchant(page) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('merchant_tea')
  await page.getByLabel('密码').fill('Merchant123456')
  await page.getByRole('button', { name: '登录并继续' }).click()
  await expect(page).toHaveURL(/\/manage\/venues/)
}

export async function loginAsAdmin(page) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('Admin123456')
  await page.getByRole('button', { name: '登录并继续' }).click()
  await expect(page).toHaveURL(/\/manage\/venues/)
}
