import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { AuthLayout } from './AuthLayout'
import { FormField } from './FormField'
import { forgotPasswordSchema } from './schemas'
import { authRepository } from '../../repositories/authRepository'
import { Button } from '../../components/Button'
import { InlineError } from '../../components/PageState'

type Values = z.infer<typeof forgotPasswordSchema>
export function ForgotPasswordPage() {
  const [serverError, setServerError] = useState('')
  const [sent, setSent] = useState(false)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({ resolver: zodResolver(forgotPasswordSchema), defaultValues: { email: '' } })
  const submit = handleSubmit(async ({ email }) => { setServerError(''); try { await authRepository.resetPassword(email); setSent(true) } catch { setServerError('DriveAlert could not send a reset email. Check the address and try again.') } })
  return <AuthLayout title="Reset your password" description="Firebase will email you a secure password reset link." footer={<Link className="font-semibold text-primary underline decoration-border underline-offset-4 hover:decoration-signal" to="/auth/sign-in">Back to sign in</Link>}>{sent ? <div role="status" className="surface p-5"><h2 className="font-semibold">Check your inbox</h2><p className="mt-2 text-sm leading-6 text-secondary">If the address belongs to a DriveAlert account, a reset link has been sent.</p></div> : <form onSubmit={submit} className="space-y-5" noValidate>{serverError && <InlineError message={serverError} />}<FormField id="email" type="email" label="Email" autoComplete="email" error={errors.email} {...register('email')} /><Button type="submit" busy={isSubmitting} className="w-full">Send reset link</Button></form>}</AuthLayout>
}
