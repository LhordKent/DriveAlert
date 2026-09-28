import { useState, type FormEvent } from 'react'
import { Check, Copy, Pencil } from 'lucide-react'
import { useAuth } from '../../app/providers/authContext'
import { usePortal } from '../../app/providers/portalContext'
import { Button } from '../../components/Button'
import { PageHeader } from '../../components/PageHeader'
import { StatusBadge } from '../../components/StatusBadge'
import { FormField } from '../auth/FormField'
import { formatConnectionCode } from '../../lib/connectionCode'
import { fullName } from '../../models/domain'
import { profileRepository } from '../../repositories/profileRepository'

export function AccountPage() {
  const { user } = useAuth()
  const { profile } = usePortal()
  const [copied, setCopied] = useState(false)
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [form, setForm] = useState({ firstName: '', middleName: '', lastName: '', phoneNumber: '' })
  if (!profile) return null

  const startEditing = () => {
    setForm({
      firstName: profile.firstName,
      middleName: profile.middleName || '',
      lastName: profile.lastName,
      phoneNumber: profile.phoneNumber || '',
    })
    setMessage('')
    setEditing(true)
  }

  const saveProfile = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!user) return
    setSaving(true)
    setMessage('')
    try {
      await profileRepository.update(user.uid, form)
      setEditing(false)
      setMessage('Profile updated.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Your profile could not be updated.')
    } finally {
      setSaving(false)
    }
  }

  const fields = [
    ['Full name', fullName(profile) || user?.displayName || 'Not available'],
    ['Email', user?.email || profile.email || 'Not available'],
    ['Phone number', profile.phoneNumber || 'Not provided'],
    ['User role', profile.userRole?.replace('_', ' ') || 'Not assigned'],
    ['Account status', profile.accountStatus],
  ]
  const formattedCode = formatConnectionCode(profile.connectionCode)

  return <>
    <PageHeader title="Account" description="Manage your shared DriveAlert identity and connection code." />
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_25rem]">
      <section className="surface overflow-hidden">
        <div className="flex min-h-16 items-center justify-between gap-4 border-b border-border-soft px-5 py-3"><h2 className="font-semibold">Profile</h2>{!editing && <Button type="button" variant="secondary" onClick={startEditing}><Pencil className="size-4" />Edit profile</Button>}</div>
        {editing ? <form onSubmit={saveProfile}>
          <div className="grid gap-5 p-5 sm:grid-cols-2">
            <FormField id="account-first-name" label="First name" required maxLength={60} value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} />
            <FormField id="account-middle-name" label="Middle name (optional)" maxLength={60} value={form.middleName} onChange={(event) => setForm({ ...form, middleName: event.target.value })} />
            <FormField id="account-last-name" label="Last name" required maxLength={60} value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} />
            <FormField id="account-phone" label="Phone number (optional)" type="tel" maxLength={30} value={form.phoneNumber} onChange={(event) => setForm({ ...form, phoneNumber: event.target.value })} />
          </div>
          <div className="flex flex-wrap justify-end gap-3 border-t border-border-soft px-5 py-4"><Button type="button" variant="quiet" onClick={() => { setEditing(false); setMessage('') }}>Cancel</Button><Button type="submit" busy={saving}>Save changes</Button></div>
        </form> : <dl className="divide-y divide-border-soft">{fields.map(([label, value]) => <div key={label} className="grid min-w-0 gap-1 px-5 py-4 sm:grid-cols-[11rem_minmax(0,1fr)]"><dt className="text-sm text-muted">{label}</dt><dd className="min-w-0 break-words text-sm font-medium text-primary">{value}</dd></div>)}</dl>}
        {message && <p role="status" aria-live="polite" className={`border-t border-border-soft px-5 py-4 text-sm ${message === 'Profile updated.' ? 'text-success' : 'text-signal'}`}>{message}</p>}
      </section>
      <aside className="surface h-fit p-5 sm:p-6">
        <div className="flex items-center justify-between gap-3"><h2 className="font-semibold">My connection code</h2><StatusBadge label="Active" tone="success" /></div>
        <p className="mt-2 text-sm leading-6 text-secondary">Share this exact code with a Driver you know. You will still control approval if they send a request.</p>
        <p className="mt-5 break-all rounded-control bg-ink px-4 py-4 font-mono text-sm tracking-wide text-primary">{formattedCode || 'Unavailable'}</p>
        <button type="button" disabled={!formattedCode} onClick={async () => { await navigator.clipboard.writeText(formattedCode); setCopied(true); window.setTimeout(() => setCopied(false), 1800) }} className="mt-3 inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-control border border-border bg-control px-4 text-sm font-semibold hover:bg-strong disabled:opacity-45">{copied ? <Check className="size-4 text-success" /> : <Copy className="size-4" />}{copied ? 'Copied' : 'Copy code'}</button>
        <p aria-live="polite" className="sr-only">{copied ? 'Connection code copied' : ''}</p>
      </aside>
    </div>
  </>
}
