import { Timestamp } from 'firebase/firestore'
import { describe, expect, it } from 'vitest'
import { formatDateTime, formatRelativeTime } from '../../src/lib/date'

describe('date formatting', () => {
  it('uses a fallback for missing timestamps', () => expect(formatDateTime(null)).toBe('Not available'))
  it('formats relative minutes', () => expect(formatRelativeTime(Timestamp.fromMillis(60_000), 120_000)).toBe('1 minute ago'))
  it('formats relative days', () => expect(formatRelativeTime(Timestamp.fromMillis(0), 172_800_000)).toBe('2 days ago'))
})
