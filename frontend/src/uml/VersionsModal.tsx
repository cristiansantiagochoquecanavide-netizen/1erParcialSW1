import { useEffect, useState } from 'react'
import { DiagramFullView } from './types'

type VersionSummary = {
  id: string
  diagramaId: string
  numeroVersion: number
  descripcion: string
  autorNombre: string
  autorEmail: string
  fecha: string
}

type Props = {
  projectId: string
  diagramId: string
  token: string
  isReadOnly: boolean
  onClose: () => void
  onRestored: (fullView: DiagramFullView) => void
  onError: (err: unknown) => void
}

export function VersionsModal({
  projectId,
  diagramId,
  token,
  isReadOnly,
  onClose,
  onRestored,
  onError
}: Props) {
  const [versions, setVersions] = useState<VersionSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [description, setDescription] = useState('')
  const [creating, setCreating] = useState(false)

  async function loadVersions() {
    try {
      setLoading(true)
      const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/versions`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      if (!res.ok) throw new Error('Error al cargar versiones.')
      const data: VersionSummary[] = await res.json()
      setVersions(data)
    } catch (err) {
      onError(err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadVersions()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (isReadOnly) return
    try {
      setCreating(true)
      const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/versions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({ descripcion: description || undefined })
      })
      if (!res.ok) {
        const b = await res.json().catch(() => ({}))
        throw new Error(b.message || 'Error al crear versión.')
      }
      setDescription('')
      loadVersions()
    } catch (err) {
      onError(err)
    } finally {
      setCreating(false)
    }
  }

  async function handleRestore(version: VersionSummary) {
    if (isReadOnly) return
    if (!confirm(`¿Restaurar la versión v${version.numeroVersion} («${version.descripcion}»)? Esto reemplazará las clases y relaciones actuales por las de esta versión.`)) {
      return
    }

    try {
      setLoading(true)
      const res = await fetch(
        `/api/projects/${projectId}/diagrams/${diagramId}/versions/${version.id}/restore`,
        {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` }
        }
      )
      if (!res.ok) {
        const b = await res.json().catch(() => ({}))
        throw new Error(b.message || 'Error al restaurar versión.')
      }
      const full: DiagramFullView = await res.json()
      onRestored(full)
      onClose()
    } catch (err) {
      onError(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal versions-modal" style={{ maxWidth: '680px' }}>
        <div className="uml-modal-header">
          <h3>🕒 Historial de Versiones del Modelo (CU09)</h3>
          <button type="button" className="close-btn" onClick={onClose}>
            ✕
          </button>
        </div>

        <div style={{ marginTop: '1rem' }}>
          {!isReadOnly && (
            <form onSubmit={handleCreate} style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem' }}>
              <input
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Descripción del hito (ej. Modelo v1 con clientes y pedidos)..."
                style={{ flex: 1, padding: '0.5rem 0.75rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
              />
              <button type="submit" disabled={creating} className="btn btn-primary">
                {creating ? 'Guardando...' : '📷 Crear Versión / Snapshot'}
              </button>
            </form>
          )}

          <h4>Versiones Registradas</h4>
          {loading ? (
            <p style={{ marginTop: '0.5rem', color: '#64748b' }}>Cargando historial...</p>
          ) : versions.length === 0 ? (
            <p style={{ marginTop: '0.5rem', color: '#64748b' }}>No se han guardado versiones todavía.</p>
          ) : (
            <div style={{ maxHeight: '350px', overflowY: 'auto', marginTop: '0.75rem', border: '1px solid #e2e8f0', borderRadius: '6px' }}>
              <table className="data-table" style={{ margin: 0 }}>
                <thead>
                  <tr>
                    <th>Versión</th>
                    <th>Descripción</th>
                    <th>Autor</th>
                    <th>Fecha</th>
                    {!isReadOnly && <th>Acción</th>}
                  </tr>
                </thead>
                <tbody>
                  {versions.map((v) => (
                    <tr key={v.id}>
                      <td>
                        <strong style={{ color: '#2563eb' }}>v{v.numeroVersion}</strong>
                      </td>
                      <td>{v.descripcion || 'Sin descripción'}</td>
                      <td>{v.autorNombre}</td>
                      <td style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        {new Date(v.fecha).toLocaleString()}
                      </td>
                      {!isReadOnly && (
                        <td>
                          <button
                            type="button"
                            className="btn-sm btn-secondary"
                            onClick={() => handleRestore(v)}
                            title="Restaurar este estado"
                          >
                            ↩ Restaurar
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="uml-modal-actions" style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cerrar
          </button>
        </div>
      </div>
    </div>
  )
}
