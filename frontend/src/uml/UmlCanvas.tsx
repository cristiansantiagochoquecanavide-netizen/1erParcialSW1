import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  Connection,
  Edge,
  BackgroundVariant,
  MarkerType
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import {
  DiagramFullView,
  DiagramSummary,
  ElementDetail,
  RelationDetail,
  Role,
  TipoElementoUML,
  TipoRelacionUML,
  UmlProperties,
  ValidationReport,
  RelationalSchema
} from './types'
import { UmlClassNode, UmlNodeType } from './UmlClassNode'
import { ClassEditorModal } from './ClassEditorModal'
import { RelationModal } from './RelationModal'
import { ValidationModal } from './ValidationModal'
import { RelationalModal } from './RelationalModal'
import { CodeGeneratorModal } from './CodeGeneratorModal'
import { AiAssistantModal } from './AiAssistantModal'
import { VersionsModal } from './VersionsModal'
import { XmiModal } from './XmiModal'
import { ApiTesterModal } from './ApiTesterModal'

const base = import.meta.env.VITE_API_BASE_URL !== undefined ? import.meta.env.VITE_API_BASE_URL : ''
const NODE_TYPES = { umlClass: UmlClassNode }

async function request<T>(path: string, token: string, options: RequestInit = {}): Promise<T> {
  const url = base ? `${base}${path}` : path
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers
    }
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message ?? 'Error al comunicar con el servidor.')
  }
  return response.status === 204 ? (undefined as T) : (response.json() as Promise<T>)
}

type Props = {
  project: { id: string; name: string; role: Role }
  auth: { token: string; name: string; email: string }
  onError: (e: unknown) => void
}

