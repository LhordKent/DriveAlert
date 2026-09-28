import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ConfirmDialog } from '../../src/components/ConfirmDialog'

it('focuses the safe action, traps tab focus, and confirms explicitly', async () => {
  const confirm = vi.fn()
  function Harness() {
    const [open, setOpen] = useState(true)
    return <ConfirmDialog open={open} title="Disconnect Driver?" message="Access will be removed." confirmLabel="Disconnect" destructive onClose={() => setOpen(false)} onConfirm={confirm} />
  }
  render(<Harness />)
  expect(document.body).toHaveStyle({ overflow: 'hidden' })
  const cancel = screen.getByRole('button', { name: 'Keep connection' })
  expect(cancel).toHaveFocus()
  await userEvent.tab({ shift: true })
  expect(screen.getByRole('button', { name: 'Disconnect' })).toHaveFocus()
  await userEvent.click(screen.getByRole('button', { name: 'Disconnect' }))
  expect(confirm).toHaveBeenCalledOnce()
})
