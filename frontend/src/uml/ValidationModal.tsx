import { ValidationReport } from './types'

type Props = {
  report: ValidationReport
  onProceedToRelational?: () => void
  onClose: () => void
}

export function ValidationModal({ report, onProceedToRelational, onClose }: Props) {
  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal-card">
        <header className="uml-modal-header">
          <div>
            <h3>Validación del Modelo UML (CU05)</h3>
            <p className="modal-subtitle">
              Diagnóstico de consistencia arquitectónica y reglas de modelado.
            </p>
          </div>
          <button type="button" className="close-btn" onClick={onClose}>✕</button>
        </header>

        <div className={`validation-summary-banner ${report.valid ? 'valid' : 'invalid'}`}>
          <div className="banner-icon">{report.valid ? '✓' : '⚠️'}</div>
          <div>
            <strong>
              {report.valid
                ? '¡Modelo consistente y válido!'
                : `Se encontraron ${report.errorCount} error(es) en el modelo`}
            </strong>
            <p>
              {report.valid
                ? 'El diagrama cumple con las reglas semánticas y está listo para transformarse en modelo relacional o backend.'
                : 'Debe corregir los errores marcados para garantizar la generación correcta de tablas y código.'}
            </p>
          </div>
        </div>

        <div className="validation-stats-row">
          <div className="stat-pill error">
            <strong>{report.errorCount}</strong> Errores
          </div>
          <div className="stat-pill warning">
            <strong>{report.warningCount}</strong> Advertencias
          </div>
          <div className="stat-pill total">
            <strong>{report.issues.length}</strong> Observaciones
          </div>
        </div>

        <div className="issues-list-container">
          {report.issues.length === 0 ? (
            <div className="empty-issues">
              <p>No se encontraron problemas ni advertencias. Tu diagrama de clases está en perfecto estado.</p>
            </div>
          ) : (
            report.issues.map((issue, index) => (
              <article key={index} className={`issue-card ${issue.severity.toLowerCase()}`}>
                <header className="issue-header">
                  <span className={`issue-severity-badge ${issue.severity.toLowerCase()}`}>
                    {issue.severity}
                  </span>
                  <span className="issue-target">
                    {issue.elementoNombre ? `Elemento: «${issue.elementoNombre}»` : 'Diagrama general'}
                  </span>
                </header>
                <p className="issue-message">{issue.mensaje}</p>
                {issue.sugerencia && (
                  <div className="issue-suggestion">
                    <span className="icon">💡</span>
                    <span>{issue.sugerencia}</span>
                  </div>
                )}
              </article>
            ))
          )}
        </div>

        <footer className="uml-modal-footer space-between">
          <button type="button" className="secondary" onClick={onClose}>Cerrar</button>
          {report.valid && onProceedToRelational && (
            <button
              type="button"
              onClick={() => {
                onClose()
                onProceedToRelational()
              }}
            >
              Generar Modelo Relacional ➔
            </button>
          )}
        </footer>
      </div>
    </div>
  )
}
