import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DriverCard } from '../../src/components/DriverCard'
import { connection, record } from '../testData'

describe('DriverCard', () => {
  it('shows peer contact details and record data', () => { render(<MemoryRouter><DriverCard connection={connection} details={{ email: 'current@example.test', phoneNumber: '09123456789' }} recordState={{ status: 'ready', records: [record], error: null, lastViewedAt: null, unreadCount: 1 }} onDisconnect={vi.fn()} /></MemoryRouter>); expect(screen.getByText('Adrian Cruz')).toBeInTheDocument(); expect(screen.getByText('current@example.test')).toBeInTheDocument(); expect(screen.getByText('09123456789')).toBeInTheDocument(); expect(screen.queryByText('Approved')).not.toBeInTheDocument(); expect(screen.getByText(/1 records/)).toBeInTheDocument(); expect(screen.getByText('1 new')).toBeInTheDocument() })
  it('offers records and disconnect actions for drivers without a phone number', async () => { const disconnect = vi.fn(); render(<MemoryRouter><DriverCard connection={connection} recordState={{ status: 'ready', records: [], error: null, lastViewedAt: null, unreadCount: 0 }} onDisconnect={disconnect} /></MemoryRouter>); expect(screen.getByText('Phone number unavailable')).toBeInTheDocument(); expect(screen.getByRole('link', { name: /view records/i })).toHaveAttribute('href', '/drivers/driver-1'); await userEvent.click(screen.getByRole('button', { name: /disconnect/i })); expect(disconnect).toHaveBeenCalledOnce() })
})
