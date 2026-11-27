import { beforeEach, expect, it, vi } from 'vitest'
import { registrationSchema, signInSchema } from '../../src/features/auth/schemas'
import { requiredPhoneNumberSchema } from '../../src/lib/phoneNumber'
import { newPasswordRequirements } from '../../src/lib/password'

vi.mock('../../src/firebase/client', () => ({ auth: {}, firestore: {} }))
vi.mock('../../src/repositories/profileRepository', () => ({ profileRepository: { createTrustedContact: vi.fn() } }))
vi.mock('firebase/auth', async (original) => ({
  ...await original<typeof import('firebase/auth')>(),
  createUserWithEmailAndPassword: vi.fn(), updateProfile: vi.fn(), sendEmailVerification: vi.fn(),
}))

const { authRepository } = await import('../../src/repositories/authRepository')
const { createUserWithEmailAndPassword, updateProfile, sendEmailVerification } = await import('firebase/auth')
const { profileRepository } = await import('../../src/repositories/profileRepository')
const input = { firstName: 'Unit', lastName: 'Test', email: 'unit@example.test',
  phoneNumber: '12345678901', password: 'Password123!', confirmPassword: 'Password123!' }

beforeEach(() => { vi.clearAllMocks(); localStorage.clear() })

it('requires exactly eleven ASCII digits with any prefix', () => {
  for (const phoneNumber of ['09171234567', '08171234567', '12345678901', ' 12345678901 ']) {
    expect(registrationSchema.safeParse({ ...input, phoneNumber }).success).toBe(true)
  }
  for (const phoneNumber of [undefined, '', '   ', '1234567890', '123456789012', '1234567890a',
    '+639171234567', '0917 1234567', '0917-1234567', '09١٢٣٤٥٦٧٨٩']) {
    expect(registrationSchema.safeParse({ ...input, phoneNumber }).success).toBe(false)
  }
  expect(requiredPhoneNumberSchema.parse(' 12345678901 ')).toBe('12345678901')
})

it('rejects invalid direct registration calls before creating a Firebase identity', async () => {
  for (const phoneNumber of ['', '1234567890', '123456789012', '1234567890a']) {
    await expect(authRepository.register({ ...input, phoneNumber })).rejects.toThrow()
  }
  expect(createUserWithEmailAndPassword).not.toHaveBeenCalled()
  expect(localStorage.length).toBe(0)
})

it('rejects numbers in names while accepting them in a suffix', async () => {
  for (const name of [{ firstName: 'Unit1' }, { middleName: 'Test٢' }, { lastName: 'Test3' }]) {
    expect(registrationSchema.safeParse({ ...input, ...name }).success).toBe(false)
    await expect(authRepository.register({ ...input, ...name })).rejects.toThrow('Names cannot contain numbers.')
  }
  expect(createUserWithEmailAndPassword).not.toHaveBeenCalled()
  expect(registrationSchema.parse({ ...input, suffix: ' 3rd ' }).suffix).toBe('3rd')
})

it('requires strong new passwords even on direct calls and keeps older passwords valid for sign-in', async () => {
  for (const password of ['Short!', 'alllower!', 'ALLUPPER!', 'NoSpecial123', 'Space Only', 'EmojiOnly🔒']) {
    expect(registrationSchema.safeParse({ ...input, password, confirmPassword: password }).success).toBe(false)
    await expect(authRepository.register({ ...input, password })).rejects.toThrow(newPasswordRequirements)
  }
  expect(createUserWithEmailAndPassword).not.toHaveBeenCalled()
  for (const password of ['SafePass!', 'Safe-drive', 'SafePass`']) {
    expect(registrationSchema.safeParse({ ...input, password, confirmPassword: password }).success).toBe(true)
  }
  expect(signInSchema.safeParse({ email: input.email, password: 'safe-drive' }).success).toBe(true)
})

it('persists the valid number for verified profile completion without saving the password', async () => {
  const user = { uid: 'registration-unit' }
  vi.mocked(createUserWithEmailAndPassword).mockResolvedValue({ user } as never)
  vi.mocked(updateProfile).mockResolvedValue()
  vi.mocked(sendEmailVerification).mockResolvedValue()
  await authRepository.register({ ...input, phoneNumber: ' 12345678901 ' })
  expect(createUserWithEmailAndPassword).toHaveBeenCalledOnce()
  const pending = JSON.parse(localStorage.getItem('drivealert.pending-registration.registration-unit')!)
  expect(pending.phoneNumber).toBe('12345678901')
  expect(pending.password).toBeUndefined()
  expect(pending).not.toHaveProperty('suffix')
})

it('keeps a trimmed suffix through verification and includes it in the display name', async () => {
  const user = { uid: 'registration-unit', email: input.email, emailVerified: false }
  vi.mocked(createUserWithEmailAndPassword).mockResolvedValue({ user } as never)
  await authRepository.register({ ...input, suffix: ' Jr. ' })
  expect(updateProfile).toHaveBeenCalledWith(user, { displayName: 'Unit Test Jr.' })
  await authRepository.finishPendingRegistration(user as never)
  expect(profileRepository.createTrustedContact).not.toHaveBeenCalled()

  await authRepository.finishPendingRegistration({ ...user, emailVerified: true } as never)
  expect(profileRepository.createTrustedContact).toHaveBeenCalledWith(user.uid, expect.objectContaining({ suffix: 'Jr.' }))
  expect(localStorage.length).toBe(0)
})
