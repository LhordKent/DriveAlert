import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../../src/app/providers/authContext'
import { PortalContext } from '../../src/app/providers/portalContext'
import { ProtectedRoute } from '../../src/app/router/RouteGuards'
import { portalValue, profile } from '../testData'

vi.mock('../../src/repositories/authRepository', () => ({ authRepository: { logout: vi.fn() } }))

function renderGuard(authenticated: boolean, overrides = {}) {
  return render(<MemoryRouter initialEntries={['/dashboard']}><AuthContext.Provider value={{ user: authenticated ? ({ uid: profile.uid } as never) : null, loading: false }}><PortalContext.Provider value={portalValue(overrides)}><Routes><Route element={<ProtectedRoute />}><Route path="/dashboard" element={<h1>Private dashboard</h1>} /></Route><Route path="/auth/sign-in" element={<h1>Sign in route</h1>} /></Routes></PortalContext.Provider></AuthContext.Provider></MemoryRouter>)
}

describe('protected route gates', () => {
  it('redirects signed-out users', () => { renderGuard(false, { profile: null, profileStatus: 'idle' }); expect(screen.getByText('Sign in route')).toBeInTheDocument() })
  it('allows Trusted Contact accounts', () => { renderGuard(true); expect(screen.getByText('Private dashboard')).toBeInTheDocument() })
  it('blocks Driver-only accounts without changing their role', () => { renderGuard(true, { profile: { ...profile, userRole: 'DRIVER' } }); expect(screen.getByText('Trusted Contact access required')).toBeInTheDocument() })
  it('blocks deactivated accounts', () => { renderGuard(true, { profile: { ...profile, accountStatus: 'DEACTIVATED' } }); expect(screen.getByText('Account deactivated')).toBeInTheDocument() })
  it('shows an explicit missing-profile state', () => { renderGuard(true, { profile: null, profileStatus: 'ready' }); expect(screen.getByText('Profile not found')).toBeInTheDocument() })
})
