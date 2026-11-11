import {
  createUserWithEmailAndPassword,
  sendEmailVerification,
  verifyBeforeUpdateEmail,
  reload,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
  updateProfile,
  type User,
} from 'firebase/auth'
import { auth } from '../firebase/client'
import { profileRepository } from './profileRepository'

export interface RegistrationInput {
  firstName: string
  middleName?: string
  lastName: string
  phoneNumber?: string
  email: string
  password: string
}

/** Profile details recoverable from the Firebase account when the browser's sign-up record is missing. */
export function fallbackRegistration(user: Pick<User, 'displayName' | 'email'>): RegistrationInput {
  const email = user.email ?? ''
  const fromEmail = email.split('@')[0].replace(/[._-]+/g, ' ').trim()
  const words = (user.displayName?.trim() || fromEmail || 'DriveAlert user').split(/\s+/)
  return {
    firstName: words.length > 1 ? words.slice(0, -1).join(' ') : words[0],
    lastName: words.length > 1 ? words[words.length - 1] : '',
    email,
    password: '',
  }
}

let lastEmailSentAt = 0
export const authRepository = {
  async finishPendingRegistration(user: User) {
    if (!user.emailVerified) return
    const key = 'drivealert.pending-registration.' + user.uid
    const pending = localStorage.getItem(key)
    if (pending) {
      const input = JSON.parse(pending) as RegistrationInput
      await profileRepository.createTrustedContact(user.uid, { ...input, email: user.email ?? input.email })
      localStorage.removeItem(key)
      return
    }
    // Sign-up details only live in the browser used to register. When the first verified sign-in happens
    // elsewhere (another browser, device or cleared storage), build the profile from the Firebase account.
    // createTrustedContact never overwrites an existing profile.
    await profileRepository.createTrustedContact(user.uid, fallbackRegistration(user))
  },
  async checkVerification(user: User) {
    await reload(user)
    if (!user.emailVerified) throw new Error('Your email is not verified yet. Open the link in your inbox, then try again.')
    await user.getIdToken(true)
    await this.finishPendingRegistration(user)
  },
  async sendVerification(user: User, newEmail?: string) {
    if (Date.now() - lastEmailSentAt < 60_000) throw new Error('Please wait a minute before requesting another email.')
    if (newEmail) await verifyBeforeUpdateEmail(user, newEmail.trim())
    else await sendEmailVerification(user)
    lastEmailSentAt = Date.now()
  },
  observe(callback: (user: User | null) => void) {
    return onAuthStateChanged(auth, callback)
  },
  async signIn(email: string, password: string) {
    return signInWithEmailAndPassword(auth, email.trim().toLowerCase(), password)
  },
  async register(input: RegistrationInput) {
    const credential = await createUserWithEmailAndPassword(auth, input.email.trim().toLowerCase(), input.password)
    // Persist profile details only, never passwords.
    const { firstName, middleName, lastName, phoneNumber, email } = input
    localStorage.setItem('drivealert.pending-registration.' + credential.user.uid,
      JSON.stringify({ firstName, middleName, lastName, phoneNumber, email }))
    const name = [input.firstName, input.middleName, input.lastName].filter(Boolean).join(' ')
    await updateProfile(credential.user, { displayName: name })
    await sendEmailVerification(credential.user)
    lastEmailSentAt = Date.now()
    return credential.user
  },
  async resetPassword(email: string) {
    await sendPasswordResetEmail(auth, email.trim().toLowerCase())
  },
  async logout() {
    await signOut(auth)
  },
}
