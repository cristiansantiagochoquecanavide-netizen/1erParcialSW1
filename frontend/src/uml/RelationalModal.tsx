import { useState } from 'react'
import { RelationalSchema } from './types'

type Props = {
  schema: RelationalSchema
  projectName: string
  onClose: () => void
}

export function RelationalModal({ schema, projectName, onClose }: Props) {
  const [activeTab, setActiveTab] = useState<'tables' | 'sql'>('tables')
  const [copied, setCopied] = useState(false)

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

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal-card large">
        <header className="uml-modal-header">
          <div>
            <h3>Modelo Relacional y Script DDL (CU06)</h3>
            <p className="modal-subtitle">
              Transformación automática del modelo conceptual UML a base de datos PostgreSQL.
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

        <footer className="uml-modal-footer">
          <button type="button" onClick={onClose}>Cerrar</button>
        </footer>
      </div>
    </div>
  )
}
