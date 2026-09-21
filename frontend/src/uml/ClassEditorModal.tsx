import { useState } from 'react'
import {
  TipoElementoUML,
  UmlAttribute,
  UmlMethod,
  UmlProperties,
  UmlVisibility,
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
  const [attributes, setAttributes] = useState<UmlAttribute[]>(initialProps.attributes)
  const [methods, setMethods] = useState<UmlMethod[]>(initialProps.methods)

  // Nuevo atributo
  const [newAttrVis, setNewAttrVis] = useState<UmlVisibility>('+')
  const [newAttrName, setNewAttrName] = useState('')
  const [newAttrType, setNewAttrType] = useState('String')

  // Nuevo método
  const [newMethodVis, setNewMethodVis] = useState<UmlVisibility>('+')
  const [newMethodName, setNewMethodName] = useState('')
  const [newMethodParams, setNewMethodParams] = useState('')
  const [newMethodReturn, setNewMethodReturn] = useState('void')

  function handleAddAttribute() {
    if (!newAttrName.trim()) return
    const attr: UmlAttribute = {
      id: crypto.randomUUID(),
      visibility: newAttrVis,
      name: newAttrName.trim(),
      type: newAttrType.trim() || 'String'
    }
    setAttributes([...attributes, attr])
    setNewAttrName('')
  }

  function handleRemoveAttribute(id: string) {
    setAttributes(attributes.filter(a => a.id !== id))
  }

  function handleAddMethod() {
    if (!newMethodName.trim()) return
    const m: UmlMethod = {
      id: crypto.randomUUID(),
      visibility: newMethodVis,
      name: newMethodName.trim(),
      parameters: newMethodParams.trim(),
      returnType: newMethodReturn.trim() || 'void'
    }
    setMethods([...methods, m])
    setNewMethodName('')
    setNewMethodParams('')
  }

  function handleRemoveMethod(id: string) {
    setMethods(methods.filter(m => m.id !== id))
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    onSave(name.trim(), type, { attributes, methods })
  }

  return (
    <div className="uml-modal-backdrop">
      <div className="uml-modal-card">
        <header className="uml-modal-header">
          <h3>Editar Elemento UML</h3>
          <button type="button" className="close-btn" onClick={onClose}>✕</button>
        </header>

        <form onSubmit={handleSubmit}>
          <div className="form-group-row">
            <div className="form-field flex-2">
              <label>Nombre del Elemento</label>
              <input
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="Ej. Usuario, Pedido, Factura"
                required
                maxLength={120}
              />
            </div>
            <div className="form-field flex-1">
              <label>Tipo UML</label>
              <select value={type} onChange={e => setType(e.target.value as TipoElementoUML)}>
                <option value="CLASE">Clase</option>
                <option value="INTERFAZ">&lt;&lt;interface&gt;&gt;</option>
                <option value="CLASE_ABSTRACTA">&lt;&lt;abstract&gt;&gt;</option>
                <option value="ENUMERACION">&lt;&lt;enum&gt;&gt;</option>
              </select>
            </div>
          </div>

          <div className="compartment-editor">
            <h4>Atributos ({attributes.length})</h4>
            <div className="items-list">
              {attributes.length === 0 ? (
                <p className="empty-hint">Sin atributos definidos.</p>
              ) : (
                attributes.map(a => (
                  <div key={a.id} className="item-row">
                    <span className="mono">{a.visibility} {a.name}: <strong>{a.type}</strong></span>
                    <button type="button" className="btn-icon danger" onClick={() => handleRemoveAttribute(a.id)}>✕</button>
                  </div>
                ))
              )}
            </div>
            <div className="inline-add">
              <select value={newAttrVis} onChange={e => setNewAttrVis(e.target.value as UmlVisibility)}>
                <option value="+">+ (public)</option>
                <option value="-">- (private)</option>
                <option value="#"># (protected)</option>
              </select>
              <input
                type="text"
                placeholder="Nombre atributo"
                value={newAttrName}
                onChange={e => setNewAttrName(e.target.value)}
              />
              <input
                type="text"
                placeholder="Tipo (ej. String, Long)"
                value={newAttrType}
                onChange={e => setNewAttrType(e.target.value)}
              />
              <button type="button" className="secondary" onClick={handleAddAttribute}>+ Atributo</button>
            </div>
          </div>

          <div className="compartment-editor">
            <h4>Operaciones / Métodos ({methods.length})</h4>
            <div className="items-list">
              {methods.length === 0 ? (
                <p className="empty-hint">Sin operaciones definidas.</p>
              ) : (
                methods.map(m => (
                  <div key={m.id} className="item-row">
                    <span className="mono">
                      {m.visibility} {m.name}({m.parameters}): <strong>{m.returnType}</strong>
                    </span>
                    <button type="button" className="btn-icon danger" onClick={() => handleRemoveMethod(m.id)}>✕</button>
                  </div>
                ))
              )}
            </div>
            <div className="inline-add">
              <select value={newMethodVis} onChange={e => setNewMethodVis(e.target.value as UmlVisibility)}>
                <option value="+">+ (public)</option>
                <option value="-">- (private)</option>
                <option value="#"># (protected)</option>
              </select>
              <input
                type="text"
                placeholder="Nombre método"
                value={newMethodName}
                onChange={e => setNewMethodName(e.target.value)}
              />
              <input
                type="text"
                placeholder="Parámetros (ej. id: UUID)"
                value={newMethodParams}
                onChange={e => setNewMethodParams(e.target.value)}
              />
              <input
                type="text"
                placeholder="Retorno (ej. void)"
                value={newMethodReturn}
                onChange={e => setNewMethodReturn(e.target.value)}
              />
              <button type="button" className="secondary" onClick={handleAddMethod}>+ Método</button>
            </div>
          </div>

          <footer className="uml-modal-footer">
            <button type="button" className="secondary" onClick={onClose}>Cancelar</button>
            <button type="submit">Guardar Cambios</button>
          </footer>
        </form>
      </div>
    </div>
  )
}
