import { z } from 'zod'

export const requiredPhoneNumberSchema = z.string({ error: 'Phone number is required.' }).trim()
  .min(1, 'Phone number is required.')
  .regex(/^[0-9]{11}$/, 'Enter exactly 11 digits.')
