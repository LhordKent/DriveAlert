import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SignInPage } from '../../src/features/auth/SignInPage'
import { ForgotPasswordPage } from '../../src/features/auth/ForgotPasswordPage'
import { authRepository } from '../../src/repositories/authRepository'

vi.mock('../../src/repositories/authRepository', () => ({ authRepository: { signIn: vi.fn(), resetPassword: vi.fn(), register: vi.fn(), observe: vi.fn(), logout: vi.fn() } }))

describe('authentication forms', () => {
  beforeEach(() => vi.clearAllMocks())
  it('validates sign-in before calling Firebase', async () => { render(<MemoryRouter><SignInPage /></MemoryRouter>); await userEvent.click(screen.getByRole('button', { name: 'Sign in' })); expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument(); expect(authRepository.signIn).not.toHaveBeenCalled() })
  it('submits a valid email/password sign-in', async () => { vi.mocked(authRepository.signIn).mockResolvedValue({} as never); render(<MemoryRouter><SignInPage /></MemoryRouter>); await userEvent.type(screen.getByLabelText('Email'), 'mara@example.com'); await userEvent.type(screen.getByLabelText('Password'), 'Password123!'); await userEvent.click(screen.getByRole('button', { name: 'Sign in' })); expect(authRepository.signIn).toHaveBeenCalledWith('mara@example.com', 'Password123!') })
  it('sends a password reset and presents a truthful success state', async () => { vi.mocked(authRepository.resetPassword).mockResolvedValue(); render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>); await userEvent.type(screen.getByLabelText('Email'), 'mara@example.com'); await userEvent.click(screen.getByRole('button', { name: /send reset/i })); expect(await screen.findByText('Check your inbox')).toBeInTheDocument() })
})
