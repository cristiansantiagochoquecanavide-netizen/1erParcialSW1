import type { ReactNode } from 'react'
import './ai-modal.css'

type AiModalShellProps = {
  title: string
  subtitle: string
  icon?: ReactNode
  children: ReactNode
  onClose: () => void
}

export function AiModalShell({ title, subtitle, icon, children, onClose }: AiModalShellProps) {
  return (
    <div className="ai-modal-backdrop">
      <section className="ai-modal-shell" role="dialog" aria-modal="true" aria-label={title}>
        <header className="ai-modal-shell-header">
          <div className="ai-modal-shell-heading">
            {icon && <span className="ai-modal-shell-icon" aria-hidden="true">{icon}</span>}
            <div>
              <h2 className="ai-modal-shell-title">{title}</h2>
              <p className="ai-modal-shell-subtitle">{subtitle}</p>
            </div>
          </div>
          <button type="button" className="ai-modal-shell-close" onClick={onClose} aria-label="Cerrar">
            x
          </button>
        </header>
        <div className="ai-modal-shell-content">{children}</div>
      </section>
    </div>
  )
}
