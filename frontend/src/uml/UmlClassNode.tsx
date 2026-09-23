import { memo } from 'react'
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react'
import { TipoElementoUML, parseUmlProperties } from './types'

export type UmlNodeData = {
  elementId: string
  name: string
  elementType: TipoElementoUML
  propertiesJson: string
  readOnly?: boolean
  onEdit?: (id: string) => void
  onDelete?: (id: string) => void
}

export type UmlNodeType = Node<UmlNodeData, 'umlClass'>

function UmlClassNodeComponent({ data, selected }: NodeProps<UmlNodeType>) {
  const { attributes, methods, description, stereotype: customStereotype } = parseUmlProperties(data.propertiesJson)

  const stereotype =
    customStereotype && customStereotype.trim()
      ? (customStereotype.startsWith('«') ? customStereotype : `«${customStereotype}»`)
      : data.elementType === 'INTERFAZ'
      ? '«interface»'
      : data.elementType === 'CLASE_ABSTRACTA'
      ? '«abstract»'
      : data.elementType === 'ENUMERACION'
      ? '«enum»'
      : null

  const isAbstract = data.elementType === 'CLASE_ABSTRACTA'

  return (
    <div className={`uml-node-card ${selected ? 'selected' : ''} ${data.elementType.toLowerCase()}`}>
      {/* Handles para conexiones de relaciones UML */}
      <Handle type="target" position={Position.Top} id="top-target" />
      <Handle type="source" position={Position.Top} id="top-source" />

      <Handle type="target" position={Position.Right} id="right-target" />
      <Handle type="source" position={Position.Right} id="right-source" />

      <Handle type="target" position={Position.Bottom} id="bottom-target" />
      <Handle type="source" position={Position.Bottom} id="bottom-source" />

      <Handle type="target" position={Position.Left} id="left-target" />
      <Handle type="source" position={Position.Left} id="left-source" />

      {/* Cabecera del elemento UML */}
      <div className="uml-node-header">
        {stereotype && <div className="uml-stereotype">{stereotype}</div>}
        <div className={`uml-node-title ${isAbstract ? 'italic' : ''}`}>{data.name}</div>
        {description && description.trim() && (
          <div className="uml-node-description" title={description}>
            {description}
          </div>
        )}

        {!data.readOnly && (
          <div className="uml-node-actions nodrag">
            <button
              type="button"
              className="uml-btn-tiny"
              title="Editar atributos y operaciones"
              onClick={(e) => {
                e.stopPropagation()
                data.onEdit?.(data.elementId)
              }}
            >
              ✎
            </button>
            <button
              type="button"
              className="uml-btn-tiny danger"
              title="Eliminar elemento"
              onClick={(e) => {
                e.stopPropagation()
                data.onDelete?.(data.elementId)
              }}
            >
              ✕
            </button>
          </div>
        )}
      </div>

      {/* Compartimento de Atributos */}
      <div className="uml-node-compartment">
        {attributes.length === 0 ? (
          <div className="uml-empty-line"> </div>
        ) : (
          attributes.map((attr) => (
            <div key={attr.id} className="uml-attribute-item">
              <span className="vis">{attr.visibility}</span> {attr.name}:{' '}
              <span className="type">{attr.type}</span>
            </div>
          ))
        )}
      </div>

      {/* Compartimento de Métodos / Operaciones */}
      <div className="uml-node-compartment">
        {methods.length === 0 ? (
          <div className="uml-empty-line"> </div>
        ) : (
          methods.map((method) => {
            const cleanName = (method.name || '').replace(/\(\s*\)$/, '')
            return (
              <div key={method.id} className="uml-method-item">
                <span className="vis">{method.visibility}</span> {cleanName}({method.parameters || ''}):{' '}
                <span className="type">{method.returnType}</span>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}

export const UmlClassNode = memo(UmlClassNodeComponent)
