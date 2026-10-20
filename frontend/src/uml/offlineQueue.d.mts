export interface OfflineAction {
  id: string
  type: string
  payload: any
  timestamp: number
  expectedRevision?: number | null
}

export interface DiagramContentSnapshot {
  elements: { id: string; [key: string]: any }[]
  relations: { id: string; [key: string]: any }[]
}

export function replayPendingQueue<T extends OfflineAction>(
  queue: T[],
  applyAction: (action: T) => Promise<void>
): Promise<{ completedIds: string[]; error: unknown | null; failedActionId: string | null }>

export function normalizePendingOperations<T extends OfflineAction>(
  queue: T[],
  baseline: DiagramContentSnapshot | null
): T[]

export function snapshotContentMatches(
  left: DiagramContentSnapshot | null,
  right: DiagramContentSnapshot | null
): boolean
