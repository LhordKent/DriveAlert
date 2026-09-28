import type { ReactNode } from 'react'
import { Brand } from '../../components/Brand'
import { AuthBrandPanel } from './AuthBrandPanel'

interface AuthLayoutProps {
  title: string
  description: string
  children: ReactNode
  footer: ReactNode
}

export function AuthLayout({ title, description, children, footer }: AuthLayoutProps) {
  return (
    <main className="grid min-h-screen bg-ink text-primary lg:grid-cols-[minmax(0,0.95fr)_minmax(34rem,1.05fr)]">
      <AuthBrandPanel />
      <section className="flex min-h-screen items-center justify-center px-5 py-10 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-9 lg:hidden"><Brand /></div>
          <div className="surface p-6 sm:p-8">
            <h1 className="text-2xl font-bold tracking-[-0.025em] text-primary">{title}</h1>
            <p className="mt-2 text-sm leading-6 text-secondary">{description}</p>
            <div className="mt-7">{children}</div>
          </div>
          <p className="mt-6 text-center text-sm text-secondary">{footer}</p>
        </div>
      </section>
    </main>
  )
}
