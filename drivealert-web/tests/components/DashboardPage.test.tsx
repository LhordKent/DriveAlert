import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PortalContext } from '../../src/app/providers/portalContext'
import { DashboardPage } from '../../src/features/dashboard/DashboardPage'
import { portalValue } from '../testData'

function renderPage(value = portalValue()) {
  return render(<MemoryRouter><PortalContext.Provider value={value}><DashboardPage /></PortalContext.Provider></MemoryRouter>)
}

describe('dashboard states', () => {
  it('summarizes approved Drivers, pending requests, and shared records', () => {
    renderPage()
    expect(screen.getAllByText('1', { selector: 'span' })).toHaveLength(2)
    expect(screen.getByText('Connected Drivers')).toBeInTheDocument()
    expect(screen.getByText('Entered Stage 3')).toBeInTheDocument()
    expect(screen.getByText(/historical activity, not live monitoring/i)).toBeInTheDocument()
  })

  it('shows an instructive empty activity state', () => {
    renderPage(portalValue({ approvedDrivers: [], connections: [], recordsByDriver: {} }))
    expect(screen.getByText('No shared records yet')).toBeInTheDocument()
  })
})
