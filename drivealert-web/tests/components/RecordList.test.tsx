import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { RecordList } from '../../src/components/RecordList'
import { record } from '../testData'

it('renders Stage 3 records with human labels and deep links', () => {
  render(<MemoryRouter><RecordList driverId="driver-1" records={[record]} /></MemoryRouter>)
  expect(screen.getByText('Entered Stage 3')).toBeInTheDocument()
  expect(screen.getByText(/Yawning, Head Nodding/)).toBeInTheDocument()
  expect(screen.getByRole('link')).toHaveAttribute('href', '/drivers/driver-1/records/record-1')
})
