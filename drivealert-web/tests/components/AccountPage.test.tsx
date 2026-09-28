import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { AuthContext } from '../../src/app/providers/authContext'
import { PortalContext } from '../../src/app/providers/portalContext'
import { AccountPage } from '../../src/features/account/AccountPage'
import { portalValue } from '../testData'

it('shows editable profile data and copies the connection code', async () => {
  render(<MemoryRouter><AuthContext.Provider value={{ user: { email: 'mara@example.com' } as never, loading: false }}><PortalContext.Provider value={portalValue()}><AccountPage /></PortalContext.Provider></AuthContext.Provider></MemoryRouter>)
  expect(screen.getByText('Mara Santos')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /edit profile/i })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /copy code/i }))
  expect(navigator.clipboard.writeText).toHaveBeenCalledWith('DA-23456-789AB-CDEFG-HJKLM-NPQRST')
})
