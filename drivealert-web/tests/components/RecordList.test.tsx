import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { RecordList } from '../../src/components/RecordList'
import { formatDateTime } from '../../src/lib/date'
import { record } from '../testData'

it('shows each shared alert as a read-only row', () => {
  render(<RecordList records={[record]} />)
  expect(screen.getByText(formatDateTime(record.periodStartedAt))).toBeInTheDocument()
  expect(screen.getByRole('list', { name: 'Shared alerts' })).toBeInTheDocument()
  expect(screen.queryByText(/stage\s*3/i)).not.toBeInTheDocument()
  expect(screen.getByText(/Yawning, Head Nodding/)).toBeInTheDocument()
  expect(screen.queryByText(/received|counted in this period|delayed sync/i)).not.toBeInTheDocument()
  expect(screen.queryByRole('link')).not.toBeInTheDocument()
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})
