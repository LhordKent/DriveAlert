import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { RegisterPage } from '../../src/features/auth/RegisterPage'
import { authRepository } from '../../src/repositories/authRepository'
import { profileRepository } from '../../src/repositories/profileRepository'

vi.mock('../../src/repositories/authRepository', () => ({ authRepository: { register: vi.fn(), signIn: vi.fn(), resetPassword: vi.fn(), observe: vi.fn(), logout: vi.fn() } }))
vi.mock('../../src/repositories/profileRepository', () => ({ profileRepository: { createTrustedContact: vi.fn(), observe: vi.fn() } }))

it('creates Firebase identity then a Trusted Contact profile', async () => {
  vi.mocked(authRepository.register).mockResolvedValue({ uid: 'trusted-new' } as never)
  vi.mocked(profileRepository.createTrustedContact).mockResolvedValue()
  render(<MemoryRouter><RegisterPage /></MemoryRouter>)
  await userEvent.type(screen.getByLabelText('First name'), 'Mara')
  await userEvent.type(screen.getByLabelText('Last name'), 'Santos')
  await userEvent.type(screen.getByLabelText('Email'), 'mara@example.com')
  await userEvent.type(screen.getByLabelText('Password'), 'Password123!')
  await userEvent.type(screen.getByLabelText('Confirm password'), 'Password123!')
  await userEvent.click(screen.getByRole('button', { name: /create account/i }))
  expect(authRepository.register).toHaveBeenCalled()
  expect(profileRepository.createTrustedContact).toHaveBeenCalledWith('trusted-new', expect.objectContaining({ firstName: 'Mara', lastName: 'Santos' }))
})
