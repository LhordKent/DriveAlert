import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { AppShell } from '../../src/app/layout/AppShell'
import { PortalContext } from '../../src/app/providers/portalContext'
import { portalValue } from '../testData'

it('explains hidden history and links to retry settings when browser storage fails', () => {
  const value = portalValue()
  value.history = { ...value.history, status: 'error', error: 'Browser storage unavailable.' }
  render(<MemoryRouter><PortalContext.Provider value={value}><AppShell /></PortalContext.Provider></MemoryRouter>)
  expect(screen.getByRole('alert')).toHaveTextContent('Your history is hidden')
  expect(screen.getByRole('link', { name: 'Open History & Storage to retry' })).toHaveAttribute('href', '/settings/history')
})
