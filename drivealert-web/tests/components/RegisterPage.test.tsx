import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { RegisterPage } from '../../src/features/auth/RegisterPage'
import { authRepository } from '../../src/repositories/authRepository'
import { profileRepository } from '../../src/repositories/profileRepository'

vi.mock('../../src/repositories/authRepository', () => ({ authRepository: { register: vi.fn(), signIn: vi.fn(), resetPassword: vi.fn(), observe: vi.fn(), logout: vi.fn() } }))
vi.mock('../../src/repositories/profileRepository', () => ({ profileRepository: { createTrustedContact: vi.fn(), observe: vi.fn() } }))

beforeEach(() => vi.clearAllMocks())

it('requires a phone number and rejects invalid lengths before account creation', async () => {
  render(<MemoryRouter><RegisterPage /></MemoryRouter>)
  await userEvent.type(screen.getByLabelText('First name'), 'Mara')
  await userEvent.type(screen.getByLabelText('Last name'), 'Santos')
  await userEvent.type(screen.getByLabelText('Email'), 'mara@example.com')
  await userEvent.type(screen.getByLabelText('Password'), 'Password123!')
  await userEvent.type(screen.getByLabelText('Confirm password'), 'Password123!')
  const submit = screen.getByRole('button', { name: /create account/i })
  await userEvent.click(submit)
  expect(await screen.findByText('Phone number is required.')).toBeInTheDocument()
  expect(authRepository.register).not.toHaveBeenCalled()
  const phone = screen.getByRole('textbox', { name: /^Phone number/ })
  for (const value of ['1234567890', '123456789012', '1234567890a']) {
    await userEvent.clear(phone)
    await userEvent.type(phone, value)
    await userEvent.click(submit)
    expect(await screen.findByText('Enter exactly 11 digits.')).toBeInTheDocument()
    expect(authRepository.register).not.toHaveBeenCalled()
  }
})

it('creates the identity and defers the profile until verification', async () => {
  vi.mocked(authRepository.register).mockResolvedValue({ uid: 'trusted-new' } as never)
  vi.mocked(profileRepository.createTrustedContact).mockResolvedValue()
  render(<MemoryRouter><RegisterPage /></MemoryRouter>)
  await userEvent.type(screen.getByLabelText('First name'), 'Mara')
  await userEvent.type(screen.getByLabelText('Last name'), 'Santos')
  await userEvent.type(screen.getByLabelText('Phone number'), '12345678901')
  await userEvent.type(screen.getByLabelText('Email'), 'mara@example.com')
  await userEvent.type(screen.getByLabelText('Password'), 'Password123!')
  await userEvent.type(screen.getByLabelText('Confirm password'), 'Password123!')
  await userEvent.click(screen.getByRole('button', { name: /create account/i }))
  expect(authRepository.register).toHaveBeenCalledWith(expect.objectContaining({ phoneNumber: '12345678901' }))
  expect(profileRepository.createTrustedContact).not.toHaveBeenCalled()
})
