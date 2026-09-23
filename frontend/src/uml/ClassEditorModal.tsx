import React, { useState } from 'react'
import {
  TipoElementoUML,
  UmlAttribute,
  UmlMethod,
  UmlProperties,
  UmlVisibility,
  UML_DATA_TYPES,
  parseUmlProperties
} from './types'

type Props = {
  initialName: string
  initialType: TipoElementoUML
  initialPropertiesJson: string
  onSave: (name: string, type: TipoElementoUML, properties: UmlProperties) => void
  onClose: () => void
}

export function ClassEditorModal({
  initialName,
  initialType,
  initialPropertiesJson,
  onSave,
  onClose
}: Props) {
  const [name, setName] = useState(initialName)
  const [type, setType] = useState<TipoElementoUML>(initialType)

  const initialProps = parseUmlProperties(initialPropertiesJson)
  const [description, setDescription] = useState(initialProps.description || '')
  const [stereotype, setStereotype] = useState(initialProps.stereotype || '')
  const [attributes, setAttributes] = useState<UmlAttribute[]>(initialProps.attributes)
  const [methods, setMethods] = useState<UmlMethod[]>(initialProps.methods)

  // Estado para nuevo atributo
  const [newAttrVis, setNewAttrVis] = useState<UmlVisibility>('+')
  const [newAttrName, setNewAttrName] = useState('')
  const [newAttrType, setNewAttrType] = useState<string>('String')
  const [customAttrType, setCustomAttrType] = useState('')
  const [isCustomType, setIsCustomType] = useState(false)

  // Estado para nuevo método
  const [newMethodVis, setNewMethodVis] = useState<UmlVisibility>('+')
  const [newMethodName, setNewMethodName] = useState('')
  const [newMethodParams, setNewMethodParams] = useState('')
  const [newMethodReturn, setNewMethodReturn] = useState('void')

  // Validación y feedback
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // Manejadores de Atributos existentes
  function handleUpdateAttribute(id: string, field: keyof UmlAttribute, value: string) {
    setAttributes((prev) =>
      prev.map((attr) => (attr.id === id ? { ...attr, [field]: value } : attr))
    )
  }

  function handleRemoveAttribute(id: string) {
    setAttributes((prev) => prev.filter((a) => a.id !== id))
  }

  function handleAddAttribute() {
    setErrorMessage(null)
    const trimmedName = newAttrName.trim()
    if (!trimmedName) {
      setErrorMessage('El nombre del atributo no puede estar vacío.')
      return
    }

    const finalType = isCustomType ? (customAttrType.trim() || 'String') : newAttrType

    const attr: UmlAttribute = {
      id: crypto.randomUUID(),
      visibility: newAttrVis,
      name: trimmedName,
      type: finalType
    }
    setAttributes([...attributes, attr])
    setNewAttrName('')
    setCustomAttrType('')
    setIsCustomType(false)
  }

  // Manejadores de Métodos existentes
  function handleUpdateMethod(id: string, field: keyof UmlMethod, value: string) {
    setMethods((prev) =>
      prev.map((m) => (m.id === id ? { ...m, [field]: value } : m))
    )
  }

  function handleRemoveMethod(id: string) {
    setMethods((prev) => prev.filter((m) => m.id !== id))
  }

  function handleAddMethod() {
    setErrorMessage(null)
    const trimmedName = newMethodName.trim()
    if (!trimmedName) {
      setErrorMessage('El nombre del método no puede estar vacío.')
      return
    }
    const m: UmlMethod = {
      id: crypto.randomUUID(),
      visibility: newMethodVis,
      name: trimmedName,
      parameters: newMethodParams.trim(),
      returnType: newMethodReturn.trim() || 'void'
    }
    setMethods([...methods, m])
    setNewMethodName('')
    setNewMethodParams('')
    setNewMethodReturn('void')
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErrorMessage(null)
    if (!name.trim()) {
      setErrorMessage('El nombre de la clase es obligatorio.')
      return
    }

    // Validar que ningún atributo tenga nombre vacío
    for (const attr of attributes) {
      if (!attr.name.trim()) {
        setErrorMessage('Todos los atributos deben tener un nombre válido.')
        return
      }
    }

    onSave(name.trim(), type, {
      description: description.trim(),
      stereotype: stereotype.trim(),
      attributes,
      methods
    })
  }

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal-card class-editor-dialog" style={{ maxWidth: '780px', width: '95%' }}>
        <header className="uml-modal-header">
          <div>
            <h3>Editar Elemento UML: {name || initialName}</h3>
            <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
              Modifica nombre, descripción, estereotipo, atributos y métodos de la clase
            </span>
          </div>
          <button type="button" className="close-btn" onClick={onClose} title="Cerrar">
            ✕
          </button>
        </header>

        {errorMessage && <div className="error" style={{ margin: '0.75rem 0' }}>{errorMessage}</div>}

        <form onSubmit={handleSubmit}>
          {/* Fila: Nombre, Tipo UML y Estereotipo */}
          <div className="form-group-row" style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: '0.75rem', marginBottom: '0.75rem' }}>
            <div className="form-field">
              <label style={{ fontWeight: 600, fontSize: '0.85rem' }}>Nombre de la Clase *</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Ej. Producto, Cliente, Pedido"
                required
                maxLength={120}
              />
            </div>
            <div className="form-field">
              <label style={{ fontWeight: 600, fontSize: '0.85rem' }}>Tipo UML</label>
              <select value={type} onChange={(e) => setType(e.target.value as TipoElementoUML)}>
                <option value="CLASE">Clase</option>
                <option value="INTERFAZ">&lt;&lt;interface&gt;&gt;</option>
                <option value="CLASE_ABSTRACTA">&lt;&lt;abstract&gt;&gt;</option>
                <option value="ENUMERACION">&lt;&lt;enum&gt;&gt;</option>
              </select>
            </div>
            <div className="form-field">
              <label style={{ fontWeight: 600, fontSize: '0.85rem' }}>Estereotipo (opcional)</label>
              <input
                type="text"
                value={stereotype}
                onChange={(e) => setStereotype(e.target.value)}
                placeholder="Ej. entity, service"
                maxLength={60}
              />
            </div>
          </div>

          {/* Campo Descripción del Elemento UML (Requerimiento 1) */}
          <div className="form-field" style={{ marginBottom: '1rem' }}>
            <label style={{ fontWeight: 600, fontSize: '0.85rem' }}>Descripción del elemento UML</label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Escribe aquí la descripción o propósito del elemento UML..."
              maxLength={500}
              style={{ width: '100%', padding: '0.5rem', borderRadius: '6px', border: '1px solid #cbd5e1', resize: 'vertical' }}
            />
          </div>

          {/* Sección de Atributos (Requerimientos 4 y 5) */}
          <div className="compartment-editor" style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <h4 style={{ margin: 0, fontSize: '0.95rem', color: '#1e293b' }}>
                Atributos ({attributes.length})
              </h4>
              <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Edita nombre, tipo y visibilidad directamente</span>
            </div>

            {/* Lista de atributos existentes editables */}
            <div className="items-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', maxHeight: '220px', overflowY: 'auto', marginBottom: '0.75rem' }}>
              {attributes.length === 0 ? (
                <p className="empty-hint" style={{ color: '#94a3b8', fontSize: '0.85rem', margin: '0.5rem 0' }}>
                  Sin atributos definidos. Puedes agregar nuevos abajo.
                </p>
              ) : (
                attributes.map((a) => (
                  <div
                    key={a.id}
                    className="item-edit-row"
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '70px 1.5fr 1.5fr 40px',
                      gap: '0.5rem',
                      alignItems: 'center',
                      background: '#ffffff',
                      padding: '0.4rem 0.6rem',
                      borderRadius: '6px',
                      border: '1px solid #e2e8f0'
                    }}
                  >
                    {/* Visibilidad */}
                    <select
                      value={a.visibility}
                      onChange={(e) => handleUpdateAttribute(a.id, 'visibility', e.target.value)}
                      title="Visibilidad"
                      style={{ padding: '0.35rem 0.4rem', fontSize: '0.85rem' }}
                    >
                      <option value="+">+ (public)</option>
                      <option value="-">- (private)</option>
                      <option value="#"># (protected)</option>
                      <option value="~">~ (package)</option>
                    </select>

                    {/* Nombre */}
                    <input
                      type="text"
                      value={a.name}
                      onChange={(e) => handleUpdateAttribute(a.id, 'name', e.target.value)}
                      placeholder="Nombre atributo"
                      required
                      style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
                    />

                    {/* Tipo con Menú Desplegable (Requerimiento 4) */}
                    <select
                      value={UML_DATA_TYPES.includes(a.type as any) ? a.type : '__CUSTOM__'}
                      onChange={(e) => {
                        const val = e.target.value
                        if (val === '__CUSTOM__') {
                          const custom = prompt('Ingresa el tipo de dato personalizado:', a.type)
                          if (custom && custom.trim()) {
                            handleUpdateAttribute(a.id, 'type', custom.trim())
                          }
                        } else {
                          handleUpdateAttribute(a.id, 'type', val)
                        }
                      }}
                      title="Tipo de dato"
                      style={{ padding: '0.35rem 0.4rem', fontSize: '0.85rem' }}
                    >
                      {!UML_DATA_TYPES.includes(a.type as any) && (
                        <option value="__CUSTOM__">{a.type} (Personalizado)</option>
                      )}
                      {UML_DATA_TYPES.map((dt) => (
                        <option key={dt} value={dt}>
                          {dt}
                        </option>
                      ))}
                      <option value="__CUSTOM__">+ Otro tipo personalizado...</option>
                    </select>

                    {/* Botón Eliminar Atributo */}
                    <button
                      type="button"
                      className="btn-icon danger"
                      onClick={() => handleRemoveAttribute(a.id)}
                      title="Eliminar atributo"
                      style={{ padding: '0.3rem', width: '32px', height: '32px' }}
                    >
                      ✕
                    </button>
                  </div>
                ))
              )}
            </div>

            {/* Agregar nuevo atributo */}
            <div
              className="inline-add"
              style={{
                display: 'grid',
                gridTemplateColumns: '70px 1.5fr 1.5fr auto',
                gap: '0.5rem',
                alignItems: 'center',
                background: '#f1f5f9',
                padding: '0.5rem 0.6rem',
                borderRadius: '6px'
              }}
            >
              <select
                value={newAttrVis}
                onChange={(e) => setNewAttrVis(e.target.value as UmlVisibility)}
                style={{ padding: '0.35rem 0.4rem', fontSize: '0.85rem' }}
              >
                <option value="+">+</option>
                <option value="-">-</option>
                <option value="#">#</option>
                <option value="~">~</option>
              </select>

              <input
                type="text"
                placeholder="Nuevo atributo (ej. precio)"
                value={newAttrName}
                onChange={(e) => setNewAttrName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    handleAddAttribute()
                  }
                }}
                style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
              />

              {!isCustomType ? (
                <select
                  value={newAttrType}
                  onChange={(e) => {
                    if (e.target.value === '__OTHER__') {
                      setIsCustomType(true)
                    } else {
                      setNewAttrType(e.target.value)
                    }
                  }}
                  style={{ padding: '0.35rem 0.4rem', fontSize: '0.85rem' }}
                >
                  {UML_DATA_TYPES.map((dt) => (
                    <option key={dt} value={dt}>
                      {dt}
                    </option>
                  ))}
                  <option value="__OTHER__">+ Otro tipo...</option>
                </select>
              ) : (
                <div style={{ display: 'flex', gap: '4px' }}>
                  <input
                    type="text"
                    placeholder="Tipo personalizado"
                    value={customAttrType}
                    onChange={(e) => setCustomAttrType(e.target.value)}
                    style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
                  />
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => setIsCustomType(false)}
                    style={{ padding: '0.2rem 0.4rem', fontSize: '0.75rem' }}
                  >
                    ↺
                  </button>
                </div>
              )}

              <button
                type="button"
                className="secondary"
                onClick={handleAddAttribute}
                style={{ padding: '0.35rem 0.75rem', fontSize: '0.85rem', whiteSpace: 'nowrap' }}
              >
                + Atributo
              </button>
            </div>
          </div>

          {/* Sección de Operaciones / Métodos */}
          <div className="compartment-editor" style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <h4 style={{ margin: 0, fontSize: '0.95rem', color: '#1e293b' }}>
                Operaciones / Métodos ({methods.length})
              </h4>
              <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Edita nombre, parámetros y retorno</span>
            </div>

            <div className="items-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', maxHeight: '180px', overflowY: 'auto', marginBottom: '0.75rem' }}>
              {methods.length === 0 ? (
                <p className="empty-hint" style={{ color: '#94a3b8', fontSize: '0.85rem', margin: '0.5rem 0' }}>
                  Sin operaciones definidas.
                </p>
              ) : (
                methods.map((m) => (
                  <div
                    key={m.id}
                    className="item-edit-row"
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '70px 1.5fr 1.5fr 1fr 40px',
                      gap: '0.5rem',
                      alignItems: 'center',
                      background: '#ffffff',
                      padding: '0.4rem 0.6rem',
                      borderRadius: '6px',
                      border: '1px solid #e2e8f0'
                    }}
                  >
                    <select
                      value={m.visibility}
                      onChange={(e) => handleUpdateMethod(m.id, 'visibility', e.target.value)}
                      style={{ padding: '0.35rem 0.4rem', fontSize: '0.85rem' }}
                    >
                      <option value="+">+</option>
                      <option value="-">-</option>
                      <option value="#">#</option>
                      <option value="~">~</option>
                    </select>
                    <input
                      type="text"
                      value={m.name}
                      onChange={(e) => handleUpdateMethod(m.id, 'name', e.target.value)}
                      placeholder="Nombre método"
                      style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
                    />
                    <input
                      type="text"
                      value={m.parameters}
                      onChange={(e) => handleUpdateMethod(m.id, 'parameters', e.target.value)}
                      placeholder="Parámetros"
                      style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
                    />
                    <input
                      type="text"
                      value={m.returnType}
                      onChange={(e) => handleUpdateMethod(m.id, 'returnType', e.target.value)}
                      placeholder="Retorno"
                      style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
                    />
                    <button
                      type="button"
                      className="btn-icon danger"
                      onClick={() => handleRemoveMethod(m.id)}
                      title="Eliminar método"
                      style={{ padding: '0.3rem', width: '32px', height: '32px' }}
                    >
                      ✕
                    </button>
                  </div>
                ))
              )}
            </div>

            <div
              className="inline-add"
              style={{
                display: 'grid',
                gridTemplateColumns: '70px 1.5fr 1.5fr 1fr auto',
                gap: '0.5rem',
                alignItems: 'center',
                background: '#f1f5f9',
                padding: '0.5rem 0.6rem',
                borderRadius: '6px'
              }}
            >
              <select
                value={newMethodVis}
                onChange={(e) => setNewMethodVis(e.target.value as UmlVisibility)}
                style={{ padding: '0.35rem 0.4rem', fontSize: '0.85rem' }}
              >
                <option value="+">+</option>
                <option value="-">-</option>
                <option value="#">#</option>
                <option value="~">~</option>
              </select>
              <input
                type="text"
                placeholder="Nuevo método (ej. calcular)"
                value={newMethodName}
                onChange={(e) => setNewMethodName(e.target.value)}
                style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
              />
              <input
                type="text"
                placeholder="Parámetros (ej. monto: Double)"
                value={newMethodParams}
                onChange={(e) => setNewMethodParams(e.target.value)}
                style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
              />
              <input
                type="text"
                placeholder="Retorno (ej. void)"
                value={newMethodReturn}
                onChange={(e) => setNewMethodReturn(e.target.value)}
                style={{ padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
              />
              <button
                type="button"
                className="secondary"
                onClick={handleAddMethod}
                style={{ padding: '0.35rem 0.75rem', fontSize: '0.85rem', whiteSpace: 'nowrap' }}
              >
                + Método
              </button>
            </div>
          </div>

          <footer className="uml-modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <button type="button" className="secondary" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit">
              Guardar Cambios
            </button>
          </footer>
        </form>
      </div>
    </div>
  )
}
