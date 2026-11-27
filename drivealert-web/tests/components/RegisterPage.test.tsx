import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { RegisterPage } from '../../src/features/auth/RegisterPage'
import { authRepository } from '../../src/repositories/authRepository'
import { profileRepository } from '../../src/repositories/profileRepository'
import { newPasswordRequirements } from '../../src/lib/password'

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
  const password = screen.getByLabelText('Password')
  const confirmation = screen.getByLabelText('Confirm password')
  expect(password).toHaveAccessibleDescription(newPasswordRequirements)
  await userEvent.type(password, 'alllower!')
  await userEvent.type(confirmation, 'alllower!')
  await userEvent.click(screen.getByRole('button', { name: /create account/i }))
  expect(password).toHaveAttribute('aria-invalid', 'true')
  expect(authRepository.register).not.toHaveBeenCalled()

  await userEvent.clear(password)
  await userEvent.clear(confirmation)
  await userEvent.type(password, 'Safe-drive')
  await userEvent.type(confirmation, 'Safe-drive')
  await userEvent.click(screen.getByRole('button', { name: /create account/i }))
  expect(authRepository.register).toHaveBeenCalledWith(expect.objectContaining({ phoneNumber: '12345678901', suffix: '' }))
  expect(profileRepository.createTrustedContact).not.toHaveBeenCalled()
})

it('removes numbers when typing or pasting names while keeping them in the suffix', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><RegisterPage /></MemoryRouter>)
  const firstName = screen.getByLabelText('First name')
  const middleName = screen.getByLabelText('Middle name (optional)')
  const lastName = screen.getByLabelText('Last name')
  const suffix = screen.getByLabelText('Suffix (optional)')

  await user.type(firstName, 'Mára1')
  await user.click(middleName)
  await user.paste('Anne-Marie٢')
  await user.type(lastName, "O'Neil3")
  await user.type(suffix, '3rd')

  expect(firstName).toHaveValue('Mára')
  expect(middleName).toHaveValue('Anne-Marie')
  expect(lastName).toHaveValue("O'Neil")
  expect(suffix).toHaveValue('3rd')
})
