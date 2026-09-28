import { Timestamp } from 'firebase/firestore'
import { z } from 'zod'

export const userRoleSchema = z.enum(['DRIVER', 'TRUSTED_CONTACT', 'BOTH'])
export const accountStatusSchema = z.enum(['ACTIVE', 'DEACTIVATED'])
export const connectionStatusSchema = z.enum(['PENDING', 'APPROVED', 'DECLINED', 'REVOKED'])
export const stageSyncRecordTypeSchema = z.enum([
  'STAGE_3_TRANSITION',
  'STAGE_3_PERSISTENCE',
  'STAGE3_TRANSITION',
  'STAGE3_PERSISTENCE',
])

export type UserRole = z.infer<typeof userRoleSchema>
export type AccountStatus = z.infer<typeof accountStatusSchema>
export type ConnectionStatus = z.infer<typeof connectionStatusSchema>
export type StageSyncRecordType = 'STAGE_3_TRANSITION' | 'STAGE_3_PERSISTENCE'

const timestampSchema = z.custom<Timestamp>((value) => value instanceof Timestamp)

const optionalText = z.string().optional().default('')

export const userProfileSchema = z.object({
  uid: z.string().min(1),
  firstName: optionalText,
  middleName: z.string().nullable().optional(),
  lastName: optionalText,
  email: optionalText,
  phoneNumber: z.string().nullable().optional(),
  userRole: userRoleSchema.nullable().optional(),
  accountStatus: accountStatusSchema.default('ACTIVE'),
  registeredAt: timestampSchema.nullable().optional(),
  deactivatedAt: timestampSchema.nullable().optional(),
  connectionCode: optionalText,
})

export type UserProfile = z.infer<typeof userProfileSchema>

export const connectionCodeMappingSchema = z.object({
  ownerUserId: z.string().min(1),
  displayName: z.string(),
  createdAt: timestampSchema.nullable().optional(),
  active: z.boolean(),
})

export type ConnectionCodeMapping = z.infer<typeof connectionCodeMappingSchema>

export const trustedContactConnectionSchema = z.object({
  connectionId: z.string().min(1),
  driverUserId: z.string().min(1),
  trustedContactUserId: z.string().min(1),
  driverName: z.string(),
  driverEmail: z.string(),
  trustedContactName: z.string(),
  trustedContactEmail: z.string(),
  requestedByUserId: z.string().min(1),
  targetConnectionCode: z.string(),
  status: connectionStatusSchema,
  requestedAt: timestampSchema,
  approvedAt: timestampSchema.nullable(),
  declinedAt: timestampSchema.nullable(),
  revokedAt: timestampSchema.nullable(),
})

export type TrustedContactConnection = z.infer<typeof trustedContactConnectionSchema>

export const stageSyncRecordSchema = z.object({
  stageSyncRecordId: z.string().min(1),
  driverUserId: z.string().min(1),
  sessionId: z.string(),
  recordType: stageSyncRecordTypeSchema,
  periodStartedAt: timestampSchema,
  periodEndedAt: timestampSchema.nullable(),
  eventCount: z.number().int().min(1),
  signs: z.array(z.string()),
  warningStage: z.literal('STAGE_3'),
  warningStageAtDetection: z.literal(3),
  syncStatus: z.literal('SYNCED'),
  createdAtClient: timestampSchema,
  uploadedAt: timestampSchema.nullable(),
})

export type StageSyncRecord = Omit<z.infer<typeof stageSyncRecordSchema>, 'recordType'> & {
  recordType: StageSyncRecordType
}

export type RequestDirection = 'INCOMING' | 'OUTGOING'

export interface ConnectionRequestView {
  connection: TrustedContactConnection
  direction: RequestDirection
}

export interface DriverRecordState {
  status: 'loading' | 'ready' | 'error'
  records: StageSyncRecord[]
  error: PortalError | null
}

export type PortalErrorKind = 'permission' | 'offline' | 'not-found' | 'invalid-data' | 'backend' | 'conflict'

export class PortalError extends Error {
  constructor(
    public readonly kind: PortalErrorKind,
    message: string,
  ) {
    super(message)
    this.name = 'PortalError'
  }
}

export function normalizeRecordType(value: z.infer<typeof stageSyncRecordTypeSchema>): StageSyncRecordType {
  return value.includes('PERSISTENCE') ? 'STAGE_3_PERSISTENCE' : 'STAGE_3_TRANSITION'
}

export function parseUserProfile(data: unknown, documentId: string): UserProfile {
  const source = typeof data === 'object' && data !== null ? data : {}
  return userProfileSchema.parse({ ...source, uid: documentId })
}

export function parseConnection(data: unknown, documentId: string): TrustedContactConnection {
  const source = typeof data === 'object' && data !== null ? data : {}
  const candidate = { ...source } as Record<string, unknown>
  if (typeof candidate.connectionId !== 'string' || !candidate.connectionId) candidate.connectionId = documentId
  return trustedContactConnectionSchema.parse(candidate)
}

export function parseStageSyncRecord(data: unknown, documentId: string): StageSyncRecord {
  const source = typeof data === 'object' && data !== null ? data : {}
  const candidate = { ...source } as Record<string, unknown>
  if (typeof candidate.stageSyncRecordId !== 'string' || !candidate.stageSyncRecordId) candidate.stageSyncRecordId = documentId
  const parsed = stageSyncRecordSchema.parse(candidate)
  return { ...parsed, recordType: normalizeRecordType(parsed.recordType) }
}

export function fullName(profile: Pick<UserProfile, 'firstName' | 'middleName' | 'lastName'>): string {
  return [profile.firstName, profile.middleName, profile.lastName].filter(Boolean).join(' ').trim()
}

export function trustedRoleAllowed(role: UserRole | null | undefined): boolean {
  return role === 'TRUSTED_CONTACT' || role === 'BOTH'
}
