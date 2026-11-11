import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { User } from 'firebase/auth'

vi.mock('../../src/firebase/client', () => ({ auth: {}, firestore: {} }))
vi.mock('../../src/repositories/profileRepository', () => ({ profileRepository: { createTrustedContact: vi.fn() } }))

const { authRepository, fallbackRegistration } = await import('../../src/repositories/authRepository')
const { profileRepository } = await import('../../src/repositories/profileRepository')

const verifiedUser = (overrides: Partial<User> = {}) =>
  ({ uid: 'user-1', emailVerified: true, email: 'mara@gmail.com', displayName: 'Mara Santos', ...overrides }) as User

describe('profile fallback when sign-up details are not in this browser', () => {
  beforeEach(() => { vi.clearAllMocks(); localStorage.clear() })

  it('splits the Firebase display name into first and last name', () => {
    expect(fallbackRegistration({ displayName: 'Mara Santos', email: 'mara@gmail.com' }))
      .toMatchObject({ firstName: 'Mara', lastName: 'Santos', email: 'mara@gmail.com' })
    expect(fallbackRegistration({ displayName: 'Juan Dela Cruz', email: 'j@gmail.com' }))
      .toMatchObject({ firstName: 'Juan Dela', lastName: 'Cruz' })
    expect(fallbackRegistration({ displayName: '  Cher  ', email: 'c@gmail.com' }))
      .toMatchObject({ firstName: 'Cher', lastName: '' })
  })

  it('uses the email name when the account has no display name', () => {
    expect(fallbackRegistration({ displayName: null, email: 'mara.santos@gmail.com' }))
      .toMatchObject({ firstName: 'mara', lastName: 'santos' })
    expect(fallbackRegistration({ displayName: null, email: null }))
      .toMatchObject({ firstName: 'DriveAlert', lastName: 'user', email: '' })
  })

  it('creates a profile from the Firebase account when this browser has no sign-up record', async () => {
    await authRepository.finishPendingRegistration(verifiedUser())
    expect(profileRepository.createTrustedContact).toHaveBeenCalledWith('user-1',
      expect.objectContaining({ firstName: 'Mara', lastName: 'Santos', email: 'mara@gmail.com' }))
  })

  it('still prefers the full sign-up details saved in this browser, including phone, then clears them', async () => {
    localStorage.setItem('drivealert.pending-registration.user-1', JSON.stringify({
      firstName: 'Mara', middleName: 'L', lastName: 'Santos', phoneNumber: '+63 900 000 0000', email: 'mara@gmail.com',
    }))
    await authRepository.finishPendingRegistration(verifiedUser())
    expect(profileRepository.createTrustedContact).toHaveBeenCalledWith('user-1',
      expect.objectContaining({ middleName: 'L', phoneNumber: '+63 900 000 0000' }))
    expect(localStorage.getItem('drivealert.pending-registration.user-1')).toBeNull()
  })

  it('does nothing for an unverified account', async () => {
    await authRepository.finishPendingRegistration(verifiedUser({ emailVerified: false }))
    expect(profileRepository.createTrustedContact).not.toHaveBeenCalled()
  })
})