export function UmlCanvas({ project, auth, onError }: Props) {
  const isReadOnly = project.role === 'LECTOR'

  const [diagram, setDiagram] = useState<DiagramSummary | null>(null)
  const [elementsRaw, setElementsRaw] = useState<ElementDetail[]>([])
  const [relationsRaw, setRelationsRaw] = useState<RelationDetail[]>([])

  const [nodes, setNodes, onNodesChange] = useNodesState<UmlNodeType>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])

  const [savingStatus, setSavingStatus] = useState<string>('Guardado')

  // Modales
  const [editingElement, setEditingElement] = useState<ElementDetail | null>(null)
  const [pendingConnection, setPendingConnection] = useState<Connection | null>(null)
  const [editingRelation, setEditingRelation] = useState<RelationDetail | null>(null)
  const [validationReport, setValidationReport] = useState<ValidationReport | null>(null)
  const [relationalSchema, setRelationalSchema] = useState<RelationalSchema | null>(null)
  const [validating, setValidating] = useState(false)
  const [generatingRelational, setGeneratingRelational] = useState(false)

  const [showGenerator, setShowGenerator] = useState(false)
  const [showAi, setShowAi] = useState(false)
  const [showVersions, setShowVersions] = useState(false)
  const [showXmi, setShowXmi] = useState(false)
  const [showApiTester, setShowApiTester] = useState(false)

  // Referencias mutables para evitar dependencias circulares en callbacks y efectos
  const elementsRef = useRef(elementsRaw)
  elementsRef.current = elementsRaw
  const diagramRef = useRef(diagram)
  diagramRef.current = diagram

  // Callback para abrir edición de elemento (referencia estable)
  const handleEditElement = useCallback((elementId: string) => {
    const el = elementsRef.current.find((e) => e.id === elementId)
    if (el) setEditingElement(el)
  }, [])

  // Callback para eliminar elemento (referencia estable sin dependencia de elementsRaw)
  const handleDeleteElement = useCallback(
    async (elementId: string) => {
      const currentDiagram = diagramRef.current
      if (isReadOnly || !currentDiagram) return
      const el = elementsRef.current.find((e) => e.id === elementId)
      if (!confirm(`¿Eliminar elemento «${el?.nombre ?? ''}» y sus relaciones?`)) return
      try {
        setSavingStatus('Guardando...')
        await request(`/api/projects/${project.id}/diagrams/${currentDiagram.id}/elements/${elementId}`, auth.token, {
          method: 'DELETE'
        })
        setElementsRaw((prev) => prev.filter((e) => e.id !== elementId))
        setRelationsRaw((prev) =>
          prev.filter((r) => r.elementoOrigen !== elementId && r.elementoDestino !== elementId)
        )
        setSavingStatus('Guardado')
      } catch (err) {
        setSavingStatus('Error')
        onError(err)
      }
    },
    [isReadOnly, project.id, auth.token, onError]
  )

  // Transformar datos del backend a nodos de React Flow
  const buildNodes = useCallback(
    (elements: ElementDetail[]): UmlNodeType[] => {
      return elements.map((el) => ({
        id: el.id,
        type: 'umlClass',
        position: { x: el.posicionX, y: el.posicionY },
        data: {
          elementId: el.id,
          name: el.nombre,
          elementType: el.tipoElemento,
          propertiesJson: el.propiedadesJson,
          readOnly: isReadOnly,
          onEdit: handleEditElement,
          onDelete: handleDeleteElement
        }
      }))
    },
    [isReadOnly, handleEditElement, handleDeleteElement]
  )

  // Transformar datos de relaciones a aristas de React Flow
  const buildEdges = useCallback((relations: RelationDetail[]): Edge[] => {
    return relations.map((rel) => {
      const isHeritage = rel.tipoRelacion === 'HERENCIA'
      const isDependency = rel.tipoRelacion === 'DEPENDENCIA'
      const isAggregation = rel.tipoRelacion === 'AGREGACION'
      const isComposition = rel.tipoRelacion === 'COMPOSICION'

      const label = [
        rel.nombreRelacion ? `«${rel.nombreRelacion}»` : '',
        rel.multiplicidadOrigen && rel.multiplicidadDestino
          ? `${rel.multiplicidadOrigen} .. ${rel.multiplicidadDestino}`
          : rel.multiplicidadOrigen || rel.multiplicidadDestino || ''
      ]
        .filter(Boolean)
        .join(' ')

      return {
        id: rel.id,
        source: rel.elementoOrigen,
        target: rel.elementoDestino,
        label: label || undefined,
        animated: isDependency,
        style: {
          stroke: isComposition ? '#1e293b' : isAggregation ? '#0284c7' : '#475569',
          strokeWidth: isComposition || isAggregation ? 2.5 : 2,
          strokeDasharray: isDependency ? '5,5' : undefined
        },
        markerEnd: isHeritage
          ? { type: MarkerType.ArrowClosed, color: '#475569', width: 22, height: 22 }
          : isDependency
          ? { type: MarkerType.Arrow, color: '#475569' }
          : undefined,
        markerStart: isComposition
          ? { type: MarkerType.ArrowClosed, color: '#1e293b' }
          : undefined
      }
    })
  }, [])

  const handleFullDiagramUpdated = useCallback(
    (full: DiagramFullView) => {
      setDiagram(full.diagram)
      setElementsRaw(full.elements)
      setRelationsRaw(full.relations)
      setNodes(buildNodes(full.elements))
      setEdges(buildEdges(full.relations))
    },
    [buildNodes, buildEdges, setNodes, setEdges]
  )

  // Carga inicial única del diagrama al montar el componente
  useEffect(() => {
    let isMounted = true
    async function init() {
      try {
        setSavingStatus('Cargando...')
        const def = await request<DiagramSummary>(
          `/api/projects/${project.id}/diagrams/default`,
          auth.token
        )
        if (!isMounted) return
        setDiagram(def)

        const full = await request<DiagramFullView>(
          `/api/projects/${project.id}/diagrams/${def.id}`,
          auth.token
        )
        if (!isMounted) return
        setElementsRaw(full.elements)
        setRelationsRaw(full.relations)
        setNodes(buildNodes(full.elements))
        setEdges(buildEdges(full.relations))
        setSavingStatus('Guardado')
      } catch (err) {
        if (!isMounted) return
        setSavingStatus('Error al cargar')
        onError(err)
      }
    }

    init()
    return () => {
      isMounted = false
    }
  }, [project.id, auth.token, buildNodes, buildEdges, setNodes, setEdges, onError])

  // Sincronizar nodos locales cuando cambian elementsRaw
  useEffect(() => {
    setNodes(buildNodes(elementsRaw))
  }, [elementsRaw, buildNodes, setNodes])

  // Sincronizar aristas locales cuando cambian relationsRaw
  useEffect(() => {
    setEdges(buildEdges(relationsRaw))
  }, [relationsRaw, buildEdges, setEdges])

  // Al finalizar de arrastrar un nodo, persistir coordenadas en backend
  const handleNodeDragStop = useCallback(
    async (_event: unknown, node: UmlNodeType) => {
      const currentDiagram = diagramRef.current
      if (isReadOnly || !currentDiagram) return
      try {
        setSavingStatus('Sincronizando...')
        await request(
          `/api/projects/${project.id}/diagrams/${currentDiagram.id}/elements/positions`,
          auth.token,
          {
            method: 'POST',
            body: JSON.stringify({
              positions: [{ id: node.id, posicionX: node.position.x, posicionY: node.position.y }]
            })
          }
        )
        setElementsRaw((prev) =>
          prev.map((e) =>
            e.id === node.id ? { ...e, posicionX: node.position.x, posicionY: node.position.y } : e
          )
        )
        setSavingStatus('Guardado')
      } catch (err) {
        setSavingStatus('Error al guardar')
        onError(err)
      }
    },
    [isReadOnly, project.id, auth.token, onError]
  )

  // Crear nuevo elemento en el centro
  async function handleAddElement(tipo: TipoElementoUML = 'CLASE') {
    if (isReadOnly || !diagram) return
    const nombreDefecto =
      tipo === 'INTERFAZ'
        ? 'NuevaInterfaz'
        : tipo === 'CLASE_ABSTRACTA'
        ? 'NuevaClaseAbstracta'
        : tipo === 'ENUMERACION'
        ? 'NuevaEnumeracion'
        : 'NuevaClase'

    const randomOffset = Math.floor(Math.random() * 80)
    const nuevo = {
      tipoElemento: tipo,
      nombre: nombreDefecto,
      posicionX: 180 + randomOffset,
      posicionY: 120 + randomOffset,
      propiedadesJson: JSON.stringify({
        attributes: [{ id: crypto.randomUUID(), visibility: '+', name: 'id', type: 'UUID' }],
        methods: []
      })
    }

    try {
      setSavingStatus('Guardando...')
      const saved = await request<ElementDetail>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/elements`,
        auth.token,
        {
          method: 'POST',
          body: JSON.stringify(nuevo)
        }
      )
      setElementsRaw((prev) => [...prev, saved])
      setSavingStatus('Guardado')
    } catch (err) {
      setSavingStatus('Error')
      onError(err)
    }
  }

  // Guardar cambios del modal de elemento
  async function handleSaveElementDetails(
    nombre: string,
    tipoElemento: TipoElementoUML,
    propiedades: UmlProperties
  ) {
    if (!editingElement || !diagram) return
    try {
      setSavingStatus('Guardando...')
      const payload = {
        id: editingElement.id,
        tipoElemento,
        nombre,
        posicionX: editingElement.posicionX,
        posicionY: editingElement.posicionY,
        propiedadesJson: JSON.stringify(propiedades)
      }

      const saved = await request<ElementDetail>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/elements`,
        auth.token,
        {
          method: 'POST',
          body: JSON.stringify(payload)
        }
      )

      setElementsRaw((prev) => prev.map((e) => (e.id === saved.id ? saved : e)))
      setEditingElement(null)
      setSavingStatus('Guardado')
    } catch (err) {
      setSavingStatus('Error')
      onError(err)
    }
  }

  // Conexión arrastrada entre dos nodos
  const handleConnect = useCallback(
    (connection: Connection) => {
      if (isReadOnly) return
      if (connection.source && connection.target && connection.source !== connection.target) {
        setPendingConnection(connection)
      }
    },
    [isReadOnly]
  )

  // Guardar nueva relación desde el modal
  async function handleSavePendingRelation(
    tipoRelacion: TipoRelacionUML,
    nombreRelacion: string | null,
    multiplicidadOrigen: string | null,
    multiplicidadDestino: string | null
  ) {
    if (!pendingConnection || !diagram) return
    try {
      setSavingStatus('Guardando relación...')
      const payload = {
        elementoOrigen: pendingConnection.source,
        elementoDestino: pendingConnection.target,
        nombreRelacion,
        tipoRelacion,
        multiplicidadOrigen,
        multiplicidadDestino
      }

      const saved = await request<RelationDetail>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/relations`,
        auth.token,
        {
          method: 'POST',
          body: JSON.stringify(payload)
        }
      )

      setRelationsRaw((prev) => [...prev, saved])
      setPendingConnection(null)
      setSavingStatus('Guardado')
    } catch (err) {
      setSavingStatus('Error')
      onError(err)
    }
  }

  // Clic sobre una relación para editar o eliminar
  function handleEdgeClick(_event: React.MouseEvent, edge: Edge) {
    const rel = relationsRaw.find((r) => r.id === edge.id)
    if (rel) setEditingRelation(rel)
  }

  // Guardar edición de relación existente
  async function handleUpdateRelation(
    tipoRelacion: TipoRelacionUML,
    nombreRelacion: string | null,
    multiplicidadOrigen: string | null,
    multiplicidadDestino: string | null
  ) {
    if (!editingRelation || !diagram) return
    try {
      setSavingStatus('Actualizando relación...')
      const payload = {
        id: editingRelation.id,
        elementoOrigen: editingRelation.elementoOrigen,
        elementoDestino: editingRelation.elementoDestino,
        nombreRelacion,
        tipoRelacion,
        multiplicidadOrigen,
        multiplicidadDestino
      }

      const saved = await request<RelationDetail>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/relations`,
        auth.token,
        {
          method: 'POST',
          body: JSON.stringify(payload)
        }
      )

      setRelationsRaw((prev) => prev.map((r) => (r.id === saved.id ? saved : r)))
      setEditingRelation(null)
      setSavingStatus('Guardado')
    } catch (err) {
      setSavingStatus('Error')
      onError(err)
    }
  }

  // Eliminar relación
  async function handleDeleteRelation() {
    if (!editingRelation || !diagram) return
    try {
      setSavingStatus('Eliminando relación...')
      await request(
        `/api/projects/${project.id}/diagrams/${diagram.id}/relations/${editingRelation.id}`,
        auth.token,
        {
          method: 'DELETE'
        }
      )
      setRelationsRaw((prev) => prev.filter((r) => r.id !== editingRelation.id))
      setEditingRelation(null)
      setSavingStatus('Guardado')
    } catch (err) {
      setSavingStatus('Error')
      onError(err)
    }
  }

  // Nombres para los modales de relaciones
  const sourceName = useMemo(() => {
    const sourceId = pendingConnection?.source ?? editingRelation?.elementoOrigen
    return elementsRaw.find((e) => e.id === sourceId)?.nombre ?? 'Elemento'
  }, [pendingConnection, editingRelation, elementsRaw])

  const targetName = useMemo(() => {
    const targetId = pendingConnection?.target ?? editingRelation?.elementoDestino
    return elementsRaw.find((e) => e.id === targetId)?.nombre ?? 'Elemento'
  }, [pendingConnection, editingRelation, elementsRaw])

  async function handleValidateDiagram() {
    if (!diagram) return
    try {
      setValidating(true)
      const report = await request<ValidationReport>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/validate`,
        auth.token
      )
      setValidationReport(report)
    } catch (err) {
      onError(err)
    } finally {
      setValidating(false)
    }
  }

  async function handleGenerateRelational() {
    if (!diagram) return
    try {
      setGeneratingRelational(true)
      const schema = await request<RelationalSchema>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/relational`,
        auth.token
      )
      setRelationalSchema(schema)
    } catch (err) {
      onError(err)
    } finally {
      setGeneratingRelational(false)
    }
  }

  return (
    <div className="uml-workspace-container">
      {/* Barra de herramientas superior */}
      <div className="uml-toolbar">
        <div className="uml-toolbar-left">
          <span className="uml-diagram-title">{diagram?.nombre ?? 'Cargando diagrama...'}</span>
          <span className={`status-pill ${savingStatus === 'Error' ? 'error' : ''}`}>
            {savingStatus}
          </span>
          {isReadOnly && <span className="readonly-badge">Modo Lectura</span>}
        </div>

        <div className="uml-toolbar-actions">
          {/* Botones de Modelado e Ingeniería (CU05 y CU06) */}
          <button
            type="button"
            className="uml-tool-btn feature-btn"
            disabled={validating}
            onClick={handleValidateDiagram}
            title="Validar consistencia y reglas semánticas del modelo UML"
          >
            {validating ? 'Validando...' : '🔍 Validar Modelo'}
          </button>
          <button
            type="button"
            className="uml-tool-btn feature-btn primary"
            disabled={generatingRelational}
            onClick={handleGenerateRelational}
            title="Transformar diagrama a tablas SQL y PostgreSQL DDL"
          >
            {generatingRelational ? 'Generando...' : '🗄️ Modelo Relacional'}
          </button>

          <button
            type="button"
            className="uml-tool-btn feature-btn"
            style={{ background: '#7c3aed', color: 'white' }}
            onClick={() => setShowAi(true)}
            title="Asistente de IA local para modelado por voz y comandos"
          >
            🤖 IA & Voz
          </button>

          <button
            type="button"
            className="uml-tool-btn feature-btn"
            style={{ background: '#16a34a', color: 'white' }}
            onClick={() => setShowGenerator(true)}
            title="Generar Backend Spring Boot y Frontend React en archivo ZIP"
          >
            ⚡ Generar Software
          </button>

          <button
            type="button"
            className="uml-tool-btn feature-btn"
            onClick={() => setShowVersions(true)}
            title="Historial de versiones y snapshots del diagrama"
          >
            🕒 Versiones
          </button>

          <button
            type="button"
            className="uml-tool-btn feature-btn"
            onClick={() => setShowXmi(true)}
            title="Exportar/Importar XMI para Enterprise Architect"
          >
            📦 XMI
          </button>

          <button
            type="button"
            className="uml-tool-btn feature-btn"
            onClick={() => setShowApiTester(true)}
            title="Probar endpoints de la API generada por REST o voz"
          >
            🧪 Probar API
          </button>

          {!isReadOnly && (
            <>
              <span className="toolbar-divider" />
              <button
                type="button"
                className="uml-tool-btn"
                onClick={() => handleAddElement('CLASE')}
              >
                + Clase
              </button>
              <button
                type="button"
                className="uml-tool-btn secondary"
                onClick={() => handleAddElement('INTERFAZ')}
              >
                + Interfaz
              </button>
              <button
                type="button"
                className="uml-tool-btn secondary"
                onClick={() => handleAddElement('CLASE_ABSTRACTA')}
              >
                + Abstracta
              </button>
              <button
                type="button"
                className="uml-tool-btn secondary"
                onClick={() => handleAddElement('ENUMERACION')}
              >
                + Enum
              </button>
            </>
          )}
        </div>
      </div>

      {/* Contenedor React Flow */}
      <div className="uml-canvas-viewport">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={handleConnect}
          onNodeDragStop={handleNodeDragStop}
          onEdgeClick={handleEdgeClick}
          nodeTypes={NODE_TYPES}
          nodesDraggable={!isReadOnly}
          nodesConnectable={!isReadOnly}
          elementsSelectable={true}
          fitView
        >
          <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="#cbd5e1" />
          <Controls />
          <MiniMap nodeStrokeWidth={3} zoomable pannable />
        </ReactFlow>
      </div>

      {/* Modal de edición detallada de clase */}
      {editingElement && (
        <ClassEditorModal
          initialName={editingElement.nombre}
          initialType={editingElement.tipoElemento}
          initialPropertiesJson={editingElement.propiedadesJson}
          onSave={handleSaveElementDetails}
          onClose={() => setEditingElement(null)}
        />
      )}

      {/* Modal para configurar nueva relación */}
      {pendingConnection && (
        <RelationModal
          sourceName={sourceName}
          targetName={targetName}
          onSave={handleSavePendingRelation}
          onClose={() => setPendingConnection(null)}
        />
      )}

      {/* Modal para modificar o borrar relación existente */}
      {editingRelation && (
        <RelationModal
          initialType={editingRelation.tipoRelacion}
          initialName={editingRelation.nombreRelacion}
          initialMultOrigin={editingRelation.multiplicidadOrigen}
          initialMultTarget={editingRelation.multiplicidadDestino}
          sourceName={sourceName}
          targetName={targetName}
          onSave={handleUpdateRelation}
          onDelete={handleDeleteRelation}
          onClose={() => setEditingRelation(null)}
        />
      )}

      {/* Modal de Validación UML (CU05) */}
      {validationReport && (
        <ValidationModal
          report={validationReport}
          onProceedToRelational={handleGenerateRelational}
          onClose={() => setValidationReport(null)}
        />
      )}

      {/* Modal de Modelo Relacional Generado (CU06) */}
      {relationalSchema && (
        <RelationalModal
          schema={relationalSchema}
          projectName={project.name}
          onClose={() => setRelationalSchema(null)}
        />
      )}

      {/* Modal Generador de Software (CU14, CU15, CU16) */}
      {showGenerator && diagram && (
        <CodeGeneratorModal
          projectName={project.name}
          diagramId={diagram.id}
          projectId={project.id}
          token={auth.token}
          onClose={() => setShowGenerator(false)}
          onError={onError}
        />
      )}

      {/* Modal Asistente IA y Voz (CU10, CU11, CU12) */}
      {showAi && diagram && (
        <AiAssistantModal
          projectId={project.id}
          diagramId={diagram.id}
          token={auth.token}
          onClose={() => setShowAi(false)}
          onDiagramUpdated={handleFullDiagramUpdated}
          onError={onError}
        />
      )}

      {/* Modal de Versiones (CU09) */}
      {showVersions && diagram && (
        <VersionsModal
          projectId={project.id}
          diagramId={diagram.id}
          token={auth.token}
          isReadOnly={isReadOnly}
          onClose={() => setShowVersions(false)}
          onRestored={handleFullDiagramUpdated}
          onError={onError}
        />
      )}

      {/* Modal de Interoperabilidad XMI (CU13) */}
      {showXmi && diagram && (
        <XmiModal
          projectId={project.id}
          diagramId={diagram.id}
          diagramName={diagram.nombre}
          token={auth.token}
          isReadOnly={isReadOnly}
          onClose={() => setShowXmi(false)}
          onDiagramUpdated={handleFullDiagramUpdated}
          onError={onError}
        />
      )}

      {/* Consola de Pruebas de API */}
      {showApiTester && (
        <ApiTesterModal
          elements={elementsRaw}
          onClose={() => setShowApiTester(false)}
        />
      )}
    </div>
  )
}
