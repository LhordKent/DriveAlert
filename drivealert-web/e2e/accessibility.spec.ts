import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { clearEmulators, createEmulatorUser, disposeFixtureApps } from './firebase-fixtures'

test.beforeEach(async () => clearEmulators())
test.afterEach(async () => disposeFixtureApps())

test('sign-in has no automatically detectable WCAG A or AA violations', async ({ page }) => {
  await page.goto('/auth/sign-in')
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  expect(results.violations).toEqual([])
})

test('authenticated dashboard has no automatically detectable WCAG A or AA violations', async ({ page }) => {
  const trusted = await createEmulatorUser('Accessible', 'TRUSTED_CONTACT', 'DA3456789ABCDEFGHJKLMNPQRSTU')
  await page.goto('/auth/sign-in')
  await page.getByLabel('Email').fill(trusted.email)
  await page.getByLabel('Password').fill(trusted.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  expect(results.violations).toEqual([])
})
