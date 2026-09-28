import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
  updateProfile,
  type User,
} from 'firebase/auth'
import { auth } from '../firebase/client'

export interface RegistrationInput {
  firstName: string
  middleName?: string
  lastName: string
  phoneNumber?: string
  email: string
  password: string
}

export const authRepository = {
  observe(callback: (user: User | null) => void) {
    return onAuthStateChanged(auth, callback)
  },
  async signIn(email: string, password: string) {
    return signInWithEmailAndPassword(auth, email.trim().toLowerCase(), password)
  },
  async register(input: RegistrationInput) {
    const credential = await createUserWithEmailAndPassword(auth, input.email.trim().toLowerCase(), input.password)
    const name = [input.firstName, input.middleName, input.lastName].filter(Boolean).join(' ')
    await updateProfile(credential.user, { displayName: name })
    return credential.user
  },
  async resetPassword(email: string) {
    await sendPasswordResetEmail(auth, email.trim().toLowerCase())
  },
  async logout() {
    await signOut(auth)
  },
}
