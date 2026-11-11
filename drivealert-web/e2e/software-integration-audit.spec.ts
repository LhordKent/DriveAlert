import { expect, test } from '@playwright/test'
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

for (const id of ['IT-6', 'IT-7', 'IT-8']) {
  test(`${id}: web reads the same relationship/record created through Android`, async ({ page }) => {
    test.skip(!fixturePath, 'Requires the explicitly requested Android/Firebase integration fixture run.')
    const fixture = fixtures.find((item) => item.case === id)
    expect(fixture?.androidPassed).toBe(true)
    if (!fixture) throw new Error(`Missing passed Android fixture ${id}`)
    await page.goto('/auth/sign-in')
    await page.getByLabel('Email').fill(fixture.contactEmail)
    await page.getByLabel('Password').fill(fixture.password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    await page.goto('/drivers')
    await expect(page.getByText('Integration Driver', { exact: true })).toBeVisible()
    await expect(page.getByText('Approved', { exact: true })).toBeVisible()
    if (id === 'IT-6') return
    expect(fixture.mobileSharedDetailsVerified).toBe(true)
    if (id === 'IT-8') expect(fixture.offlineQueueVerified).toBe(true)
    await page.goto(`/drivers/${fixture.driverUid}/records/${fixture.recordId}`)
    await expect(page.getByRole('heading', { name: 'Alert started', exact: true })).toBeVisible()
    await expect(page.getByText(fixture.sessionId!, { exact: true })).toBeVisible()
    await expect(page.getByText(/Yawning/)).toBeVisible()
    await expect(page.getByText(/Head nodding/i)).toBeVisible()
    const countRow = page.getByText('Events counted in this period', { exact: true }).locator('..')
    await expect(countRow).toContainText('2')
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Alert started', exact: true })).toBeVisible()
  })
}
