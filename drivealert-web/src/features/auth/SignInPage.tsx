import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { AuthLayout } from './AuthLayout'
import { FormField } from './FormField'
import { signInSchema, type SignInValues } from './schemas'
import { authRepository } from '../../repositories/authRepository'
import { Button } from '../../components/Button'
import { InlineError } from '../../components/PageState'

export function SignInPage() {
  const [serverError, setServerError] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<SignInValues>({ resolver: zodResolver(signInSchema), defaultValues: { email: '', password: '' } })
  const submit = handleSubmit(async (values) => { setServerError(''); try { await authRepository.signIn(values.email, values.password) } catch { setServerError('Email or password is incorrect, or DriveAlert could not reach Firebase.') } })
  return <AuthLayout title="Welcome back" description="Sign in to review approved Drivers and shared alert records." footer={<>New to DriveAlert? <Link className="font-semibold text-primary underline decoration-border underline-offset-4 hover:decoration-signal" to="/auth/register">Create a Trusted Contact account</Link></>}><form onSubmit={submit} className="space-y-5" noValidate>{serverError && <InlineError message={serverError} />}<FormField id="email" type="email" autoComplete="email" label="Email" error={errors.email} {...register('email')} /><FormField id="password" type="password" autoComplete="current-password" label="Password" error={errors.password} {...register('password')} /><div className="flex justify-end"><Link className="text-sm font-semibold text-secondary hover:text-primary" to="/auth/forgot-password">Forgot password?</Link></div><Button type="submit" busy={isSubmitting} className="w-full">Sign in</Button></form></AuthLayout>
}
