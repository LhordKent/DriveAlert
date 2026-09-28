import { FirebaseError } from 'firebase/app'
import { PortalError } from '../models/domain'

export function toPortalError(error: unknown, fallback = 'DriveAlert could not complete that request.'): PortalError {
  if (error instanceof PortalError) return error
  if (error instanceof FirebaseError) {
    if (error.code.includes('permission-denied')) return new PortalError('permission', 'You no longer have permission to view or change this data.')
    if (error.code.includes('unavailable') || error.code.includes('network')) return new PortalError('offline', 'DriveAlert is offline. Check your connection and try again.')
    if (error.code.includes('not-found')) return new PortalError('not-found', 'The requested DriveAlert record no longer exists.')
  }
  if (error instanceof z.ZodError) return new PortalError('invalid-data', 'DriveAlert received a record it could not safely read.')
  return new PortalError('backend', error instanceof Error ? error.message : fallback)
}

import { z } from 'zod'
