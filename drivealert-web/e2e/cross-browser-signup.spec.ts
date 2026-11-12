import { existsSync } from 'node:fs'
import { chromium, expect, test, type Browser, type Page } from '@playwright/test'
import { clearEmulators } from './firebase-fixtures'

// Reproduces the real-world case: sign up in one browser, then confirm the email and sign in from a
// different one (for example, the confirmation link opens in the default browser). Each browser keeps its
// own storage, so the second browser never sees the sign-up details saved by the first.
const PROJECT_ID = 'drivealert-lhordkent'
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099'
const FIRESTORE = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080'
const BRAVE = 'C:/Program Files/BraveSoftware/Brave-Browser/Application/brave.exe'
const EMAIL = 'mara.crossbrowser@example.com'
const PASSWORD = 'Password123!'

const browsers: Browser[] = []
test.beforeEach(async () => clearEmulators())
test.afterEach(async () => { await Promise.all(browsers.splice(0).map((browser) => browser.close())) })

async function launch(kind: 'chrome' | 'brave') {
  // Real installed browsers with fresh, throwaway profiles; personal browser data is never used.
  const browser = kind === 'chrome'
    ? await chromium.launch({ channel: 'chrome' })
    : await chromium.launch({ executablePath: BRAVE })
  browsers.push(browser)
  return (await browser.newContext({ baseURL: test.info().project.use.baseURL })).newPage()
}

async function register(page: Page) {
  await page.goto('/auth/register')
  await page.getByLabel('First name').fill('Mara')
  await page.getByLabel('Last name').fill('Santos')
  await page.getByLabel(/Phone/).fill('+63 900 000 0000')
  await page.getByLabel('Email').fill(EMAIL)
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
  await page.getByLabel('Confirm password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible()
}

async function verificationLink() {
  const response = await fetch(`http://${AUTH}/emulator/v1/projects/${PROJECT_ID}/oobCodes`)
  const { oobCodes } = await response.json() as { oobCodes: Array<{ email: string; oobLink: string; requestType: string }> }
  return oobCodes.filter((code) => code.email === EMAIL && code.requestType === 'VERIFY_EMAIL').at(-1)?.oobLink
}

async function confirmEmailIn(page: Page) {
  // The "Verify your email" screen can render before the emulator records the email, so wait for it.
  await expect.poll(verificationLink, { message: 'verification email', timeout: 10_000 }).toBeTruthy()
  await page.goto((await verificationLink())!)
  await expect(page.getByText(/has been verified|successfully verified/i)).toBeVisible()
}

async function signIn(page: Page) {
  await page.goto('/auth/sign-in')
  await page.getByLabel('Email').fill(EMAIL)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

type Fields = Record<string, { stringValue?: string; nullValue?: null }>
async function storedProfiles() {
  const response = await fetch(`http://${FIRESTORE}/v1/projects/${PROJECT_ID}/databases/(default)/documents/users`, {
    headers: { Authorization: 'Bearer owner' },
  })
  const body = await response.json() as { documents?: Array<{ fields: Fields }> }
  return (body.documents ?? []).map(({ fields }) => Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, value.stringValue ?? null])))
}
async function connectionCodeOwners() {
  const response = await fetch(`http://${FIRESTORE}/v1/projects/${PROJECT_ID}/databases/(default)/documents/connectionCodes`, {
    headers: { Authorization: 'Bearer owner' },
  })
  const body = await response.json() as { documents?: unknown[] }
  return body.documents?.length ?? 0
}

test.describe('sign up in Chrome, finish in Brave', () => {
  test.skip(!existsSync(BRAVE), 'Brave is not installed on this machine.')
  test.setTimeout(120_000)

  test('Brave gets a working profile even though the sign-up details stayed in Chrome', async () => {
    const chrome = await launch('chrome')
    const brave = await launch('brave')

    await register(chrome)
    await confirmEmailIn(brave)
    await signIn(brave)

    await expect(brave.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    await expect(brave.getByText('Profile not found')).toHaveCount(0)
    await brave.goto('/account')
    await expect(brave.getByText('Mara Santos').first()).toBeVisible()

    const [profile] = await storedProfiles()
    expect(profile).toMatchObject({ firstName: 'Mara', lastName: 'Santos', email: EMAIL, userRole: 'TRUSTED_CONTACT', accountStatus: 'ACTIVE' })
    expect(profile.connectionCode).toMatch(/^DA[23456789A-HJ-NP-Z]{26}$/)
    expect(await connectionCodeOwners()).toBe(1)

    // Returning to the original Chrome tab must not create a second profile or a second code.
    await chrome.getByRole('button', { name: "I've verified my email" }).click()
    await expect(chrome.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    const after = await storedProfiles()
    expect(after).toHaveLength(1)
    expect(after[0].connectionCode).toBe(profile.connectionCode)
    expect(await connectionCodeOwners()).toBe(1)
    expect(await chrome.evaluate(() => Object.keys(localStorage).filter((key) => key.startsWith('drivealert.pending-registration.')))).toEqual([])

    // A third, completely fresh browser (another computer) also works from the saved profile.
    const anotherComputer = await launch('chrome')
    await signIn(anotherComputer)
    await expect(anotherComputer.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  })

  test('signing up and finishing in the same browser still keeps every detail, including phone', async () => {
    const brave = await launch('brave')
    await register(brave)
    const link = await brave.context().newPage()
    await confirmEmailIn(link)
    await link.close()
    await brave.getByRole('button', { name: "I've verified my email" }).click()
    await expect(brave.getByRole('heading', { name: 'Dashboard' })).toBeVisible()

    const [profile] = await storedProfiles()
    expect(profile).toMatchObject({ firstName: 'Mara', lastName: 'Santos', phoneNumber: '+63 900 000 0000' })
  })
})
