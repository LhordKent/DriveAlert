import { useState } from 'react'
import { useAuth } from '../../app/providers/authContext'
import { authRepository } from '../../repositories/authRepository'
import { AuthLayout } from './AuthLayout'
import { Button } from '../../components/Button'
import { InlineError } from '../../components/PageState'

export function VerifyEmailPage() {
  const { user, refreshVerification } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [email, setEmail] = useState('')
  async function run(action: () => Promise<void>, success = '') {
    if (busy) return
    setBusy(true); setError(''); setMessage('')
    try { await action(); setMessage(success) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not complete this request. Please try again.') }
    finally { setBusy(false) }
  }
  if (!user) return null
  return <AuthLayout title="Verify your email" description={`Verify ${user.email} to continue. Open the email link, then return here. Use Resend if you need a link, and check your spam folder.`} footer={<Button variant="quiet" disabled={busy} onClick={() => run(() => authRepository.logout())}>Sign out</Button>}>
    <div className="space-y-5">
      {error && <InlineError message={error} />}
      {message && <p role="status" className="text-sm text-secondary">{message}</p>}
      <Button className="w-full" busy={busy} onClick={() => run(refreshVerification)}>I've verified my email</Button>
      <Button className="w-full" variant="secondary" disabled={busy} onClick={() => run(() => authRepository.sendVerification(user), 'Verification email sent. Check your inbox and spam folder.')}>Resend verification email</Button>
      <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); void run(() => authRepository.sendVerification(user, email), `Check ${email} for a link to confirm the new address. Then return and check verification.`) }}>
        <label htmlFor="verification-email" className="block text-sm font-semibold">Correct email address</label>
        <input id="verification-email" type="email" autoComplete="email" required value={email} disabled={busy} onChange={(event) => setEmail(event.target.value)} className="w-full rounded-control border border-border bg-control px-4 py-3 text-primary" />
        <Button type="submit" variant="quiet" disabled={busy}>Send link to this address</Button>
      </form>
    </div>
  </AuthLayout>
}
