import { describe, expect, it } from 'vitest'
import { formatConnectionCode, generateConnectionCode, isValidConnectionCode, normalizeConnectionCode } from '../../src/lib/connectionCode'

describe('connection codes', () => {
  const code = 'DA23456789ABCDEFGHJKLMNPQRST'
  it('normalizes case, spaces, and hyphens', () => expect(normalizeConnectionCode(' da-23456 789ab-cdefg-hjklm-npqrst ')).toBe(code))
  it('validates only the Android contract format', () => { expect(isValidConnectionCode(code)).toBe(true); expect(isValidConnectionCode('DA-TOO-SHORT')).toBe(false); expect(isValidConnectionCode(code.replace('2', '0'))).toBe(false) })
  it('formats a valid code into readable groups', () => expect(formatConnectionCode(code)).toBe('DA-23456-789AB-CDEFG-HJKLM-NPQRST'))
  it('leaves invalid values unchanged', () => expect(formatConnectionCode('bad')).toBe('bad'))
  it('generates a deterministic valid code from supplied bytes', () => expect(generateConnectionCode(new Uint8Array(26))).toBe(`DA${'2'.repeat(26)}`))
})
