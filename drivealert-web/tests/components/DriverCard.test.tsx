import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DriverCard } from '../../src/components/DriverCard'
import { connection, record } from '../testData'

describe('DriverCard', () => {
  it('shows real relationship and record data', () => { render(<MemoryRouter><DriverCard connection={connection} recordState={{ status: 'ready', records: [record], error: null }} onDisconnect={vi.fn()} /></MemoryRouter>); expect(screen.getByText('Adrian Cruz')).toBeInTheDocument(); expect(screen.getByText('Approved')).toBeInTheDocument(); expect(screen.getByText(/1 records/)).toBeInTheDocument() })
  it('offers records and disconnect actions', async () => { const disconnect = vi.fn(); render(<MemoryRouter><DriverCard connection={connection} recordState={{ status: 'ready', records: [], error: null }} onDisconnect={disconnect} /></MemoryRouter>); expect(screen.getByRole('link', { name: /view records/i })).toHaveAttribute('href', '/drivers/driver-1'); await userEvent.click(screen.getByRole('button', { name: /disconnect/i })); expect(disconnect).toHaveBeenCalledOnce() })
})
