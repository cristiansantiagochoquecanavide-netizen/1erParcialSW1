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
  MarkerType,
  SelectionMode
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

interface PendingSyncAction {
  id: string
  type: 'SAVE_ELEMENT' | 'DELETE_ELEMENT' | 'SAVE_POSITIONS' | 'SAVE_RELATION' | 'DELETE_RELATION'
  payload: any
  timestamp: number
}

function getOfflineQueue(diagramId: string): PendingSyncAction[] {
  try {
    const raw = localStorage.getItem(`case.pending_sync.${diagramId}`)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function setOfflineQueue(diagramId: string, queue: PendingSyncAction[]) {
  try {
    localStorage.setItem(`case.pending_sync.${diagramId}`, JSON.stringify(queue))
  } catch {
    // Quota exceeded
  }
}

function appendOfflineQueue(
  diagramId: string,
  action: Omit<PendingSyncAction, 'id' | 'timestamp'>
): number {
  const queue = getOfflineQueue(diagramId)
  queue.push({
    ...action,
    id: 'sync_' + Math.random().toString(36).slice(2, 9),
    timestamp: Date.now()
  })
  setOfflineQueue(diagramId, queue)
  return queue.length
}

function saveLocalDiagramSnapshot(
  diagramId: string,
  diagram: DiagramSummary | null,
  elements: ElementDetail[],
  relations: RelationDetail[]
) {
  try {
    localStorage.setItem(
      `case.offline.${diagramId}`,
      JSON.stringify({ diagram, elements, relations, savedAt: Date.now() })
    )
  } catch {
    // Quota exceeded
  }
}

function loadLocalDiagramSnapshot(
  diagramId: string
): { diagram: DiagramSummary | null; elements: ElementDetail[]; relations: RelationDetail[] } | null {
  try {
    const raw = localStorage.getItem(`case.offline.${diagramId}`)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
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
  const [isOnline, setIsOnline] = useState<boolean>(navigator.onLine)
  const [isSyncingQueue, setIsSyncingQueue] = useState<boolean>(false)
  const [pendingQueueCount, setPendingQueueCount] = useState<number>(0)

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
  const relationsRef = useRef(relationsRaw)
  relationsRef.current = relationsRaw
  const diagramRef = useRef(diagram)
  diagramRef.current = diagram
  const nodesRef = useRef(nodes)
  nodesRef.current = nodes

  // Callback para abrir edición de elemento (referencia estable)
  const handleEditElement = useCallback((elementId: string) => {
    const el = elementsRef.current.find((e) => e.id === elementId)
    if (el) setEditingElement(el)
  }, [])

  // Callback para eliminar elemento individual (con soporte Offline CU10)
  const handleDeleteElement = useCallback(
    async (elementId: string) => {
      const currentDiagram = diagramRef.current
      if (isReadOnly || !currentDiagram) return
      const el = elementsRef.current.find((e) => e.id === elementId)
      if (!confirm(`¿Deseas eliminar la clase «${el?.nombre ?? ''}» y sus relaciones?`)) return

      const nextElements = elementsRef.current.filter((e) => e.id !== elementId)
      const nextRelations = relationsRef.current.filter(
        (r) => r.elementoOrigen !== elementId && r.elementoDestino !== elementId
      )

      if (!navigator.onLine) {
        setElementsRaw(nextElements)
        setRelationsRaw(nextRelations)
        saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, nextRelations)
        const count = appendOfflineQueue(currentDiagram.id, {
          type: 'DELETE_ELEMENT',
          payload: { elementId }
        })
        setPendingQueueCount(count)
        setSavingStatus('Guardado localmente (Offline)')
        return
      }

      try {
        setSavingStatus('Guardando...')
        await request(`/api/projects/${project.id}/diagrams/${currentDiagram.id}/elements/${elementId}`, auth.token, {
          method: 'DELETE'
        })
        setElementsRaw(nextElements)
        setRelationsRaw(nextRelations)
        saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, nextRelations)
        setSavingStatus('Guardado')
      } catch (err) {
        setElementsRaw(nextElements)
        setRelationsRaw(nextRelations)
        saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, nextRelations)
        const count = appendOfflineQueue(currentDiagram.id, {
          type: 'DELETE_ELEMENT',
          payload: { elementId }
        })
        setPendingQueueCount(count)
        setSavingStatus('Guardado localmente (Offline)')
      }
    },
    [isReadOnly, project.id, auth.token]
  )

  // Eliminar elementos seleccionados en lote (Requerimiento 2, 10 y 13)
  const handleDeleteSelected = useCallback(async () => {
    const currentDiagram = diagramRef.current
    if (isReadOnly || !currentDiagram) return
    const selectedNodes = nodesRef.current.filter((n) => n.selected)
    if (selectedNodes.length === 0) return

    const names = selectedNodes
      .map((n) => n.data.name)
      .slice(0, 3)
      .join(', ') + (selectedNodes.length > 3 ? '...' : '')

    const promptText =
      selectedNodes.length === 1
        ? `¿Deseas eliminar la clase «${names}» y sus relaciones?`
        : `¿Deseas eliminar las ${selectedNodes.length} clases seleccionadas («${names}») y sus relaciones?`

    if (!confirm(promptText)) return

    const selectedIds = new Set(selectedNodes.map((n) => n.id))
    const nextElements = elementsRef.current.filter((e) => !selectedIds.has(e.id))
    const nextRelations = relationsRef.current.filter(
      (r) => !selectedIds.has(r.elementoOrigen) && !selectedIds.has(r.elementoDestino)
    )

    if (!navigator.onLine) {
      setElementsRaw(nextElements)
      setRelationsRaw(nextRelations)
      saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, nextRelations)
      let count = 0
      for (const n of selectedNodes) {
        count = appendOfflineQueue(currentDiagram.id, {
          type: 'DELETE_ELEMENT',
          payload: { elementId: n.id }
        })
      }
      setPendingQueueCount(count)
      setSavingStatus('Guardado localmente (Offline)')
      return
    }

    try {
      setSavingStatus('Eliminando seleccionados...')
      for (const n of selectedNodes) {
        await request(
          `/api/projects/${project.id}/diagrams/${currentDiagram.id}/elements/${n.id}`,
          auth.token,
          { method: 'DELETE' }
        )
      }
      setElementsRaw(nextElements)
      setRelationsRaw(nextRelations)
      saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, nextRelations)
      setSavingStatus('Guardado')
    } catch (err) {
      setElementsRaw(nextElements)
      setRelationsRaw(nextRelations)
      saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, nextRelations)
      let count = 0
      for (const n of selectedNodes) {
        count = appendOfflineQueue(currentDiagram.id, {
          type: 'DELETE_ELEMENT',
          payload: { elementId: n.id }
        })
      }
      setPendingQueueCount(count)
      setSavingStatus('Guardado localmente (Offline)')
    }
  }, [isReadOnly, project.id, auth.token])

  // Transformar datos del backend a nodos de React Flow preservando selección
  const buildNodes = useCallback(
    (elements: ElementDetail[], previousNodes?: UmlNodeType[]): UmlNodeType[] => {
      const selectedMap = new Map((previousNodes ?? nodesRef.current ?? []).map((n) => [n.id, !!n.selected]))
      return elements.map((el) => ({
        id: el.id,
        type: 'umlClass',
        position: { x: el.posicionX, y: el.posicionY },
        selected: selectedMap.get(el.id) ?? false,
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

  // Sincronizar cola de operaciones pendientes al reconectar a internet (CU10)
  const flushPendingQueue = useCallback(
    async (diagramId: string) => {
      const queue = getOfflineQueue(diagramId)
      if (queue.length === 0) return

      setIsSyncingQueue(true)
      setSavingStatus('Sincronizando...')

      for (const action of queue) {
        try {
          if (action.type === 'SAVE_ELEMENT') {
            await request(`/api/projects/${project.id}/diagrams/${diagramId}/elements`, auth.token, {
              method: 'POST',
              body: JSON.stringify(action.payload)
            })
          } else if (action.type === 'DELETE_ELEMENT') {
            await request(
              `/api/projects/${project.id}/diagrams/${diagramId}/elements/${action.payload.elementId}`,
              auth.token,
              { method: 'DELETE' }
            )
          } else if (action.type === 'SAVE_POSITIONS') {
            await request(
              `/api/projects/${project.id}/diagrams/${diagramId}/elements/positions`,
              auth.token,
              {
                method: 'POST',
                body: JSON.stringify(action.payload)
              }
            )
          } else if (action.type === 'SAVE_RELATION') {
            await request(`/api/projects/${project.id}/diagrams/${diagramId}/relations`, auth.token, {
              method: 'POST',
              body: JSON.stringify(action.payload)
            })
          } else if (action.type === 'DELETE_RELATION') {
            await request(
              `/api/projects/${project.id}/diagrams/${diagramId}/relations/${action.payload.relationId}`,
              auth.token,
              { method: 'DELETE' }
            )
          }
        } catch (itemErr) {
          console.warn('Error al sincronizar acción pendiente:', itemErr)
        }
      }

      setOfflineQueue(diagramId, [])
      setPendingQueueCount(0)

      try {
        const full = await request<DiagramFullView>(
          `/api/projects/${project.id}/diagrams/${diagramId}`,
          auth.token
        )
        setElementsRaw(full.elements)
        setRelationsRaw(full.relations)
        saveLocalDiagramSnapshot(diagramId, diagramRef.current, full.elements, full.relations)
        setSavingStatus('Guardado')
      } catch {
        setSavingStatus('Guardado localmente')
      } finally {
        setIsSyncingQueue(false)
      }
    },
    [project.id, auth.token]
  )

  // Escuchar eventos de conexión a internet (CU10 Requerimiento 7)
  useEffect(() => {
    function handleOnline() {
      setIsOnline(true)
      const currentDiagramId = diagramRef.current?.id
      if (currentDiagramId) {
        flushPendingQueue(currentDiagramId)
      }
    }

    function handleOffline() {
      setIsOnline(false)
      setSavingStatus('Sin conexión (Offline)')
    }

    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [flushPendingQueue])

  // Carga inicial única del diagrama al montar el componente (con soporte offline)
  useEffect(() => {
    let isMounted = true
    async function init() {
      try {
        setSavingStatus('Cargando...')
        if (!navigator.onLine) {
          const cachedDiagramId = localStorage.getItem(`case.last_diagram.${project.id}`)
          if (cachedDiagramId) {
            const cached = loadLocalDiagramSnapshot(cachedDiagramId)
            if (cached && isMounted) {
              setDiagram(cached.diagram)
              setElementsRaw(cached.elements)
              setRelationsRaw(cached.relations)
              setPendingQueueCount(getOfflineQueue(cachedDiagramId).length)
              setSavingStatus('Cargado offline')
              return
            }
          }
        }

        const def = await request<DiagramSummary>(
          `/api/projects/${project.id}/diagrams/default`,
          auth.token
        )
        if (!isMounted) return
        setDiagram(def)
        localStorage.setItem(`case.last_diagram.${project.id}`, def.id)

        const full = await request<DiagramFullView>(
          `/api/projects/${project.id}/diagrams/${def.id}`,
          auth.token
        )
        if (!isMounted) return
        setElementsRaw(full.elements)
        setRelationsRaw(full.relations)
        saveLocalDiagramSnapshot(def.id, def, full.elements, full.relations)
        setPendingQueueCount(getOfflineQueue(def.id).length)
        setSavingStatus('Guardado')

        if (navigator.onLine && getOfflineQueue(def.id).length > 0) {
          flushPendingQueue(def.id)
        }
      } catch (err) {
        if (!isMounted) return
        const cachedDiagramId = localStorage.getItem(`case.last_diagram.${project.id}`)
        if (cachedDiagramId) {
          const cached = loadLocalDiagramSnapshot(cachedDiagramId)
          if (cached) {
            setDiagram(cached.diagram)
            setElementsRaw(cached.elements)
            setRelationsRaw(cached.relations)
            setPendingQueueCount(getOfflineQueue(cachedDiagramId).length)
            setSavingStatus('Cargado offline')
            return
          }
        }
        setSavingStatus('Error al cargar')
        onError(err)
      }
    }

    init()
    return () => {
      isMounted = false
    }
  }, [project.id, auth.token])

  const [wsConnected, setWsConnected] = useState(false)

  // Caso de Uso 8: Conexión WebSocket para sincronización bidireccional en tiempo real (solo si online)
  useEffect(() => {
    if (!diagram || !isOnline) return
    const currentDiagramId = diagram.id
    let ws: WebSocket | null = null
    let isDestroyed = false

    function connectWs() {
      if (isDestroyed || !navigator.onLine) return
      try {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        let host = window.location.host
        if (base) {
          host = base.replace(/^https?:\/\//, '')
        }
        ws = new WebSocket(`${protocol}//${host}/ws/diagrams/${currentDiagramId}`)

        ws.onopen = () => {
          if (!isDestroyed) setWsConnected(true)
        }

        ws.onmessage = async (event) => {
          try {
            const data = JSON.parse(event.data)
            if (data.diagramId === currentDiagramId && navigator.onLine) {
              const full = await request<DiagramFullView>(
                `/api/projects/${project.id}/diagrams/${currentDiagramId}`,
                auth.token
              )
              setElementsRaw((prev) => {
                const prevStr = JSON.stringify(prev)
                const nextStr = JSON.stringify(full.elements)
                return prevStr === nextStr ? prev : full.elements
              })
              setRelationsRaw((prev) => {
                const prevStr = JSON.stringify(prev)
                const nextStr = JSON.stringify(full.relations)
                return prevStr === nextStr ? prev : full.relations
              })
            }
          } catch {
            // Ignorar errores de parseo
          }
        }

        ws.onclose = () => {
          if (!isDestroyed) {
            setWsConnected(false)
            if (navigator.onLine) setTimeout(connectWs, 3000)
          }
        }

        ws.onerror = () => {
          if (!isDestroyed) setWsConnected(false)
        }
      } catch {
        if (!isDestroyed) setWsConnected(false)
      }
    }

    connectWs()

    return () => {
      isDestroyed = true
      if (ws) ws.close()
    }
  }, [diagram?.id, project.id, auth.token, isOnline])

  // Caso de Uso 8: Sincronización continua reactiva (cada 1.5s <= 2s solo si online)
  useEffect(() => {
    if (!diagram?.id || !isOnline) return
    const interval = setInterval(async () => {
      if (!navigator.onLine) return
      try {
        const full = await request<DiagramFullView>(
          `/api/projects/${project.id}/diagrams/${diagram.id}`,
          auth.token
        )
        setElementsRaw((prev) => {
          const prevStr = JSON.stringify(prev)
          const nextStr = JSON.stringify(full.elements)
          return prevStr === nextStr ? prev : full.elements
        })
        setRelationsRaw((prev) => {
          const prevStr = JSON.stringify(prev)
          const nextStr = JSON.stringify(full.relations)
          return prevStr === nextStr ? prev : full.relations
        })
      } catch {
        // En caso de corte momentáneo de red, se mantiene el estado local
      }
    }, 1500)

    return () => clearInterval(interval)
  }, [diagram?.id, project.id, auth.token, isOnline])

  // Sincronizar nodos locales cuando cambian elementsRaw sin perder selección
  useEffect(() => {
    setNodes((prev) => buildNodes(elementsRaw, prev))
  }, [elementsRaw, buildNodes, setNodes])

  // Sincronizar aristas locales cuando cambian relationsRaw
  useEffect(() => {
    setEdges(buildEdges(relationsRaw))
  }, [relationsRaw, buildEdges, setEdges])

  // Atajos de teclado: Ctrl+A (seleccionar todo), Escape (deseleccionar), Delete (eliminar seleccionados)
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      const active = document.activeElement
      const isInput =
        active &&
        (active.tagName === 'INPUT' ||
          active.tagName === 'TEXTAREA' ||
          active.tagName === 'SELECT' ||
          (active as HTMLElement).isContentEditable)

      if (isInput) return

      // Ctrl + A o Cmd + A: Seleccionar todas las clases dentro del lienzo
      if ((e.ctrlKey || e.metaKey) && (e.key === 'a' || e.key === 'A')) {
        e.preventDefault()
        setNodes((prev) => prev.map((n) => ({ ...n, selected: true })))
      }

      // Escape: Deseleccionar todos los elementos
      if (e.key === 'Escape') {
        setNodes((prev) => prev.map((n) => ({ ...n, selected: false })))
      }

      // Delete o Supr: Eliminar elementos seleccionados con confirmación
      if (e.key === 'Delete') {
        e.preventDefault()
        handleDeleteSelected()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [handleDeleteSelected, setNodes])

  // Al finalizar de arrastrar uno o varios nodos seleccionados, persistir coordenadas juntas (con soporte Offline)
  const handleNodeDragStop = useCallback(
    async (_event: unknown, node: UmlNodeType, draggedNodes?: UmlNodeType[]) => {
      const currentDiagram = diagramRef.current
      if (isReadOnly || !currentDiagram) return
      const movedList = draggedNodes && draggedNodes.length > 0 ? draggedNodes : [node]
      const positions = movedList.map((n) => ({
        id: n.id,
        posicionX: n.position.x,
        posicionY: n.position.y
      }))

      const posMap = new Map(positions.map((p) => [p.id, p]))
      const nextElements = elementsRef.current.map((e) => {
        const upd = posMap.get(e.id)
        return upd ? { ...e, posicionX: upd.posicionX, posicionY: upd.posicionY } : e
      })

      if (!navigator.onLine) {
        setElementsRaw(nextElements)
        saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, relationsRaw)
        const count = appendOfflineQueue(currentDiagram.id, {
          type: 'SAVE_POSITIONS',
          payload: { positions }
        })
        setPendingQueueCount(count)
        setSavingStatus('Guardado localmente (Offline)')
        return
      }

      try {
        setSavingStatus('Sincronizando...')
        await request(
          `/api/projects/${project.id}/diagrams/${currentDiagram.id}/elements/positions`,
          auth.token,
          {
            method: 'POST',
            body: JSON.stringify({ positions })
          }
        )
        setElementsRaw(nextElements)
        saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, relationsRaw)
        setSavingStatus('Guardado')
      } catch (err) {
        setElementsRaw(nextElements)
        saveLocalDiagramSnapshot(currentDiagram.id, currentDiagram, nextElements, relationsRaw)
        const count = appendOfflineQueue(currentDiagram.id, {
          type: 'SAVE_POSITIONS',
          payload: { positions }
        })
        setPendingQueueCount(count)
        setSavingStatus('Guardado localmente (Offline)')
      }
    },
    [isReadOnly, project.id, auth.token, relationsRaw]
  )

  // Crear nuevo elemento en el centro (con soporte Offline CU10)
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
    const payload = {
      tipoElemento: tipo,
      nombre: nombreDefecto,
      posicionX: 180 + randomOffset,
      posicionY: 120 + randomOffset,
      propiedadesJson: JSON.stringify({
        description: '',
        stereotype: '',
        attributes: [],
        methods: []
      })
    }

    if (!navigator.onLine) {
      const nuevo: ElementDetail = {
        id: crypto.randomUUID(),
        diagramaId: diagram.id,
        ...payload
      }
      const nextElements = [...elementsRaw, nuevo]
      setElementsRaw(nextElements)
      saveLocalDiagramSnapshot(diagram.id, diagram, nextElements, relationsRaw)
      const count = appendOfflineQueue(diagram.id, {
        type: 'SAVE_ELEMENT',
        payload: nuevo
      })
      setPendingQueueCount(count)
      setSavingStatus('Guardado localmente (Offline)')
      return
    }

    try {
      setSavingStatus('Guardando...')
      const saved = await request<ElementDetail>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/elements`,
        auth.token,
        {
          method: 'POST',
          body: JSON.stringify(payload)
        }
      )
      const nextElements = [...elementsRaw, saved]
      setElementsRaw(nextElements)
      saveLocalDiagramSnapshot(diagram.id, diagram, nextElements, relationsRaw)
      setSavingStatus('Guardado')
    } catch (err) {
      onError(err)
      setSavingStatus('Error al guardar')
    }
  }

  // Guardar cambios del modal de elemento (con soporte Offline CU10)
  async function handleSaveElementDetails(
    nombre: string,
    tipoElemento: TipoElementoUML,
    propiedades: UmlProperties
  ) {
    if (!editingElement || !diagram) return
    const payload = {
      id: editingElement.id,
      tipoElemento,
      nombre,
      posicionX: editingElement.posicionX,
      posicionY: editingElement.posicionY,
      propiedadesJson: JSON.stringify(propiedades)
    }

    const nextElements = elementsRaw.map((e) =>
      e.id === editingElement.id ? { ...e, ...payload } : e
    )

    if (!navigator.onLine) {
      setElementsRaw(nextElements)
      saveLocalDiagramSnapshot(diagram.id, diagram, nextElements, relationsRaw)
      const count = appendOfflineQueue(diagram.id, {
        type: 'SAVE_ELEMENT',
        payload
      })
      setPendingQueueCount(count)
      setEditingElement(null)
      setSavingStatus('Guardado localmente (Offline)')
      return
    }

    try {
      setSavingStatus('Guardando...')
      const saved = await request<ElementDetail>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/elements`,
        auth.token,
        {
          method: 'POST',
          body: JSON.stringify(payload)
        }
      )
      const updatedElements = elementsRaw.map((e) => (e.id === saved.id ? saved : e))
      setElementsRaw(updatedElements)
      saveLocalDiagramSnapshot(diagram.id, diagram, updatedElements, relationsRaw)
      setEditingElement(null)
      setSavingStatus('Guardado')
    } catch (err) {
      setElementsRaw(nextElements)
      saveLocalDiagramSnapshot(diagram.id, diagram, nextElements, relationsRaw)
      const count = appendOfflineQueue(diagram.id, {
        type: 'SAVE_ELEMENT',
        payload
      })
      setPendingQueueCount(count)
      setEditingElement(null)
      setSavingStatus('Guardado localmente (Offline)')
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

  // Guardar nueva relación desde el modal (con soporte Offline CU10)
  async function handleSavePendingRelation(
    tipoRelacion: TipoRelacionUML,
    nombreRelacion: string | null,
    multiplicidadOrigen: string | null,
    multiplicidadDestino: string | null
  ) {
    if (!pendingConnection || !diagram) return
    const payload = {
      elementoOrigen: pendingConnection.source,
      elementoDestino: pendingConnection.target,
      nombreRelacion,
      tipoRelacion,
      multiplicidadOrigen,
      multiplicidadDestino
    }

    if (!navigator.onLine) {
      const localRel: RelationDetail = {
        id: crypto.randomUUID(),
        diagramaId: diagram.id,
        elementoOrigen: pendingConnection.source,
        elementoDestino: pendingConnection.target,
        nombreRelacion,
        tipoRelacion,
        multiplicidadOrigen,
        multiplicidadDestino
      }
      const nextRels = [...relationsRaw, localRel]
      setRelationsRaw(nextRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, nextRels)
      const count = appendOfflineQueue(diagram.id, {
        type: 'SAVE_RELATION',
        payload
      })
      setPendingQueueCount(count)
      setPendingConnection(null)
      setSavingStatus('Guardado localmente (Offline)')
      return
    }

    try {
      setSavingStatus('Guardando relación...')
      const saved = await request<RelationDetail>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/relations`,
        auth.token,
        {
          method: 'POST',
          body: JSON.stringify(payload)
        }
      )

      const nextRels = [...relationsRaw, saved]
      setRelationsRaw(nextRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, nextRels)
      setPendingConnection(null)
      setSavingStatus('Guardado')
    } catch (err) {
      const localRel: RelationDetail = {
        id: crypto.randomUUID(),
        diagramaId: diagram.id,
        elementoOrigen: pendingConnection.source,
        elementoDestino: pendingConnection.target,
        nombreRelacion,
        tipoRelacion,
        multiplicidadOrigen,
        multiplicidadDestino
      }
      const nextRels = [...relationsRaw, localRel]
      setRelationsRaw(nextRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, nextRels)
      const count = appendOfflineQueue(diagram.id, {
        type: 'SAVE_RELATION',
        payload
      })
      setPendingQueueCount(count)
      setPendingConnection(null)
      setSavingStatus('Guardado localmente (Offline)')
    }
  }

  // Clic sobre una relación para editar o eliminar
  function handleEdgeClick(_event: React.MouseEvent, edge: Edge) {
    const rel = relationsRaw.find((r) => r.id === edge.id)
    if (rel) setEditingRelation(rel)
  }

  // Guardar edición de relación existente (con soporte Offline CU10)
  async function handleUpdateRelation(
    tipoRelacion: TipoRelacionUML,
    nombreRelacion: string | null,
    multiplicidadOrigen: string | null,
    multiplicidadDestino: string | null
  ) {
    if (!editingRelation || !diagram) return
    const payload = {
      id: editingRelation.id,
      elementoOrigen: editingRelation.elementoOrigen,
      elementoDestino: editingRelation.elementoDestino,
      nombreRelacion,
      tipoRelacion,
      multiplicidadOrigen,
      multiplicidadDestino
    }

    const nextRels = relationsRaw.map((r) =>
      r.id === editingRelation.id ? { ...r, ...payload } : r
    )

    if (!navigator.onLine) {
      setRelationsRaw(nextRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, nextRels)
      const count = appendOfflineQueue(diagram.id, {
        type: 'SAVE_RELATION',
        payload
      })
      setPendingQueueCount(count)
      setEditingRelation(null)
      setSavingStatus('Guardado localmente (Offline)')
      return
    }

    try {
      setSavingStatus('Actualizando relación...')
      const saved = await request<RelationDetail>(
        `/api/projects/${project.id}/diagrams/${diagram.id}/relations`,
        auth.token,
        {
          method: 'POST',
          body: JSON.stringify(payload)
        }
      )

      const updatedRels = relationsRaw.map((r) => (r.id === saved.id ? saved : r))
      setRelationsRaw(updatedRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, updatedRels)
      setEditingRelation(null)
      setSavingStatus('Guardado')
    } catch (err) {
      setRelationsRaw(nextRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, nextRels)
      const count = appendOfflineQueue(diagram.id, {
        type: 'SAVE_RELATION',
        payload
      })
      setPendingQueueCount(count)
      setEditingRelation(null)
      setSavingStatus('Guardado localmente (Offline)')
    }
  }

  // Eliminar relación (con soporte Offline CU10)
  async function handleDeleteRelation() {
    if (!editingRelation || !diagram) return
    const nextRels = relationsRaw.filter((r) => r.id !== editingRelation.id)

    if (!navigator.onLine) {
      setRelationsRaw(nextRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, nextRels)
      const count = appendOfflineQueue(diagram.id, {
        type: 'DELETE_RELATION',
        payload: { relationId: editingRelation.id }
      })
      setPendingQueueCount(count)
      setEditingRelation(null)
      setSavingStatus('Guardado localmente (Offline)')
      return
    }

    try {
      setSavingStatus('Eliminando relación...')
      await request(
        `/api/projects/${project.id}/diagrams/${diagram.id}/relations/${editingRelation.id}`,
        auth.token,
        {
          method: 'DELETE'
        }
      )
      setRelationsRaw(nextRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, nextRels)
      setEditingRelation(null)
      setSavingStatus('Guardado')
    } catch (err) {
      setRelationsRaw(nextRels)
      saveLocalDiagramSnapshot(diagram.id, diagram, elementsRaw, nextRels)
      const count = appendOfflineQueue(diagram.id, {
        type: 'DELETE_RELATION',
        payload: { relationId: editingRelation.id }
      })
      setPendingQueueCount(count)
      setEditingRelation(null)
      setSavingStatus('Guardado localmente (Offline)')
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

  const selectedCount = useMemo(() => nodes.filter((n) => n.selected).length, [nodes])

  return (
    <div className="uml-workspace-container">
      {/* Barra de herramientas superior */}
      <div className="uml-toolbar">
        <div className="uml-toolbar-left">
          <span className="uml-diagram-title">{diagram?.nombre ?? 'Cargando diagrama...'}</span>
          <span
            className={`status-pill ${
              !isOnline ? 'offline' : isSyncingQueue ? 'syncing' : savingStatus.includes('Error') ? 'error' : 'online'
            }`}
            title={
              !isOnline
                ? 'Modo Offline: Sin conexión a internet. Los cambios se guardan localmente y se sincronizarán al reconectar.'
                : isSyncingQueue
                ? 'Sincronizando operaciones pendientes con el servidor...'
                : 'Conexión activa con el servidor'
            }
          >
            {!isOnline
              ? `🟡 Modo Offline${pendingQueueCount > 0 ? ` (${pendingQueueCount} pendientes)` : ''}`
              : isSyncingQueue
              ? `🔄 Sincronizando (${pendingQueueCount})...`
              : '🟢 En línea'}
          </span>
          <span className={`status-pill ${savingStatus.includes('Error') ? 'error' : ''}`}>
            {savingStatus}
          </span>
          {isOnline && (
            <span
              className={`collab-pill ${wsConnected ? 'connected' : 'syncing'}`}
              title="Sincronización colaborativa en tiempo real (< 2s)"
            >
              {wsConnected ? '⚡ En vivo' : '🔄 Sincronizando'}
            </span>
          )}
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
            title="Transformar diagrama a tablas SQL y PostgreSQL DDL (CU06 / CU11)"
          >
            {generatingRelational ? 'Generando...' : '🗄️ Relacional & SQL'}
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
            title="Exportar o importar modelos en XMI o JSON con cardinalidades (CU13)"
          >
            📦 Exportar / Importar
          </button>

          <button
            type="button"
            className="uml-tool-btn feature-btn"
            onClick={() => setShowApiTester(true)}
            title="Probar endpoints de la API generada por REST o voz"
          >
            🧪 Probar API
          </button>

          {/* Controles de Selección Múltiple (Requerimientos 2 y 13) */}
          {selectedCount > 0 ? (
            <div className="selection-toolbar-group">
              <span className="selection-count-badge">
                🎯 {selectedCount} {selectedCount === 1 ? 'seleccionada' : 'seleccionadas'}
              </span>
              {!isReadOnly && (
                <button
                  type="button"
                  className="uml-tool-btn danger"
                  onClick={handleDeleteSelected}
                  title="Eliminar clases seleccionadas (Supr / Delete)"
                >
                  🗑️ Eliminar ({selectedCount})
                </button>
              )}
              <button
                type="button"
                className="uml-tool-btn secondary"
                onClick={() => setNodes((prev) => prev.map((n) => ({ ...n, selected: false })))}
                title="Deseleccionar todo (Escape)"
              >
                Deseleccionar todo
              </button>
            </div>
          ) : (
            <button
              type="button"
              className="uml-tool-btn secondary"
              onClick={() => setNodes((prev) => prev.map((n) => ({ ...n, selected: true })))}
              title="Seleccionar todas las clases dentro del lienzo (Ctrl + A)"
            >
              Seleccionar todo (Ctrl+A)
            </button>
          )}

          {!isReadOnly && (
            <>
              <span className="toolbar-divider" />
              <button
                type="button"
                className="uml-tool-btn"
                onClick={() => handleAddElement('CLASE')}
                title="Crear una nueva clase vacía en el lienzo"
              >
                + Nueva Clase
              </button>
              <button
                type="button"
                className="uml-tool-btn secondary"
                onClick={() => handleAddElement('INTERFAZ')}
                title="Crear una nueva interfaz UML"
              >
                + Interfaz
              </button>
              <button
                type="button"
                className="uml-tool-btn secondary"
                onClick={() => handleAddElement('CLASE_ABSTRACTA')}
                title="Crear una nueva clase abstracta"
              >
                + Abstracta
              </button>
              <button
                type="button"
                className="uml-tool-btn secondary"
                onClick={() => handleAddElement('ENUMERACION')}
                title="Crear una nueva enumeración UML"
              >
                + Enumeración
              </button>
            </>
          )}
        </div>
      </div>

      {/* Contenedor React Flow con Selección Múltiple (Cursor y Ctrl+A) */}
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
          selectionOnDrag={true}
          selectionMode={SelectionMode.Partial}
          panOnDrag={[1, 2]}
          multiSelectionKeyCode={['Control', 'Meta']}
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

      {/* Modal de Modelo Relacional Generado (CU06 / CU11) */}
      {relationalSchema && diagram && (
        <RelationalModal
          schema={relationalSchema}
          projectName={project.name}
          projectId={project.id}
          diagramId={diagram.id}
          token={auth.token}
          isReadOnly={isReadOnly}
          onClose={() => setRelationalSchema(null)}
          onDiagramUpdated={handleFullDiagramUpdated}
          onError={onError}
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
