import { expect, test } from '@playwright/test'
import {
  addStageRecord,
  approveTrustedRequest,
  clearEmulators,
  createEmulatorUser,
  disposeFixtureApps,
  relationshipId,
  requestFromDriver,
} from './firebase-fixtures'

const DRIVER_CODE = 'DA23456789ABCDEFGHJKLMNPQRST'
const TRUSTED_CODE = 'DA3456789ABCDEFGHJKLMNPQRSTU'

test.beforeEach(async () => clearEmulators())
test.afterEach(async () => disposeFixtureApps())

async function signIn(page: import('@playwright/test').Page, email: string, password: string) {
  await page.goto('/auth/sign-in')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
}

test('invites a Driver, shows the outgoing request, and cancels it', async ({ page }) => {
  const trusted = await createEmulatorUser('Trusted', 'TRUSTED_CONTACT', TRUSTED_CODE)
  const driver = await createEmulatorUser('Driver', 'DRIVER', DRIVER_CODE)
  await signIn(page, trusted.email, trusted.password)
  await page.goto('/requests/invite')
  await page.getByLabel('DriveAlert connection code').fill(driver.code)
  await page.getByRole('button', { name: 'Find Driver' }).click()
  await expect(page.getByText(driver.name)).toBeVisible()
  await page.getByRole('button', { name: 'Send connection request' }).click()
  await expect(page.getByRole('heading', { name: 'Outgoing requests' })).toBeVisible()
  await expect(page.getByText(driver.name)).toBeVisible()
  await page.getByRole('button', { name: 'Cancel request' }).click()
  await expect(page.getByText('No outgoing requests')).toBeVisible()
})

test('accepts a Driver, opens and reloads Stage 3 detail, then disconnects', async ({ page }) => {
  const trusted = await createEmulatorUser('Trusted', 'TRUSTED_CONTACT', TRUSTED_CODE)
  const driver = await createEmulatorUser('Driver', 'DRIVER', DRIVER_CODE)
  await requestFromDriver(driver, trusted)
  await signIn(page, trusted.email, trusted.password)
  await page.goto('/requests')
  await expect(page.getByText(driver.name)).toBeVisible()
  await page.getByRole('button', { name: 'Accept' }).click()
  await expect(page.getByText('No incoming requests')).toBeVisible()
  const approvedAt = Date.now()
  await addStageRecord(driver, approvedAt)
  await page.goto('/drivers')
  await expect(page.getByText(driver.name)).toBeVisible()
  await page.getByRole('link', { name: 'View records' }).click()
  await expect(page.getByText('Entered Stage 3')).toBeVisible()
  await page.getByRole('link', { name: /Entered Stage 3/ }).click()
  await expect(page.getByRole('heading', { name: 'Entered Stage 3' })).toBeVisible()
  await expect(page.getByText('session-e2e')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Entered Stage 3' })).toBeVisible()
  await page.goto('/drivers')
  await page.getByRole('button', { name: /Disconnect/ }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Disconnect', exact: true }).click()
  await expect(page.getByText('No connected Drivers')).toBeVisible()
  await page.goto(`/drivers/${driver.uid}/records/stage-record-1`)
  await expect(page.getByRole('heading', { name: 'Record unavailable' })).toBeVisible()
})

test('declines an incoming request', async ({ page }) => {
  const trusted = await createEmulatorUser('Trusted', 'TRUSTED_CONTACT', TRUSTED_CODE)
  const driver = await createEmulatorUser('Driver', 'DRIVER', DRIVER_CODE)
  await requestFromDriver(driver, trusted)
  await signIn(page, trusted.email, trusted.password)
  await page.goto('/requests')
  await page.getByRole('button', { name: 'Decline' }).click()
  await expect(page.getByText('No incoming requests')).toBeVisible()
})

test('shows an approved Driver after the Driver accepts a Trusted Contact request', async ({ page }) => {
  const trusted = await createEmulatorUser('Trusted', 'TRUSTED_CONTACT', TRUSTED_CODE)
  const driver = await createEmulatorUser('Driver', 'DRIVER', DRIVER_CODE)
  await signIn(page, trusted.email, trusted.password)
  await page.goto('/requests/invite')
  await page.getByLabel('DriveAlert connection code').fill(driver.code)
  await page.getByRole('button', { name: 'Find Driver' }).click()
  await page.getByRole('button', { name: 'Send connection request' }).click()
  await expect(page.getByRole('heading', { name: 'Outgoing requests' })).toBeVisible()
  await expect(page.getByText(driver.name)).toBeVisible()
  await approveTrustedRequest(driver, trusted)
  await page.goto('/drivers')
  await expect(page.getByText(driver.name)).toBeVisible()
  expect(relationshipId(driver, trusted)).toContain('__')
})
