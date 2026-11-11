import { expect, test } from '@playwright/test'
import { doc, getDocFromServer } from 'firebase/firestore'
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

test('accepts a Driver, opens and reloads shared alert detail, then disconnects', async ({ page }) => {
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
  await expect(page.getByText('Alert started')).toBeVisible()
  await page.getByRole('link', { name: /Alert started/ }).click()
  await expect(page.getByRole('heading', { name: 'Alert started' })).toBeVisible()
  await expect(page.getByText('session-e2e')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Alert started' })).toBeVisible()
  await page.goto('/drivers')
  await page.getByRole('button', { name: /Disconnect/ }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Disconnect', exact: true }).click()
  await expect(page.getByText('No connected Drivers')).toBeVisible()
  // The list updates optimistically; verify the revocation reached the server before reloading.
  await expect.poll(async () => {
    const host = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080'
    const response = await fetch(`http://${host}/v1/projects/drivealert-lhordkent/databases/(default)/documents/trustedContactConnections/${relationshipId(driver, trusted)}`, { headers: { Authorization: 'Bearer owner' } })
    const document = await response.json() as { fields?: { status?: { stringValue?: string } } }
    return document.fields?.status?.stringValue
  }).toBe('REVOKED')
  await expect(getDocFromServer(doc(trusted.firestore, 'users', driver.uid, 'stageSyncRecords', 'stage-record-1'))).rejects.toMatchObject({ code: 'permission-denied' })
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

test('uses shared-alert language everywhere except About DriveAlert on desktop and mobile', async ({ page }, testInfo) => {
  const trusted = await createEmulatorUser('Trusted', 'TRUSTED_CONTACT', TRUSTED_CODE)
  const driver = await createEmulatorUser('Driver', 'DRIVER', DRIVER_CODE)
  await requestFromDriver(driver, trusted)
  const { approvedAt } = await approveTrustedRequest(driver, trusted)
  await addStageRecord(driver, approvedAt)
  await signIn(page, trusted.email, trusted.password)

  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport)
    for (const [path, title] of [
      ['/dashboard', 'Dashboard'], ['/drivers', 'Connected Drivers'],
      [`/drivers/${driver.uid}`, driver.name],
      [`/drivers/${driver.uid}/records/stage-record-1`, 'Alert started'],
      ['/requests', 'Requests'], ['/requests/invite', 'Invite a Driver'],
      ['/account', 'Account'], ['/settings', 'Settings'], ['/settings/history', 'History & Storage'],
    ]) {
      await page.goto(path)
      await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
      if (path === '/dashboard' || path === `/drivers/${driver.uid}`) await expect(page.getByText('Alert started')).toBeVisible()
      await expect(page.locator('body')).not.toContainText(/stage[\s_-]*3/i)
      expect(await page.locator('meta[name="description"]').getAttribute('content')).not.toMatch(/stage[\s_-]*3/i)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      if (title === 'Dashboard' || title === 'Alert started') {
        await page.screenshot({ path: testInfo.outputPath(`${title === 'Dashboard' ? 'dashboard' : 'record'}-${viewport.width}.png`), fullPage: true })
      }
    }
    await page.goto('/settings')
    await page.getByRole('link', { name: /About DriveAlert/ }).click()
    await expect(page.getByRole('heading', { name: 'About DriveAlert', exact: true })).toBeVisible()
    await expect(page.locator('body')).toContainText('Stage 3')
  }
})
