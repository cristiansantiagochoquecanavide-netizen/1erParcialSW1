import { useState } from 'react'
import { TipoRelacionUML } from './types'

type Props = {
  initialType?: TipoRelacionUML
  initialName?: string | null
  initialMultOrigin?: string | null
  initialMultTarget?: string | null
  sourceName: string
  targetName: string
  onSave: (type: TipoRelacionUML, name: string | null, multOrigin: string | null, multTarget: string | null) => void
  onDelete?: () => void
  onClose: () => void
}

export function RelationModal({
  initialType = 'ASOCIACION',
  initialName = '',
  initialMultOrigin = '',
  initialMultTarget = '',
  sourceName,
  targetName,
  onSave,
  onDelete,
  onClose
}: Props) {
  const [type, setType] = useState<TipoRelacionUML>(initialType)
  const [name, setName] = useState(initialName || '')
  const [multOrigin, setMultOrigin] = useState(initialMultOrigin || '')
  const [multTarget, setMultTarget] = useState(initialMultTarget || '')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    onSave(type, name.trim() || null, multOrigin.trim() || null, multTarget.trim() || null)
  }

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal-card small">
        <header className="uml-modal-header">
          <h3>Configurar Relación UML</h3>
          <button type="button" className="close-btn" onClick={onClose}>✕</button>
        </header>

        <form onSubmit={handleSubmit}>
          <div className="relation-endpoints">
            <span><strong>Origen:</strong> {sourceName}</span>
            <span>➔</span>
            <span><strong>Destino:</strong> {targetName}</span>
          </div>

          <div className="form-field">
            <label>Tipo de Relación</label>
            <select value={type} onChange={e => setType(e.target.value as TipoRelacionUML)}>
              <option value="ASOCIACION">Asociación (——)</option>
              <option value="HERENCIA">Generalización / Herencia (——▷)</option>
              <option value="AGREGACION">Agregación (◇——)</option>
              <option value="COMPOSICION">Composición (◆——)</option>
              <option value="DEPENDENCIA">Dependencia (- - ▷)</option>
            </select>
          </div>

          <div className="form-field">
            <label>Nombre / Rol de la Relación (Opcional)</label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="Ej. tiene, pertenece, contiene"
              maxLength={120}
            />
          </div>

          <div className="form-group-row">
            <div className="form-field flex-1">
              <label>Multiplicidad Origen</label>
              <input
                type="text"
                value={multOrigin}
                onChange={e => setMultOrigin(e.target.value)}
                placeholder="Ej. 1, 0..1, *, 1..*"
                maxLength={20}
              />
            </div>
            <div className="form-field flex-1">
              <label>Multiplicidad Destino</label>
              <input
                type="text"
                value={multTarget}
                onChange={e => setMultTarget(e.target.value)}
                placeholder="Ej. 1, 0..1, *, 1..*"
                maxLength={20}
              />
            </div>
          </div>

          <footer className="uml-modal-footer space-between">
            {onDelete ? (
              <button type="button" className="danger" onClick={onDelete}>Eliminar Relación</button>
            ) : <span />}
            <div className="actions">
              <button type="button" className="secondary" onClick={onClose}>Cancelar</button>
              <button type="submit">Guardar Relación</button>
            </div>
          </footer>
        </form>
      </div>
    </div>
  )
}
