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
  // Predeterminado en XMI / XML para interoperabilidad (CU13)
  const [activeFormat, setActiveFormat] = useState<'xmi' | 'json'>('xmi')
  const [importText, setImportText] = useState('')
  const [loading, setLoading] = useState(false)
  const [selectedFileName, setSelectedFileName] = useState<string | null>(null)
  const [selectedFileSize, setSelectedFileSize] = useState<string | null>(null)
  const [statusMessage, setStatusMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  // Descargar archivo XMI / XML con cardinalidades
  async function handleExportXmi(ext: 'xmi' | 'xml' = 'xmi') {
    try {
      setLoading(true)
      setStatusMessage(null)
      const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/xmi/export`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      if (!res.ok) throw new Error('Error al exportar archivo XMI.')
      const blob = await res.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${diagramName.toLowerCase().replace(/\s+/g, '_')}.${ext}`
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

  // Descargar backup JSON completo con cardinalidades y coordenadas
  async function handleExportJson() {
    try {
      setLoading(true)
      setStatusMessage(null)
      const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/xmi/export-json`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      if (!res.ok) throw new Error('Error al exportar archivo JSON.')
      const blob = await res.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${diagramName.toLowerCase().replace(/\s+/g, '_')}_backup.json`
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

  // Importar archivo
  async function handleImportSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (isReadOnly) return

    if (!importText.trim()) {
      setStatusMessage({
        type: 'error',
        text: activeFormat === 'xmi'
          ? 'Por favor selecciona un archivo .xmi / .xml de Enterprise Architect o pega su contenido XML antes de importar.'
          : 'Por favor selecciona un archivo .json o pega el JSON del diagrama antes de importar.'
      })
      return
    }

    try {
      setLoading(true)
      setStatusMessage(null)
      const endpoint =
        activeFormat === 'xmi'
          ? `/api/projects/${projectId}/diagrams/${diagramId}/xmi/import`
          : `/api/projects/${projectId}/diagrams/${diagramId}/xmi/import-json`

      const contentType = activeFormat === 'xmi' ? 'application/xml' : 'application/json'

      const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': contentType,
          Authorization: `Bearer ${token}`
        },
        body: importText
      })

      if (!res.ok) {
        const b = await res.json().catch(() => ({}))
        throw new Error(b.message || 'Error al importar diagrama.')
      }

      const updated: DiagramFullView = await res.json()
      const elCount = updated.elements?.length ?? 0
      const relCount = updated.relations?.length ?? 0

      setStatusMessage({
        type: 'success',
        text: `¡Importación exitosa! Se cargaron ${elCount} clases/tablas y ${relCount} relaciones al diagrama.`
      })

      onDiagramUpdated(updated)
      setTimeout(() => {
        onClose()
      }, 1500)
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Error desconocido al importar'
      setStatusMessage({ type: 'error', text: msg })
      onError(err)
    } finally {
      setLoading(false)
    }
  }

  function handleFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setSelectedFileName(file.name)
    setSelectedFileSize((file.size / 1024).toFixed(1) + ' KB')
    setStatusMessage(null)

    const reader = new FileReader()
    reader.onload = (event) => {
      setImportText(String(event.target?.result || ''))
    }
    reader.readAsText(file)
  }

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal xmi-modal" style={{ maxWidth: '680px', width: '90%' }}>
        <div className="uml-modal-header">
          <h3>📦 Exportación e Importación de Modelos (CU13)</h3>
          <button type="button" className="close-btn" onClick={onClose}>
            ✕
          </button>
        </div>

        {/* Notificación de Estado */}
        {statusMessage && (
          <div
            style={{
              marginTop: '0.75rem',
              padding: '0.75rem 1rem',
              borderRadius: '6px',
              fontSize: '0.85rem',
              fontWeight: 500,
              background: statusMessage.type === 'success' ? '#dcfce7' : '#fee2e2',
              color: statusMessage.type === 'success' ? '#166534' : '#991b1b',
              border: `1px solid ${statusMessage.type === 'success' ? '#86efac' : '#fca5a5'}`
            }}
          >
            {statusMessage.type === 'success' ? '✅ ' : '⚠️ '}
            {statusMessage.text}
          </div>
        )}

        {/* Selector de Formato: XMI / XML prioritario vs JSON nativo */}
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.5rem' }}>
          <button
            type="button"
            className={`tab-btn ${activeFormat === 'xmi' ? 'active' : ''}`}
            onClick={() => { setActiveFormat('xmi'); setImportText(''); setSelectedFileName(null); setStatusMessage(null) }}
            style={{
              padding: '0.5rem 1rem',
              borderRadius: '6px',
              fontWeight: activeFormat === 'xmi' ? 700 : 500,
              background: activeFormat === 'xmi' ? '#2563eb' : '#f1f5f9',
              color: activeFormat === 'xmi' ? 'white' : '#475569',
              border: 'none',
              cursor: 'pointer'
            }}
          >
            🏢 Estándar OMG XMI / XML (.xmi, .xml)
          </button>
          <button
            type="button"
            className={`tab-btn ${activeFormat === 'json' ? 'active' : ''}`}
            onClick={() => { setActiveFormat('json'); setImportText(''); setSelectedFileName(null); setStatusMessage(null) }}
            style={{
              padding: '0.5rem 1rem',
              borderRadius: '6px',
              fontWeight: activeFormat === 'json' ? 700 : 500,
              background: activeFormat === 'json' ? '#2563eb' : '#f1f5f9',
              color: activeFormat === 'json' ? 'white' : '#475569',
              border: 'none',
              cursor: 'pointer'
            }}
          >
            📋 Backup JSON Completo (.json)
          </button>
        </div>

        <div style={{ marginTop: '1rem', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {/* Sección de Exportación */}
          <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.4rem' }}>
              <h4 style={{ margin: 0 }}>Exportar Diagrama Actual</h4>
              <span style={{ fontSize: '0.75rem', background: '#dbeafe', color: '#1d4ed8', padding: '2px 8px', borderRadius: '4px', fontWeight: 600 }}>
                {activeFormat === 'xmi' ? 'Formato OMG XMI 2.1 / XML' : 'Formato JSON'}
              </span>
            </div>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '1rem' }}>
              {activeFormat === 'xmi'
                ? 'Genera un documento XML bajo el estándar OMG XMI compatible con Enterprise Architect, StarUML y Visual Paradigm, exportando clases, generalizaciones, asociaciones y cardinalidades en <ownedEnd>.'
                : 'Exporta la estructura completa con coordenadas en lienzo, estilos, descripciones y multiplicidades en formato JSON.'}
            </p>
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
              {activeFormat === 'xmi' ? (
                <>
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={loading}
                    onClick={() => handleExportXmi('xmi')}
                    style={{ background: '#2563eb', color: 'white', padding: '0.6rem 1.2rem', borderRadius: '6px', border: 'none', cursor: 'pointer', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                  >
                    📥 Descargar como XMI (.xmi)
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    disabled={loading}
                    onClick={() => handleExportXmi('xml')}
                    style={{ background: '#475569', color: 'white', padding: '0.6rem 1.2rem', borderRadius: '6px', border: 'none', cursor: 'pointer', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                  >
                    📥 Descargar como XML (.xml)
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={loading}
                  onClick={handleExportJson}
                  style={{ background: '#2563eb', color: 'white', padding: '0.6rem 1.2rem', borderRadius: '6px', border: 'none', cursor: 'pointer', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                >
                  📥 Descargar Backup JSON (.json)
                </button>
              )}
            </div>
          </div>

          {/* Sección de Importación */}
          {!isReadOnly && (
            <div style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.4rem' }}>
                <h4 style={{ margin: 0 }}>
                  Importar {activeFormat === 'xmi' ? 'Archivo XMI / XML (.xmi / .xml)' : 'Archivo JSON (.json)'}
                </h4>
                {activeFormat === 'xmi' && (
                  <span style={{ fontSize: '0.7rem', background: '#ecfdf5', color: '#047857', padding: '2px 8px', borderRadius: '4px', fontWeight: 600 }}>
                    Enterprise Architect 1.1 / 2.1 & Tablas
                  </span>
                )}
              </div>
              <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.75rem' }}>
                {activeFormat === 'xmi'
                  ? 'Compatible con archivos exportados de Enterprise Architect (XMI 1.1, 1.2, 2.1, Native XML, tablas relacionales con columnas), StarUML y Visual Paradigm.'
                  : 'Restaura fielmente el diagrama completo con clases, relaciones y posiciones exactas desde el archivo JSON:'}
              </p>

              <form onSubmit={handleImportSubmit}>
                <div style={{ marginBottom: '0.75rem' }}>
                  <input
                    type="file"
                    accept={activeFormat === 'xmi' ? '.xmi,.xml,.uml' : '.json'}
                    onChange={handleFileSelected}
                    style={{ fontSize: '0.85rem', display: 'block' }}
                  />
                  {selectedFileName && (
                    <div style={{ marginTop: '0.4rem', fontSize: '0.8rem', color: '#16a34a', display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600 }}>
                      <span>📄 Archivo cargado: {selectedFileName} ({selectedFileSize})</span>
                    </div>
                  )}
                </div>

                <textarea
                  rows={6}
                  value={importText}
                  onChange={(e) => setImportText(e.target.value)}
                  placeholder={
                    activeFormat === 'xmi'
                      ? 'O pega aquí directamente el contenido XML / XMI del archivo...'
                      : 'O pega aquí el contenido JSON del diagrama...'
                  }
                  style={{ width: '100%', padding: '0.5rem', borderRadius: '6px', border: '1px solid #cbd5e1', fontFamily: 'monospace', fontSize: '0.8rem', resize: 'vertical' }}
                />

                <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'flex-end' }}>
                  <button
                    type="submit"
                    disabled={loading || !importText.trim()}
                    className="btn btn-primary"
                    style={{ background: '#16a34a', color: 'white', padding: '0.5rem 1rem', borderRadius: '6px', border: 'none', cursor: 'pointer', fontWeight: 600 }}
                  >
                    {loading ? 'Importando modelo...' : activeFormat === 'xmi' ? '📤 Importar XMI/XML al Diagrama' : '📤 Importar JSON al Diagrama'}
                  </button>
                </div>
              </form>
            </div>
          )}
        </div>

        <div className="uml-modal-actions" style={{ marginTop: '1.25rem', display: 'flex', justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose} style={{ padding: '0.4rem 1rem', borderRadius: '6px' }}>
            Cerrar
          </button>
        </div>
      </div>
    </div>
  )
}
