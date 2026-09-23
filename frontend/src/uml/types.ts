export type Role = 'PROPIETARIO' | 'EDITOR' | 'LECTOR'

export type TipoElementoUML = 'CLASE' | 'INTERFAZ' | 'CLASE_ABSTRACTA' | 'ENUMERACION'
export type TipoRelacionUML = 'ASOCIACION' | 'HERENCIA' | 'AGREGACION' | 'COMPOSICION' | 'DEPENDENCIA'

export type UmlVisibility = '+' | '-' | '#' | '~'

export const UML_DATA_TYPES = [
  'String',
  'Integer',
  'Long',
  'Float',
  'Double',
  'Decimal',
  'Boolean',
  'Date',
  'DateTime',
  'Time',
  'Char',
  'Text',
  'UUID',
  'JSON',
  'List',
  'Set',
  'Array',
  'Object',
  'Enum'
] as const

export type UmlDataType = (typeof UML_DATA_TYPES)[number]

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
  description?: string
  stereotype?: string
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

export type ValidationIssue = {
  severity: 'ERROR' | 'WARNING' | 'INFO'
  elementoId: string | null
  elementoNombre: string | null
  mensaje: string
  sugerencia: string
}

export type ValidationReport = {
  valid: boolean
  errorCount: number
  warningCount: number
  issues: ValidationIssue[]
}

export type RelationalColumn = {
  name: string
  sqlType: string
  primaryKey: boolean
  nullable: boolean
  unique: boolean
  defaultValue: string | null
}

export type RelationalForeignKey = {
  constraintName: string
  columnName: string
  targetTable: string
  targetColumn: string
  cascadeDelete: boolean
}

export type RelationalTable = {
  name: string
  umlSourceClass: string
  columns: RelationalColumn[]
  foreignKeys: RelationalForeignKey[]
}

export type RelationalSchema = {
  tables: RelationalTable[]
  sqlDdl: string
}

export function parseUmlProperties(json?: string | null): UmlProperties {
  if (!json) return { description: '', stereotype: '', attributes: [], methods: [] }
  try {
    const parsed = JSON.parse(json)
    return {
      description: typeof parsed.description === 'string' ? parsed.description : '',
      stereotype: typeof parsed.stereotype === 'string' ? parsed.stereotype : '',
      attributes: Array.isArray(parsed.attributes) ? parsed.attributes : [],
      methods: Array.isArray(parsed.methods) ? parsed.methods : []
    }
  } catch {
    return { description: '', stereotype: '', attributes: [], methods: [] }
  }
}
