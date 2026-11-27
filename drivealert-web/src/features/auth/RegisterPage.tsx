import { useState, type ChangeEvent } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { AuthLayout } from './AuthLayout'
import { FormField } from './FormField'
import { registrationSchema, type RegistrationValues } from './schemas'
import { authRepository } from '../../repositories/authRepository'
import { removeNameNumbers } from '../../models/domain'
import { newPasswordRequirements } from '../../lib/password'
import { Button } from '../../components/Button'
import { InlineError } from '../../components/PageState'

export function RegisterPage() {
  const [serverError, setServerError] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<RegistrationValues>({
    resolver: zodResolver(registrationSchema),
    defaultValues: { firstName: '', middleName: '', lastName: '', suffix: '', phoneNumber: '', email: '', password: '', confirmPassword: '' },
  })
  const registerName = (name: 'firstName' | 'middleName' | 'lastName') => {
    const field = register(name)
    return {
      ...field,
      onChange(event: ChangeEvent<HTMLInputElement>) {
        const input = event.target
        const value = removeNameNumbers(input.value)
        if (value !== input.value) {
          const cursor = removeNameNumbers(input.value.slice(0, input.selectionStart ?? input.value.length)).length
          input.value = value
          input.setSelectionRange(cursor, cursor)
        }
        void field.onChange(event)
      },
    }
  }
  const submit = handleSubmit(async (values) => {
    setServerError('')
    try {
      await authRepository.register(values)
    } catch (error) {
      setServerError(error instanceof Error ? error.message : 'Your account could not be created. Please try again.')
    }
  })
  return (
    <AuthLayout
      title="Create your Trusted Contact account"
      description="Use the same identity across DriveAlert mobile and web. Your role will be created as Trusted Contact."
      footer={<>Already have an account? <Link className="font-semibold text-primary underline decoration-border underline-offset-4 hover:decoration-signal" to="/auth/sign-in">Sign in</Link></>}
    >
      <form onSubmit={submit} className="space-y-5" noValidate>
        {serverError && <InlineError message={serverError} />}
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField id="firstName" label="First name" autoComplete="given-name" error={errors.firstName} {...registerName('firstName')} />
          <FormField id="middleName" label="Middle name (optional)" autoComplete="additional-name" error={errors.middleName} {...registerName('middleName')} />
          <FormField id="lastName" label="Last name" autoComplete="family-name" error={errors.lastName} {...registerName('lastName')} />
          <FormField id="suffix" label="Suffix (optional)" autoComplete="honorific-suffix" error={errors.suffix} {...register('suffix')} />
        </div>
        <FormField id="phoneNumber" label="Phone number" type="tel" inputMode="numeric" autoComplete="tel" required error={errors.phoneNumber} {...register('phoneNumber')} />
        <FormField id="email" type="email" label="Email" autoComplete="email" error={errors.email} {...register('email')} />
        <FormField id="password" type="password" label="Password" autoComplete="new-password" hint={newPasswordRequirements} error={errors.password} {...register('password')} />
        <FormField id="confirmPassword" type="password" label="Confirm password" autoComplete="new-password" error={errors.confirmPassword} {...register('confirmPassword')} />
        <Button type="submit" busy={isSubmitting} className="w-full">Create account</Button>
      </form>
    </AuthLayout>
  )
}
