const PREFIX = 'DA'
const BODY_LENGTH = 26
const ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'
const CODE_PATTERN = new RegExp(`^${PREFIX}[${ALPHABET}]{${BODY_LENGTH}}$`)

export function normalizeConnectionCode(value: string): string {
  return value.toUpperCase().replace(/\s/g, '').replace(/-/g, '')
}

export function isValidConnectionCode(value: string): boolean {
  return CODE_PATTERN.test(normalizeConnectionCode(value))
}

export function formatConnectionCode(value: string): string {
  const normalized = normalizeConnectionCode(value)
  if (!isValidConnectionCode(normalized)) return value
  const body = normalized.slice(PREFIX.length)
  return `${PREFIX}-${body.slice(0, 5)}-${body.slice(5, 10)}-${body.slice(10, 15)}-${body.slice(15, 20)}-${body.slice(20)}`
}

export function generateConnectionCode(randomValues = crypto.getRandomValues(new Uint8Array(BODY_LENGTH))): string {
  return PREFIX + [...randomValues].map((value) => ALPHABET[value % ALPHABET.length]).join('')
}
