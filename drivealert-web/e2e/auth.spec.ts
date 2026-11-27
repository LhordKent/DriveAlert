import { expect, test } from '@playwright/test'
import { confirmPasswordReset, getAuth } from 'firebase/auth'
import { clearEmulators, createEmulatorUser, disposeFixtureApps } from './firebase-fixtures'

const projectId = 'drivealert-lhordkent'
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099'
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080'

async function getEmailCode(email: string, requestType: 'VERIFY_EMAIL' | 'PASSWORD_RESET') {
  const response = await fetch(`http://${authHost}/emulator/v1/projects/${projectId}/oobCodes`)
  const { oobCodes } = await response.json() as {
    oobCodes: Array<{ email: string; oobCode: string; oobLink: string; requestType: string }>
  }
  return oobCodes.filter((code) => code.email === email && code.requestType === requestType).at(-1)
}

async function firestoreFields(path: string) {
  const response = await fetch(
    `http://${firestoreHost}/v1/projects/${projectId}/databases/(default)/documents/${path}`,
    { headers: { Authorization: 'Bearer owner' } },
  )
  expect(response.ok).toBe(true)
  return response.json() as Promise<{
    fields?: Record<string, { stringValue?: string }>
    documents?: Array<{ fields: Record<string, { stringValue?: string }> }>
  }>
}

test.beforeEach(async () => clearEmulators())
test.afterEach(async () => disposeFixtureApps())

test('registers a Trusted Contact, restores the session, logs out, and signs back in', async ({ page }) => {
  test.setTimeout(60_000)
  await page.goto('/auth/register')
  await page.getByLabel('First name').fill('Mara1')
  await expect(page.getByLabel('First name')).toHaveValue('Mara')
  await page.getByLabel('Last name').fill('Santos')
  await page.getByLabel('Suffix (optional)').fill(' 3rd ')
  await page.getByLabel('Email').fill('mara.portal@example.com')
  await page.getByLabel('Password', { exact: true }).fill('Password123!')
  await page.getByLabel('Confirm password').fill('Password123!')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByText('Phone number is required.')).toBeVisible()
  await page.getByLabel('Phone number').fill('09000000000')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible()
  await page.getByRole('button', { name: "I've verified my email" }).click()
  await expect(page.getByText(/Your email is not verified yet/)).toBeVisible()
  expect((await firestoreFields('users')).documents ?? []).toHaveLength(0)
  await expect.poll(() => getEmailCode('mara.portal@example.com', 'VERIFY_EMAIL')).toBeTruthy()
  const verification = await getEmailCode('mara.portal@example.com', 'VERIFY_EMAIL')
  const verificationPage = await page.context().newPage()
  await verificationPage.goto(verification!.oobLink)
  await expect(verificationPage.getByText(/has been verified|successfully verified/i)).toBeVisible()
  await verificationPage.close()
  await page.getByRole('button', { name: "I've verified my email" }).click()
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
  await page.goto('/account')
  await expect(page.getByText('Mara Santos 3rd', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: 'Edit profile' }).click()
  await page.getByLabel('Last name').fill('Reyes')
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.getByText('Profile updated.')).toBeVisible()
  await page.reload()
  await expect(page.getByText('Mara Reyes 3rd', { exact: true }).first()).toBeVisible()
  const [profile] = (await firestoreFields('users')).documents ?? []
  expect(profile.fields).toMatchObject({ lastName: { stringValue: 'Reyes' }, suffix: { stringValue: '3rd' } })
  const code = profile.fields.connectionCode.stringValue
  expect((await firestoreFields(`connectionCodes/${code}`)).fields?.displayName.stringValue).toBe('Mara Reyes 3rd')
})

test('accepts a legacy password at sign-in and lets the user replace it through a reset link', async ({ page }) => {
  const contact = await createEmulatorUser('Legacy', 'TRUSTED_CONTACT', 'DA23456789ABCDEFGHJKLMNPQRST', 'safe-drive')
  await page.goto('/auth/sign-in')
  const signIn = async (password: string) => {
    await page.getByLabel('Email').fill(contact.email)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Sign in' }).click()
  }
  await signIn(contact.password)
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  await page.getByRole('button', { name: 'Log out' }).click()
  await page.getByRole('link', { name: 'Forgot password?' }).click()
  await page.getByLabel('Email').fill(contact.email)
  await page.getByRole('button', { name: 'Send reset link' }).click()
  await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible()
  await expect.poll(() => getEmailCode(contact.email, 'PASSWORD_RESET')).toBeTruthy()
  const reset = await getEmailCode(contact.email, 'PASSWORD_RESET')
  const newPassword = 'Fresh-pass#'
  await confirmPasswordReset(getAuth(contact.firestore.app), reset!.oobCode, newPassword)

  await page.getByRole('link', { name: 'Back to sign in' }).click()
  await signIn(contact.password)
  await expect(page.getByRole('alert')).toBeVisible()
  await signIn(newPassword)
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
})

test('shows only profile details and requires a valid phone number when editing', async ({ page }) => {
  const contact = await createEmulatorUser('Profile', 'TRUSTED_CONTACT', 'DA23456789ABCDEFGHJKLMNPQRST')
  await page.goto('/auth/sign-in')
  await page.getByLabel('Email').fill(contact.email)
  await page.getByLabel('Password').fill(contact.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  await page.goto('/account')
  await expect(page.getByText('Phone number', { exact: true })).toBeVisible()
  await expect(page.getByText('User role', { exact: true })).toHaveCount(0)
  await expect(page.getByText('Account status', { exact: true })).toHaveCount(0)

  await page.getByRole('button', { name: 'Edit profile' }).click()
  const phone = page.getByRole('textbox', { name: 'Phone number', exact: true })
  const save = page.getByRole('button', { name: 'Save changes' })
  await expect(phone).toHaveAttribute('required', '')
  await save.click()
  await expect(phone).toBeFocused()
  await phone.fill('12345')
  await save.click()
  await expect(page.getByText('Enter exactly 11 digits.')).toBeVisible()
  await phone.fill('08123456789')
  await save.click()
  await expect(page.getByText('Profile updated.')).toBeVisible()
  await page.reload()
  await expect(page.getByText('08123456789')).toBeVisible()
})

test('shows an explicit unauthorized-role state for Driver-only accounts', async ({ page }) => {
  const driver = await createEmulatorUser('DriverOnly', 'DRIVER', 'DA23456789ABCDEFGHJKLMNPQRST')
  await page.goto('/auth/sign-in')
  await page.getByLabel('Email').fill(driver.email)
  await page.getByLabel('Password').fill(driver.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Trusted Contact access required' })).toBeVisible()
})
