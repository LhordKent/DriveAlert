import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PortalContext } from '../../src/app/providers/portalContext'
import { RequestsPage } from '../../src/features/requests/RequestsPage'
import { connectionRepository } from '../../src/repositories/connectionRepository'
import { connection, portalValue } from '../testData'

vi.mock('../../src/repositories/connectionRepository', () => ({ connectionRepository: { accept: vi.fn(), decline: vi.fn() } }))

describe('request actions', () => {
  beforeEach(() => vi.clearAllMocks())
  it('accepts and declines incoming requests', async () => {
    const incoming = [{ ...connection, status: 'PENDING' as const }]
    render(<MemoryRouter><PortalContext.Provider value={portalValue({ incomingRequests: incoming })}><RequestsPage /></PortalContext.Provider></MemoryRouter>)
    await userEvent.click(screen.getByRole('button', { name: 'Accept' }))
    await waitFor(() => expect(connectionRepository.accept).toHaveBeenCalledWith(connection.connectionId))
    await userEvent.click(screen.getByRole('button', { name: 'Decline' }))
    await waitFor(() => expect(connectionRepository.decline).toHaveBeenCalledWith(connection.connectionId))
  })

  it('directs a Trusted Contact to their own code when there are no invitations', () => {
    render(<MemoryRouter><PortalContext.Provider value={portalValue()}><RequestsPage /></PortalContext.Provider></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'View my connection code' })).toHaveAttribute('href', '/account')
    expect(screen.queryByRole('link', { name: 'Invite a Driver' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Outgoing requests' })).not.toBeInTheDocument()
  })
})
