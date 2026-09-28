import {
  doc,
  onSnapshot,
  runTransaction,
  serverTimestamp,
  type Unsubscribe,
} from 'firebase/firestore'
import { firestore } from '../firebase/client'
import { generateConnectionCode } from '../lib/connectionCode'
import { fullName, parseUserProfile, PortalError, type UserProfile } from '../models/domain'
import type { RegistrationInput } from './authRepository'
import { toPortalError } from '../lib/errors'

const MAX_CODE_ATTEMPTS = 5

export const profileRepository = {
  observe(uid: string, onValue: (value: UserProfile | null) => void, onError: (error: PortalError) => void): Unsubscribe {
    return onSnapshot(
      doc(firestore, 'users', uid),
      (snapshot) => onValue(snapshot.exists() ? parseUserProfile(snapshot.data(), snapshot.id) : null),
      (error) => onError(toPortalError(error)),
    )
  },

  async createTrustedContact(uid: string, input: RegistrationInput): Promise<void> {
    for (let attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt += 1) {
      const connectionCode = generateConnectionCode()
      try {
        await runTransaction(firestore, async (transaction) => {
          const profileRef = doc(firestore, 'users', uid)
          const codeRef = doc(firestore, 'connectionCodes', connectionCode)
          const codeSnapshot = await transaction.get(codeRef)
          if (codeSnapshot.exists()) throw new PortalError('conflict', 'connection-code-collision')

          const profile: Record<string, unknown> = {
            uid,
            firstName: input.firstName.trim(),
            lastName: input.lastName.trim(),
            email: input.email.trim().toLowerCase(),
            userRole: 'TRUSTED_CONTACT',
            accountStatus: 'ACTIVE',
            registeredAt: serverTimestamp(),
            deactivatedAt: null,
            connectionCode,
          }
          if (input.middleName?.trim()) profile.middleName = input.middleName.trim()
          if (input.phoneNumber?.trim()) profile.phoneNumber = input.phoneNumber.trim()

          transaction.set(profileRef, profile)
          transaction.set(codeRef, {
            ownerUserId: uid,
            displayName: fullName({
              firstName: input.firstName.trim(),
              middleName: input.middleName?.trim() || null,
              lastName: input.lastName.trim(),
            }),
            createdAt: serverTimestamp(),
            active: true,
          })
        })
        return
      } catch (error) {
        if (error instanceof PortalError && error.message === 'connection-code-collision') continue
        throw toPortalError(error, 'Your Trusted Contact profile could not be created.')
      }
    }
    throw new PortalError('conflict', 'A unique connection code could not be created. Please try again.')
  },

  async update(uid: string, input: { firstName: string; middleName: string; lastName: string; phoneNumber: string }): Promise<void> {
    try {
      await runTransaction(firestore, async (transaction) => {
        const profileRef = doc(firestore, 'users', uid)
        const profileSnapshot = await transaction.get(profileRef)
        if (!profileSnapshot.exists()) throw new PortalError('not-found', 'Your DriveAlert profile could not be found.')

        const profile = parseUserProfile(profileSnapshot.data(), profileSnapshot.id)
        const firstName = input.firstName.trim()
        const middleName = input.middleName.trim()
        const lastName = input.lastName.trim()
        const phoneNumber = input.phoneNumber.trim()
        if (!firstName || !lastName) throw new PortalError('invalid-data', 'First name and last name are required.')

        const codeRef = profile.connectionCode ? doc(firestore, 'connectionCodes', profile.connectionCode) : null
        const codeSnapshot = codeRef ? await transaction.get(codeRef) : null

        transaction.update(profileRef, { firstName, middleName, lastName, phoneNumber })
        if (codeRef && codeSnapshot?.exists()) {
          transaction.update(codeRef, { displayName: fullName({ firstName, middleName, lastName }) })
        }
      })
    } catch (error) {
      throw toPortalError(error, 'Your profile could not be updated.')
    }
  },
}
