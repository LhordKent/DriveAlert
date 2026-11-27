import { z } from 'zod'

export const newPasswordRequirements =
  'Use at least 8 characters, including an uppercase letter, a lowercase letter, and a special character.'

const firebaseSpecialCharacters = "^$*.[]{}()?\"!@#%&/\\,><':;|_~`-"

export const newPasswordSchema = z.string().refine(
  (password) => password.length >= 8
    && /[A-Z]/.test(password)
    && /[a-z]/.test(password)
    && [...password].some((character) => firebaseSpecialCharacters.includes(character)),
  newPasswordRequirements,
)
