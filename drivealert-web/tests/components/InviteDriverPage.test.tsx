import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PortalContext } from '../../src/app/providers/portalContext'
import { InviteDriverPage } from '../../src/features/requests/InviteDriverPage'
import { connectionRepository } from '../../src/repositories/connectionRepository'
import { PortalError } from '../../src/models/domain'
import { portalValue, profile } from '../testData'

const navigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => ({ ...(await importOriginal<typeof import('react-router-dom')>()), useNavigate: () => navigate }))
vi.mock('../../src/repositories/connectionRepository', () => ({ connectionRepository: { resolveCode: vi.fn(), requestDriver: vi.fn() } }))

describe('invite Driver form', () => {
  beforeEach(() => vi.clearAllMocks())
  it('shows invalid-code feedback from the exact lookup', async () => {
    vi.mocked(connectionRepository.resolveCode).mockRejectedValue(new PortalError('not-found', 'Enter a complete DriveAlert connection code.'))
    render(<MemoryRouter><PortalContext.Provider value={portalValue()}><InviteDriverPage /></PortalContext.Provider></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('DriveAlert connection code'), 'bad')
    await userEvent.click(screen.getByRole('button', { name: 'Find Driver' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('complete DriveAlert connection code')
    expect(connectionRepository.resolveCode).toHaveBeenCalledWith('BAD')
  })

  it('resolves an exact code and sends the deterministic request', async () => {
    const target = { userId: 'driver-2', displayName: 'Long Driver Name', normalizedCode: 'DA23456789ABCDEFGHJKLMNPQRST' }
    vi.mocked(connectionRepository.resolveCode).mockResolvedValue(target)
    vi.mocked(connectionRepository.requestDriver).mockResolvedValue()
    render(<MemoryRouter><PortalContext.Provider value={portalValue()}><InviteDriverPage /></PortalContext.Provider></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('DriveAlert connection code'), target.normalizedCode)
    await userEvent.click(screen.getByRole('button', { name: 'Find Driver' }))
    expect(await screen.findByText('Long Driver Name')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Send connection request' }))
    await waitFor(() => expect(connectionRepository.requestDriver).toHaveBeenCalledWith(profile, target))
    expect(navigate).toHaveBeenCalledWith('/requests', { replace: true })
  })
})
