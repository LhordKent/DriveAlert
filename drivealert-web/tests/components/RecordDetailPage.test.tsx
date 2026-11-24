import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PortalContext } from '../../src/app/providers/portalContext'
import { RecordDetailPage } from '../../src/features/records/RecordDetailPage'
import { formatDateTime } from '../../src/lib/date'
import { portalValue, record } from '../testData'

function renderRoute(path: string, value = portalValue()) { return render(<MemoryRouter initialEntries={[path]}><PortalContext.Provider value={value}><Routes><Route path="/drivers/:driverId/records/:recordId" element={<RecordDetailPage />} /></Routes></PortalContext.Provider></MemoryRouter>) }
describe('record detail', () => {
  it('shows only the driver, alert time and signs when opening a record directly', () => {
    renderRoute('/drivers/driver-1/records/record-1')
    expect(screen.getByRole('heading', { name: 'Shared alert' })).toBeInTheDocument()
    expect(screen.getAllByRole('term').map((term) => term.textContent)).toEqual(['Driver', 'Alert time', 'Detected signs'])
    expect(screen.getAllByRole('definition').map((value) => value.textContent)).toEqual([
      'Adrian Cruz', formatDateTime(record.periodStartedAt), 'Yawning, Head Nodding',
    ])
    expect(screen.queryByText('session-1')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete record' })).toBeInTheDocument()
  })
  it('shows record-not-found rather than a blank screen', () => { renderRoute('/drivers/driver-1/records/missing'); expect(screen.getByText('Record not found')).toBeInTheDocument() })
  it('shows revoked access rather than a blank screen', () => { renderRoute('/drivers/driver-1/records/record-1', portalValue({ approvedDrivers: [], connections: [], recordsByDriver: {} })); expect(screen.getByText('Record unavailable')).toBeInTheDocument() })
})
