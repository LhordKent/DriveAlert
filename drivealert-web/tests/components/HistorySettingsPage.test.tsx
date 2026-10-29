import { MemoryRouter } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PortalContext } from '../../src/app/providers/portalContext'
import { HistorySettingsPage } from '../../src/features/settings/HistorySettingsPage'
import { HistoryDeleteButton } from '../../src/components/HistoryDeleteButton'
import { portalValue } from '../testData'

describe('history settings', () => {
  it('explains manual mode and previews shorter retention without saving until confirmed', async () => {
    const value = portalValue()
    render(<MemoryRouter><PortalContext.Provider value={value}><HistorySettingsPage /></PortalContext.Provider></MemoryRouter>)
    expect(screen.getByText(/History stays in your view until you delete it/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    await userEvent.click(screen.getByLabelText('1 day'))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    const dialog = screen.getByRole('alertdialog')
    expect(within(dialog).getByText(/This removes 1 record/)).toBeInTheDocument()
    expect(value.history.save).not.toHaveBeenCalled()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save and apply' }))
    expect(value.history.save).toHaveBeenCalledWith(1)
  })
  it('validates custom days and cancels without changing stored settings', async () => {
    const value = portalValue()
    render(<MemoryRouter><PortalContext.Provider value={value}><HistorySettingsPage /></PortalContext.Provider></MemoryRouter>)
    await userEvent.click(screen.getByLabelText('Custom'))
    const input = screen.getByLabelText('Number of days')
    await userEvent.type(input, '3651')
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    await userEvent.clear(input); await userEvent.type(input, '14')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(value.history.save).not.toHaveBeenCalled()
  })
  it('shows a retry action when browser history cannot be loaded', async () => {
    const value = portalValue()
    value.history = { ...value.history, status: 'error', error: 'Browser storage unavailable.' }
    render(<MemoryRouter><PortalContext.Provider value={value}><HistorySettingsPage /></PortalContext.Provider></MemoryRouter>)
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Retry history settings' }))
    expect(value.history.refresh).toHaveBeenCalledOnce()
  })
  it('discards an unconfirmed retention change when the signed-in account changes', async () => {
    const value = portalValue()
    const renderPage = (context: typeof value) => <MemoryRouter><PortalContext.Provider value={context}><HistorySettingsPage /></PortalContext.Provider></MemoryRouter>
    const { rerender } = render(renderPage(value))
    await userEvent.click(screen.getByLabelText('1 day'))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
    const next = portalValue()
    next.history.snapshot = { policy: { userId: 'another-account', days: null, expiredBefore: Number.MIN_SAFE_INTEGER }, dismissals: [] }
    rerender(renderPage(next))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(next.history.save).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Keep until I delete')).toBeChecked()
  })
  it('confirms a personal record removal and keeps the dialog open if saving fails', async () => {
    const value = portalValue()
    value.history.dismiss = vi.fn().mockRejectedValue(new Error('quota'))
    render(<PortalContext.Provider value={value}><HistoryDeleteButton driverId="driver-1" recordIds={['record-1']} label="Delete record" /></PortalContext.Provider>)
    await userEvent.click(screen.getByRole('button', { name: 'Delete record' }))
    expect(value.history.dismiss).not.toHaveBeenCalled()
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete record' }))
    expect(value.history.dismiss).toHaveBeenCalledWith('driver-1', ['record-1'])
    expect(await screen.findByText(/History could not be removed/)).toBeInTheDocument()
  })
})
