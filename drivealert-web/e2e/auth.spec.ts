import { expect, test } from '@playwright/test'
import { clearEmulators, createEmulatorUser, disposeFixtureApps } from './firebase-fixtures'

test.beforeEach(async () => clearEmulators())
test.afterEach(async () => disposeFixtureApps())

test('registers a Trusted Contact, restores the session, logs out, and signs back in', async ({ page }) => {
  await page.goto('/auth/register')
  await page.getByLabel('First name').fill('Mara')
  await page.getByLabel('Last name').fill('Santos')
  await page.getByLabel('Email').fill('mara.portal@example.com')
  await page.getByLabel('Password', { exact: true }).fill('Password123!')
  await page.getByLabel('Confirm password').fill('Password123!')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(page).toHaveURL(/\/auth\/sign-in/)
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/auth\/sign-in/)
  await page.getByLabel('Email').fill('mara.portal@example.com')
  await page.getByLabel('Password').fill('Password123!')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
})

test('shows an explicit unauthorized-role state for Driver-only accounts', async ({ page }) => {
  const driver = await createEmulatorUser('DriverOnly', 'DRIVER', 'DA23456789ABCDEFGHJKLMNPQRST')
  await page.goto('/auth/sign-in')
  await page.getByLabel('Email').fill(driver.email)
  await page.getByLabel('Password').fill(driver.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Trusted Contact access required' })).toBeVisible()
})
