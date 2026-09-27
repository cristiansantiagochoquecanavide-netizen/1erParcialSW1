import { useEffect, useRef } from 'react'
import './confirm-modal.css'

interface ConfirmModalProps {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmModal({
  open,
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  onConfirm,
  onCancel
}: ConfirmModalProps) {
  const confirmButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return

    confirmButtonRef.current?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCancel()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onCancel])

  if (!open) return null

  return (
    <div className="confirm-modal-backdrop" role="presentation" onMouseDown={(event) => event.stopPropagation()}>
      <section
        className="confirm-modal"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
        aria-describedby="confirm-modal-message"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="confirm-modal-header">
          <span className="confirm-modal-icon" aria-hidden="true">!</span>
          <h2 id="confirm-modal-title">{title}</h2>
        </header>
        <p id="confirm-modal-message">{message}</p>
        <div className="confirm-modal-actions">
          <button type="button" className="confirm-modal-cancel" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button type="button" className="confirm-modal-confirm" onClick={onConfirm} ref={confirmButtonRef}>
            {confirmLabel}
          </button>
        </div>
      </section>
    </div>
  )
}
