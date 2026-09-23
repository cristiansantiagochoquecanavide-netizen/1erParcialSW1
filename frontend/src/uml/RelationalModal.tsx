import { useState } from 'react'
import { DiagramFullView, RelationalSchema } from './types'

type Props = {
  schema: RelationalSchema
  projectName: string
  projectId?: string
  diagramId?: string
  token?: string
  isReadOnly?: boolean
  onClose: () => void
  onDiagramUpdated?: (fullView: DiagramFullView) => void
  onError?: (err: unknown) => void
}

export function RelationalModal({
  schema,
  projectName,
  projectId,
  diagramId,
  token,
  isReadOnly,
  onClose,
  onDiagramUpdated,
  onError
}: Props) {
  const [activeTab, setActiveTab] = useState<'tables' | 'sql' | 'import'>('tables')
  const [copied, setCopied] = useState(false)
  const [importSqlText, setImportSqlText] = useState('')
  const [importing, setImporting] = useState(false)
  const [importSuccess, setImportSuccess] = useState<string | null>(null)

  function handleCopySql() {
    navigator.clipboard.writeText(schema.sqlDdl)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  function handleDownloadSql() {
    const blob = new Blob([schema.sqlDdl], { type: 'text/sql;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `esquema_${projectName.toLowerCase().replace(/\s+/g, '_')}.sql`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  async function handleImportSqlSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!projectId || !diagramId || !token || !importSqlText.trim()) return

    setImporting(true)
    setImportSuccess(null)
    try {
      const res = await fetch(`/api/projects/${projectId}/diagrams/${diagramId}/import/sql`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({ sql: importSqlText })
      })

      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        throw new Error(body.message || 'Error al importar script SQL.')
      }

      const updatedFullView: DiagramFullView = await res.json()
      setImportSuccess('¡Tablas y relaciones SQL importadas exitosamente al modelo UML!')
      if (onDiagramUpdated) {
        onDiagramUpdated(updatedFullView)
      }
    } catch (err) {
      if (onError) onError(err)
      else alert(String(err))
    } finally {
      setImporting(false)
    }
  }

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal-card large">
        <header className="uml-modal-header">
          <div>
            <h3>Modelo Relacional y Script DDL (CU06 / CU11)</h3>
            <p className="modal-subtitle">
              Transformación bidireccional entre modelo conceptual UML y tablas SQL PostgreSQL.
            </p>
          </div>
          <button type="button" className="close-btn" onClick={onClose}>✕</button>
        </header>

        {/* Pestañas de navegación */}
        <div className="relational-tabs">
          <button
            type="button"
            className={`tab-btn ${activeTab === 'tables' ? 'active' : ''}`}
            onClick={() => setActiveTab('tables')}
          >
            📋 Esquema de Tablas ({schema.tables.length})
          </button>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'sql' ? 'active' : ''}`}
            onClick={() => setActiveTab('sql')}
          >
            💾 Script SQL DDL (PostgreSQL)
          </button>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'import' ? 'active' : ''}`}
            onClick={() => setActiveTab('import')}
          >
            📥 Importar SQL a UML (CU11)
          </button>
        </div>

        {/* Contenido Pestaña 1: Tablas Visuales */}
        {activeTab === 'tables' && (
          <div className="relational-tables-view">
            {schema.tables.length === 0 ? (
              <p className="empty-hint">No hay tablas generadas. Agregue clases al diagrama.</p>
            ) : (
              schema.tables.map((table) => (
                <article key={table.name} className="relational-table-card">
                  <header className="table-card-header">
                    <span className="table-title">🗄️ {table.name}</span>
                    <span className="uml-origin-tag">UML: «{table.umlSourceClass}»</span>
                  </header>

                  <div className="table-columns-container">
                    <table className="sql-table-grid">
                      <thead>
                        <tr>
                          <th>Columna</th>
                          <th>Tipo SQL</th>
                          <th>Clave</th>
                          <th>Restricciones</th>
                        </tr>
                      </thead>
                      <tbody>
                        {table.columns.map((col) => (
                          <tr key={col.name}>
                            <td className="col-name mono">
                              {col.primaryKey && <span className="pk-icon" title="Primary Key">🔑 </span>}
                              {col.name}
                            </td>
                            <td className="col-type mono">{col.sqlType}</td>
                            <td>
                              {col.primaryKey ? (
                                <span className="badge pk">PK</span>
                              ) : (
                                <span className="text-muted">-</span>
                              )}
                            </td>
                            <td>
                              <span className="constraints-list">
                                {!col.nullable && <span className="badge">NOT NULL</span>}
                                {col.unique && !col.primaryKey && (
                                  <span className="badge unique">UNIQUE</span>
                                )}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  {/* Claves Foráneas de la tabla */}
                  {table.foreignKeys.length > 0 && (
                    <div className="table-fks-section">
                      <span className="fk-section-title">Claves Foráneas (FKs):</span>
                      <ul className="fk-list">
                        {table.foreignKeys.map((fk) => (
                          <li key={fk.constraintName}>
                            <span className="mono">{fk.columnName}</span> ➔{' '}
                            <strong>{fk.targetTable}</strong>({fk.targetColumn})
                            {fk.cascadeDelete && <span className="badge cascade">CASCADE</span>}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </article>
              ))
            )}
          </div>
        )}

        {/* Contenido Pestaña 2: Script SQL DDL */}
        {activeTab === 'sql' && (
          <div className="sql-ddl-view">
            <div className="sql-actions-bar">
              <span>Script PostgreSQL DDL listo para ejecución:</span>
              <div className="actions">
                <button type="button" className="secondary small" onClick={handleCopySql}>
                  {copied ? '✓ Copiado' : '📋 Copiar SQL'}
                </button>
                <button type="button" className="small" onClick={handleDownloadSql}>
                  ⬇️ Descargar .sql
                </button>
              </div>
            </div>
            <pre className="sql-code-block">
              <code>{schema.sqlDdl}</code>
            </pre>
          </div>
        )}

        {/* Contenido Pestaña 3: Importar SQL DDL a Clases UML (CU11) */}
        {activeTab === 'import' && (
          <div className="sql-import-view" style={{ padding: '1rem 0' }}>
            <p style={{ fontSize: '0.9rem', color: '#475569', marginBottom: '0.75rem' }}>
              Ingresa una o varias sentencias <code>CREATE TABLE</code> en SQL (PostgreSQL, MySQL o estándar).
              El sistema reconocerá automáticamente el nombre de la tabla como clase UML, sus campos como atributos con sus tipos correspondientes, claves primarias y claves foráneas convertidas en relaciones con cardinalidad (CU11).
            </p>

            {importSuccess && (
              <div style={{ background: '#dcfce7', color: '#15803d', padding: '0.75rem 1rem', borderRadius: '6px', marginBottom: '0.75rem', fontWeight: 600 }}>
                {importSuccess}
              </div>
            )}

            <form onSubmit={handleImportSqlSubmit}>
              <textarea
                rows={10}
                value={importSqlText}
                onChange={(e) => setImportSqlText(e.target.value)}
                placeholder={`CREATE TABLE producto (\n  id SERIAL PRIMARY KEY,\n  nombre VARCHAR(100) NOT NULL,\n  precio NUMERIC(10,2) NOT NULL,\n  stock INT DEFAULT 0\n);`}
                style={{ width: '100%', fontFamily: 'monospace', fontSize: '0.85rem', padding: '0.75rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
                disabled={importing || isReadOnly}
              />
              <div style={{ marginTop: '0.75rem', display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <button
                  type="submit"
                  disabled={importing || !importSqlText.trim() || isReadOnly}
                  style={{ background: '#2563eb', color: 'white', padding: '0.5rem 1rem', borderRadius: '6px', fontWeight: 600 }}
                >
                  {importing ? 'Procesando SQL...' : '📥 Reconocer e Importar a Modelo UML'}
                </button>
              </div>
            </form>
          </div>
        )}

        <footer className="uml-modal-footer">
          <button type="button" onClick={onClose}>Cerrar</button>
        </footer>
      </div>
    </div>
  )
}
