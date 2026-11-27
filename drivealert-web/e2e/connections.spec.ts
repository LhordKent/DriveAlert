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
  seedLegacyPendingInvitation,
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

test('Settings has no email discovery switch', async ({ page }) => {
  const contact = await createEmulatorUser('Trusted', 'TRUSTED_CONTACT', TRUSTED_CODE)
  await signIn(page, contact.email, contact.password)
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible()
  await expect(page.getByText('Find me by email')).toHaveCount(0)
})

test('redirects the old invite URL and hides previous contact-sent requests', async ({ page }) => {
  const trusted = await createEmulatorUser('Trusted', 'TRUSTED_CONTACT', TRUSTED_CODE)
  const driver = await createEmulatorUser('Driver', 'DRIVER', DRIVER_CODE)
  await seedLegacyPendingInvitation(driver, trusted)
  await signIn(page, trusted.email, trusted.password)
  await expect(page.getByText('No incoming requests')).toBeVisible()
  await expect(page.getByRole('link', { name: '0 Pending requests' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Invite a Driver' })).toHaveCount(0)
  await page.goto('/requests/invite')
  await expect(page).toHaveURL(/\/requests$/)
  await expect(page.getByText('No incoming requests')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Accept' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Cancel request' })).toHaveCount(0)
  await page.getByRole('link', { name: 'View my connection code' }).click()
  await expect(page.getByRole('heading', { name: 'Account' })).toBeVisible()
  await expect(page.getByText('Share this exact code with a Driver you know.', { exact: false })).toBeVisible()
  await page.goto('/drivers')
  await expect(page.getByRole('link', { name: 'Invite a Driver' })).toHaveCount(0)
})

test('accepts a Driver, shows read-only alert rows, then disconnects', async ({ page }) => {
  const trusted = await createEmulatorUser('Trusted', 'TRUSTED_CONTACT', TRUSTED_CODE)
  const driver = await createEmulatorUser('Driver', 'DRIVER', DRIVER_CODE)
  const longEmail = `${'long-name'.repeat(7)}@example.test`
  const phoneNumber = '09123456789'
  await page.route('**/v1/connection-contact-details', async (route) => {
    const headers = { 'access-control-allow-origin': 'http://127.0.0.1:4173', 'access-control-allow-headers': 'Authorization, Content-Type', 'access-control-allow-methods': 'POST' }
    if (route.request().method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers })
      return
    }
    expect(route.request().postDataJSON()).toEqual({ connectionIds: [relationshipId(driver, trusted)] })
    expect(route.request().headers().authorization?.startsWith('Bearer ')).toBe(true)
    await route.fulfill({ headers, json: { details: [{ connectionId: relationshipId(driver, trusted), email: longEmail, phoneNumber }] } })
  })
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
  for (const viewport of [{ width: 360, height: 844 }, { width: 1440, height: 900 }]) {
    await page.setViewportSize(viewport)
    await expect(page.getByText(longEmail)).toBeVisible()
    await expect(page.getByText(phoneNumber)).toBeVisible()
    await expect(page.getByText('Approved', { exact: true })).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  }
  await page.getByRole('link', { name: 'View records' }).click()
  const alerts = page.getByRole('list', { name: 'Shared alerts' })
  await expect(alerts.getByRole('link')).toHaveCount(0)
  await expect(alerts.getByRole('button')).toHaveCount(0)
  await alerts.getByText('Yawning, Head Nodding').click()
  await expect(page).toHaveURL(`/drivers/${driver.uid}`)
  await page.goto(`/drivers/${driver.uid}/records/stage-record-1`)
  await expect(page.getByRole('heading', { name: 'Shared alert' })).toBeVisible()
  await expect(page.getByRole('term')).toHaveText(['Driver', 'Alert time', 'Detected signs'])
  await expect(page.getByText('session-e2e')).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Shared alert' })).toBeVisible()
  await page.goto('/drivers')
  await page.getByRole('button', { name: /Disconnect/ }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Disconnect', exact: true }).click()
  await expect(page.getByText('No connected Drivers')).toBeVisible()
  await expect(page.getByText(phoneNumber)).toHaveCount(0)
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
      [`/drivers/${driver.uid}/records/stage-record-1`, 'Shared alert'],
      ['/requests', 'Requests'],
      ['/account', 'Account'], ['/settings', 'Settings'], ['/settings/history', 'History & Storage'],
    ]) {
      await page.goto(path)
      await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
      if (path === '/dashboard' || path === `/drivers/${driver.uid}`) {
        const alerts = page.getByRole('list', { name: 'Shared alerts' })
        await expect(alerts.getByText('Yawning, Head Nodding')).toBeVisible()
        await expect(alerts.getByRole('link')).toHaveCount(0)
      }
      await expect(page.locator('body')).not.toContainText(/stage[\s_-]*3/i)
      expect(await page.locator('meta[name="description"]').getAttribute('content')).not.toMatch(/stage[\s_-]*3/i)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      if (title === 'Dashboard' || title === 'Shared alert') {
        await page.screenshot({ path: testInfo.outputPath(`${title === 'Dashboard' ? 'dashboard' : 'record'}-${viewport.width}.png`), fullPage: true })
      }
    }
    await page.goto('/settings')
    await page.getByRole('link', { name: /About DriveAlert/ }).click()
    await expect(page.getByRole('heading', { name: 'About DriveAlert', exact: true })).toBeVisible()
    await expect(page.locator('body')).toContainText('Stage 3')
  }
})
