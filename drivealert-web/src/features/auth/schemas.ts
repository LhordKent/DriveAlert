import { z } from 'zod'
import { requiredPhoneNumberSchema } from '../../lib/phoneNumber'
import { newPasswordSchema } from '../../lib/password'
import { removeNameNumbers } from '../../models/domain'

const namePart = z.string().trim().refine((name) => name === removeNameNumbers(name), 'Names cannot contain numbers.')

export const signInSchema = z.object({ email: z.string().trim().email('Enter a valid email address.'), password: z.string().min(1, 'Password is required.') })
export const forgotPasswordSchema = z.object({ email: z.string().trim().email('Enter a valid email address.') })
export const registrationSchema = z.object({
  firstName: namePart.min(1, 'First name is required.'),
  middleName: namePart.optional(),
  lastName: namePart.min(1, 'Last name is required.'),
  suffix: z.string().trim().optional(),
  phoneNumber: requiredPhoneNumberSchema,
  email: z.string().trim().email('Enter a valid email address.'),
  password: newPasswordSchema,
  confirmPassword: z.string(),
}).refine((value) => value.password === value.confirmPassword, { message: 'Passwords do not match.', path: ['confirmPassword'] })

export type SignInValues = z.infer<typeof signInSchema>
export type RegistrationValues = z.infer<typeof registrationSchema>
