import { expect, test, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { doc, getDoc } from 'firebase/firestore'
import { addStageRecord, approveTrustedRequest, clearEmulators, createEmulatorUser, disposeFixtureApps, relationshipId, requestFromDriver, type EmulatorUser } from './firebase-fixtures'

const day = 86_400_000
const address = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080'
test.beforeEach(async () => clearEmulators())
test.afterEach(async () => disposeFixtureApps())

async function signIn(page: Page, user: EmulatorUser) {
  await page.goto('/auth/sign-in')
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Password').fill(user.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
}

async function connectHistorically(driver: EmulatorUser, trusted: EmulatorUser) {
  await requestFromDriver(driver, trusted)
  await approveTrustedRequest(driver, trusted)
  const time = Date.now() - 20 * day
  const response = await fetch(`http://${address}/v1/projects/drivealert-lhordkent/databases/(default)/documents/trustedContactConnections/${relationshipId(driver, trusted)}?updateMask.fieldPaths=approvedAt&updateMask.fieldPaths=requestedAt`, {
    method: 'PATCH', headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: { approvedAt: { timestampValue: new Date(time).toISOString() }, requestedAt: { timestampValue: new Date(time - 1000).toISOString() } } }),
  })
  expect(response.ok).toBe(true)
}

test('retention expires original events, persists after reload, updates badges and never deletes source records', async ({ page }) => {
  const trusted = await createEmulatorUser('History', 'TRUSTED_CONTACT', 'DA' + '3'.repeat(26))
  const driver = await createEmulatorUser('Driver', 'DRIVER', 'DA' + '2'.repeat(26))
  await connectHistorically(driver, trusted)
  await addStageRecord(driver, Date.now() - 2 * day, 'old')
  await addStageRecord(driver, Date.now(), 'recent')
  await signIn(page, trusted)
  await expect(page.getByRole('status')).toContainText('2 unread records')
  await page.goto('/settings')
  await page.getByRole('link', { name: /History & Storage/ }).click()
  await page.getByLabel('1 day', { exact: true }).check()
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alertdialog')).toContainText('This removes 1 record')
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await page.getByRole('button', { name: 'Save and apply' }).click()
  await expect(page.getByRole('status')).toContainText('saved in this browser')
  await page.reload()
  await expect(page.getByLabel('1 day', { exact: true })).toBeChecked()
  await page.goto('/dashboard')
  await expect(page.getByRole('status')).toContainText('1 unread record')
  await page.goto('/settings/history')
  await page.getByLabel('7 days', { exact: true }).check()
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('saved in this browser')
  await page.goto(`/drivers/${driver.uid}/records/old`)
  await expect(page.getByRole('heading', { name: 'Record not found' })).toBeVisible()
  expect((await getDoc(doc(driver.firestore, 'users', driver.uid, 'stageSyncRecords', 'old'))).exists()).toBe(true)
  await page.goto(`/drivers/${driver.uid}/records/recent`)
  await page.getByRole('button', { name: 'Delete record', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Delete record', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Record not found' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Record not found' })).toBeVisible()
  expect((await getDoc(doc(driver.firestore, 'users', driver.uid, 'stageSyncRecords', 'recent'))).exists()).toBe(true)
})

test('personal clear updates another tab, stays scoped to its driver, and leaves another account untouched', async ({ page, context }) => {
  const first = await createEmulatorUser('First', 'TRUSTED_CONTACT', 'DA' + '3'.repeat(26))
  const second = await createEmulatorUser('Second', 'TRUSTED_CONTACT', 'DA' + '4'.repeat(26))
  const driver = await createEmulatorUser('Driver', 'DRIVER', 'DA' + '2'.repeat(26))
  const other = await createEmulatorUser('Other', 'DRIVER', 'DA' + '5'.repeat(26))
  await connectHistorically(driver, first); await connectHistorically(driver, second); await connectHistorically(other, first)
  await addStageRecord(driver, Date.now())
  await addStageRecord(other, Date.now())
  await signIn(page, first)
  await page.goto(`/drivers/${driver.uid}`)
  const tab = await context.newPage()
  await tab.goto(`/drivers/${driver.uid}`)
  await expect(tab.getByRole('link', { name: /Entered Stage 3/ })).toBeVisible()
  await page.getByRole('button', { name: 'Clear history' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Clear history' }).click()
  await expect(page.getByRole('heading', { name: 'No shared records' })).toBeVisible()
  await expect(tab.getByRole('heading', { name: 'No shared records' })).toBeVisible()
  await page.goto(`/drivers/${other.uid}`)
  await expect(page.getByRole('link', { name: /Entered Stage 3/ })).toBeVisible()
  await tab.close()
  await page.goto('/settings')
  await page.getByRole('main').getByRole('button', { name: 'Log out' }).click()
  await signIn(page, second)
  await page.goto(`/drivers/${driver.uid}`)
  await expect(page.getByRole('link', { name: /Entered Stage 3/ })).toBeVisible()
})

test('custom controls are accessible at desktop and mobile sizes', async ({ page }, testInfo) => {
  const trusted = await createEmulatorUser('Accessible', 'TRUSTED_CONTACT', 'DA' + '3'.repeat(26))
  await signIn(page, trusted)
  await page.goto('/settings/history')
  await page.getByLabel('Custom', { exact: true }).check()
  await page.getByLabel('Number of days').fill('3651')
  await expect(page.getByRole('button', { name: 'Save', exact: true })).toBeDisabled()
  await page.getByLabel('Number of days').fill('14')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await page.getByRole('button', { name: 'Save and apply' }).click()
  await expect(page.getByRole('status')).toContainText('saved in this browser')
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.screenshot({ path: testInfo.outputPath('history-desktop.png'), fullPage: true })
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()).violations).toEqual([])
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: testInfo.outputPath('history-mobile.png'), fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()).violations).toEqual([])
})
