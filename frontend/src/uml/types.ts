export type Role = 'PROPIETARIO' | 'EDITOR' | 'LECTOR'

export type TipoElementoUML = 'CLASE' | 'INTERFAZ' | 'CLASE_ABSTRACTA' | 'ENUMERACION'
export type TipoRelacionUML = 'ASOCIACION' | 'HERENCIA' | 'AGREGACION' | 'COMPOSICION' | 'DEPENDENCIA'

export type UmlVisibility = '+' | '-' | '#'

export type UmlAttribute = {
  id: string
  visibility: UmlVisibility
  name: string
  type: string
}

export type UmlMethod = {
  id: string
  visibility: UmlVisibility
  name: string
  returnType: string
  parameters: string
}

export type UmlProperties = {
  attributes: UmlAttribute[]
  methods: UmlMethod[]
}

export type DiagramSummary = {
  id: string
  projectId: string
  nombre: string
  descripcion: string | null
  estado: string
  fechaCreacion: string
}

export type ElementDetail = {
  id: string
  diagramaId: string
  tipoElemento: TipoElementoUML
  nombre: string
  posicionX: number
  posicionY: number
  propiedadesJson: string
}

export type RelationDetail = {
  id: string
  diagramaId: string
  elementoOrigen: string
  elementoDestino: string
  nombreRelacion: string | null
  tipoRelacion: TipoRelacionUML
  multiplicidadOrigen: string | null
  multiplicidadDestino: string | null
}

export type DiagramFullView = {
  diagram: DiagramSummary
  elements: ElementDetail[]
  relations: RelationDetail[]
}

export function parseUmlProperties(json?: string | null): UmlProperties {
  if (!json) return { attributes: [], methods: [] }
  try {
    const parsed = JSON.parse(json)
    return {
      attributes: Array.isArray(parsed.attributes) ? parsed.attributes : [],
      methods: Array.isArray(parsed.methods) ? parsed.methods : []
    }
  } catch {
    return { attributes: [], methods: [] }
  }
}
