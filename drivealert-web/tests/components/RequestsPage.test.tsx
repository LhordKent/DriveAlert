import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PortalContext } from '../../src/app/providers/portalContext'
import { RequestsPage } from '../../src/features/requests/RequestsPage'
import { connectionRepository } from '../../src/repositories/connectionRepository'
import { connection, portalValue } from '../testData'

vi.mock('../../src/repositories/connectionRepository', () => ({ connectionRepository: { accept: vi.fn(), decline: vi.fn(), cancel: vi.fn() } }))

describe('request actions', () => {
  beforeEach(() => vi.clearAllMocks())
  it('accepts and declines incoming requests', async () => {
    const incoming = [{ connection: { ...connection, status: 'PENDING' as const }, direction: 'INCOMING' as const }]
    render(<MemoryRouter><PortalContext.Provider value={portalValue({ incomingRequests: incoming, outgoingRequests: [] })}><RequestsPage /></PortalContext.Provider></MemoryRouter>)
    await userEvent.click(screen.getByRole('button', { name: 'Accept' }))
    await waitFor(() => expect(connectionRepository.accept).toHaveBeenCalledWith(connection.connectionId))
    await userEvent.click(screen.getByRole('button', { name: 'Decline' }))
    await waitFor(() => expect(connectionRepository.decline).toHaveBeenCalledWith(connection.connectionId))
  })

  it('cancels outgoing requests', async () => {
    const outgoing = [{ connection: { ...connection, status: 'PENDING' as const, requestedByUserId: 'trusted-1' }, direction: 'OUTGOING' as const }]
    render(<MemoryRouter><PortalContext.Provider value={portalValue({ incomingRequests: [], outgoingRequests: outgoing })}><RequestsPage /></PortalContext.Provider></MemoryRouter>)
    await userEvent.click(screen.getByRole('button', { name: 'Cancel request' }))
    await waitFor(() => expect(connectionRepository.cancel).toHaveBeenCalledWith(connection.connectionId))
  })
})
