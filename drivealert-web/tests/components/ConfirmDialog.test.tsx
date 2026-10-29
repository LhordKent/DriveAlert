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

it('keeps focus inside while busy and restores the trigger only on close', async () => {
  const close = vi.fn()
  const trigger = document.createElement('button')
  document.body.append(trigger)
  trigger.focus()
  const props = { open: true, title: 'Delete record?', message: 'Remove this record.', confirmLabel: 'Delete', onConfirm: vi.fn(), onClose: close }
  const view = render(<ConfirmDialog {...props} />)
  expect(screen.getByRole('button', { name: 'Keep connection' })).toHaveFocus()
  view.rerender(<ConfirmDialog {...props} busy />)
  const dialog = screen.getByRole('alertdialog')
  expect(dialog).toHaveFocus()
  await userEvent.tab()
  expect(dialog).toHaveFocus()
  await userEvent.tab({ shift: true })
  expect(dialog).toHaveFocus()
  await userEvent.keyboard('{Escape}')
  expect(close).not.toHaveBeenCalled()
  view.rerender(<ConfirmDialog {...props} onClose={close} />)
  expect(screen.getByRole('button', { name: 'Keep connection' })).toHaveFocus()
  await userEvent.keyboard('{Escape}')
  expect(close).toHaveBeenCalledOnce()
  view.rerender(<ConfirmDialog {...props} open={false} />)
  expect(trigger).toHaveFocus()
  trigger.remove()
})
