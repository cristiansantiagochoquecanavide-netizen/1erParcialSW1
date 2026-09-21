import { useState } from 'react'
import { DiagramFullView } from './types'

type Props = {
  projectId: string
  diagramId: string
  diagramName: string
  token: string
  isReadOnly: boolean
  onClose: () => void
  onDiagramUpdated: (fullView: DiagramFullView) => void
  onError: (err: unknown) => void
}

export function XmiModal({
  projectId,
  diagramId,
  diagramName,
  token,
  isReadOnly,
  onClose,
  onDiagramUpdated,
  onError
}: Props) {
  const [importXml, setImportXml] = useState('')
  const [loading, setLoading] = useState(false)

  // Descargar archivo XMI 2.1
  async function handleExportXmi() {
    try {
      setLoading(true)
      const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/xmi/export`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      if (!res.ok) throw new Error('Error al exportar archivo XMI.')
      const blob = await res.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${diagramName.toLowerCase().replace(/\s+/g, '_')}.xmi`
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
    } catch (err) {
      onError(err)
    } finally {
      setLoading(false)
    }
  }

  // Importar archivo XMI
  async function handleImportXmi(e: React.FormEvent) {
    e.preventDefault()
    if (isReadOnly || !importXml.trim()) return

    try {
      setLoading(true)
      const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/xmi/import`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/xml',
          Authorization: `Bearer ${token}`
        },
        body: importXml
      })
      if (!res.ok) {
        const b = await res.json().catch(() => ({}))
        throw new Error(b.message || 'Error al importar archivo XMI.')
      }
      const updated: DiagramFullView = await res.json()
      onDiagramUpdated(updated)
      onClose()
    } catch (err) {
      onError(err)
    } finally {
      setLoading(false)
    }
  }

  function handleFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (event) => {
      setImportXml(String(event.target?.result || ''))
    }
    reader.readAsText(file)
  }

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal xmi-modal" style={{ maxWidth: '640px' }}>
        <div className="uml-modal-header">
          <h3>📦 Interoperabilidad XMI / Enterprise Architect (CU13)</h3>
          <button type="button" className="close-btn" onClick={onClose}>
            ✕
          </button>
        </div>

        <div style={{ marginTop: '1rem', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {/* Exportación */}
          <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            <h4 style={{ marginBottom: '0.4rem' }}>Exportar Modelo UML</h4>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '1rem' }}>
              Genera un documento estándar OMG XMI 2.1 con clases, atributos, visibilidad, generalizaciones y asociaciones, listo para abrir en Enterprise Architect, StarUML o Visual Paradigm.
            </p>
            <button
              type="button"
              className="btn btn-primary"
              disabled={loading}
              onClick={handleExportXmi}
            >
              📥 Descargar Archivo XMI (.xmi)
            </button>
          </div>

          {/* Importación */}
          {!isReadOnly && (
            <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
              <h4 style={{ marginBottom: '0.4rem' }}>Importar Modelo XMI</h4>
              <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.75rem' }}>
                Selecciona un archivo .xmi o pega el contenido XML para agregar sus clases y atributos al diagrama:
              </p>

              <form onSubmit={handleImportXmi}>
                <input
                  type="file"
                  accept=".xmi,.xml"
                  onChange={handleFileSelected}
                  style={{ marginBottom: '0.75rem', fontSize: '0.85rem' }}
                />

                <textarea
                  rows={6}
                  value={importXml}
                  onChange={(e) => setImportXml(e.target.value)}
                  placeholder="O pega aquí el contenido XML / XMI..."
                  style={{ width: '100%', padding: '0.5rem', borderRadius: '6px', border: '1px solid #cbd5e1', fontFamily: 'monospace', fontSize: '0.8rem', resize: 'vertical' }}
                />

                <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'flex-end' }}>
                  <button
                    type="submit"
                    disabled={loading || !importXml.trim()}
                    className="btn btn-primary"
                  >
                    {loading ? 'Importando...' : '📤 Cargar e Importar a Lienzo'}
                  </button>
                </div>
              </form>
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
