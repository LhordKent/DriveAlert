import { expect, test, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'

// This explicitly selected suite consumes records uploaded by the Android integration test.
// It must not clear emulators or seed replacement shared records.
const fixturePath = process.env.DRIVEALERT_INTEGRATION_FIXTURES
type Fixture = {
  case: string; androidPassed: boolean; driverUid: string; contactUid: string
  contactEmail: string; password: string; recordId?: string; sessionId?: string
  offlineQueueVerified?: boolean; mobileSharedDetailsVerified?: boolean
}
const fixtures: Fixture[] = fixturePath ? JSON.parse(readFileSync(fixturePath, 'utf8')) : []

async function signInAsContact(page: Page, fixture: Fixture) {
  await page.goto('/auth/sign-in')
  await page.getByLabel('Email').fill(fixture.contactEmail)
  await page.getByLabel('Password').fill(fixture.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
}

for (const id of ['IT-6', 'IT-7', 'IT-8']) {
  test(`${id}: web reads the same relationship/record created through Android`, async ({ page }) => {
    test.skip(!fixturePath, 'Requires the explicitly requested Android/Firebase integration fixture run.')
    const fixture = fixtures.find((item) => item.case === id)
    expect(fixture?.androidPassed).toBe(true)
    if (!fixture) throw new Error(`Missing passed Android fixture ${id}`)
    await signInAsContact(page, fixture)
    await page.goto('/drivers')
    await expect(page.getByText('Integration Driver', { exact: true })).toBeVisible()
    await expect(page.getByText('Approved', { exact: true })).toBeVisible()
    if (id === 'IT-6') return
    expect(fixture.mobileSharedDetailsVerified).toBe(true)
    if (id === 'IT-8') expect(fixture.offlineQueueVerified).toBe(true)
    await page.goto(`/drivers/${fixture.driverUid}/records/${fixture.recordId}`)
    await expect(page.getByRole('heading', { name: 'Shared alert', exact: true })).toBeVisible()
    await expect(page.getByRole('term')).toHaveText(['Driver', 'Alert time', 'Detected signs'])
    await expect(page.getByText(/Yawning/)).toBeVisible()
    await expect(page.getByText(/Head nodding/i)).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Shared alert', exact: true })).toBeVisible()
  })
}

test('IT-11: web accepts a fresh invitation sent from Android after an Android decline', async ({ page }) => {
  test.skip(!fixturePath, 'Requires the explicitly requested Android/Firebase integration fixture run.')
  const fixture = fixtures.find((item) => item.case === 'IT-11')
  expect(fixture?.androidPassed).toBe(true)
  if (!fixture) throw new Error('Missing passed Android fixture IT-11')

  await signInAsContact(page, fixture)
  await page.goto('/requests')
  const request = page.getByRole('article').filter({ hasText: 'Integration Driver' })
  await expect(request).toContainText('Integration Driver')
  await request.getByRole('button', { name: 'Accept' }).click()
  await expect(page.getByText('No incoming requests')).toBeVisible()
  await page.goto('/drivers')
  await expect(page.getByText('Integration Driver', { exact: true })).toBeVisible()
  await expect(page.getByText('Approved', { exact: true })).toBeVisible()
})
