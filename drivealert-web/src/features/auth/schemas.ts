import { z } from 'zod'
import { registrationPhoneSchema } from '../../lib/phoneNumber'

export const signInSchema = z.object({ email: z.string().trim().email('Enter a valid email address.'), password: z.string().min(1, 'Password is required.') })
export const forgotPasswordSchema = z.object({ email: z.string().trim().email('Enter a valid email address.') })
export const registrationSchema = z.object({
  firstName: z.string().trim().min(1, 'First name is required.'),
  middleName: z.string().trim().optional(),
  lastName: z.string().trim().min(1, 'Last name is required.'),
  phoneNumber: registrationPhoneSchema,
  email: z.string().trim().email('Enter a valid email address.'),
  password: z.string().min(8, 'Use at least 8 characters.'),
  confirmPassword: z.string(),
}).refine((value) => value.password === value.confirmPassword, { message: 'Passwords do not match.', path: ['confirmPassword'] })

export type SignInValues = z.infer<typeof signInSchema>
export type RegistrationValues = z.infer<typeof registrationSchema>
