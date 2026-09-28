import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PortalContext } from '../../src/app/providers/portalContext'
import { DriversPage } from '../../src/features/drivers/DriversPage'
import { PortalError } from '../../src/models/domain'
import { portalValue } from '../testData'

function renderPage(value = portalValue()) { return render(<MemoryRouter><PortalContext.Provider value={value}><DriversPage /></PortalContext.Provider></MemoryRouter>) }
describe('DriversPage states', () => {
  it('shows loading skeletons', () => { renderPage(portalValue({ connectionsStatus: 'loading', approvedDrivers: [] })); expect(screen.getByRole('status')).toBeInTheDocument() })
  it('shows an instructive empty state', () => { renderPage(portalValue({ approvedDrivers: [], connections: [], recordsByDriver: {} })); expect(screen.getByText('No connected Drivers')).toBeInTheDocument() })
  it('shows backend errors', () => { renderPage(portalValue({ connectionsStatus: 'error', connectionsError: new PortalError('offline', 'Offline'), approvedDrivers: [] })); expect(screen.getByRole('alert')).toHaveTextContent('Offline') })
  it('shows production Driver data', () => { renderPage(); expect(screen.getByText('Adrian Cruz')).toBeInTheDocument() })
})
