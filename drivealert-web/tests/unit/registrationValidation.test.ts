import { beforeEach, expect, it, vi } from 'vitest'
import { registrationSchema } from '../../src/features/auth/schemas'
import { registrationPhoneSchema } from '../../src/lib/phoneNumber'

vi.mock('../../src/firebase/client', () => ({ auth: {}, firestore: {} }))
vi.mock('../../src/repositories/profileRepository', () => ({ profileRepository: { createTrustedContact: vi.fn() } }))
vi.mock('firebase/auth', async (original) => ({
  ...await original<typeof import('firebase/auth')>(),
  createUserWithEmailAndPassword: vi.fn(), updateProfile: vi.fn(), sendEmailVerification: vi.fn(),
}))

const { authRepository } = await import('../../src/repositories/authRepository')
const { createUserWithEmailAndPassword, updateProfile, sendEmailVerification } = await import('firebase/auth')
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
  expect(registrationPhoneSchema.parse(' 12345678901 ')).toBe('12345678901')
})

it('rejects invalid direct registration calls before creating a Firebase identity', async () => {
  for (const phoneNumber of ['', '1234567890', '123456789012', '1234567890a']) {
    await expect(authRepository.register({ ...input, phoneNumber })).rejects.toThrow()
  }
  expect(createUserWithEmailAndPassword).not.toHaveBeenCalled()
  expect(localStorage.length).toBe(0)
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
})
