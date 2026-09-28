import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PortalContext } from '../../src/app/providers/portalContext'
import { RecordDetailPage } from '../../src/features/records/RecordDetailPage'
import { portalValue } from '../testData'

function renderRoute(path: string, value = portalValue()) { return render(<MemoryRouter initialEntries={[path]}><PortalContext.Provider value={value}><Routes><Route path="/drivers/:driverId/records/:recordId" element={<RecordDetailPage />} /></Routes></PortalContext.Provider></MemoryRouter>) }
describe('record detail', () => {
  it('supports direct URLs with all available record fields', () => { renderRoute('/drivers/driver-1/records/record-1'); expect(screen.getByText('Entered Stage 3', { selector: 'h1' })).toBeInTheDocument(); expect(screen.getByText('3')).toBeInTheDocument(); expect(screen.getByText('session-1')).toBeInTheDocument(); expect(screen.getByText('Yawning, Head Nodding')).toBeInTheDocument() })
  it('shows record-not-found rather than a blank screen', () => { renderRoute('/drivers/driver-1/records/missing'); expect(screen.getByText('Record not found')).toBeInTheDocument() })
  it('shows revoked access rather than a blank screen', () => { renderRoute('/drivers/driver-1/records/record-1', portalValue({ approvedDrivers: [], connections: [], recordsByDriver: {} })); expect(screen.getByText('Record unavailable')).toBeInTheDocument() })
})
