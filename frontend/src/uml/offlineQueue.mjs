export async function replayPendingQueue(queue, applyAction) {
  const completedIds = []
  for (const action of queue) {
    try {
      await applyAction(action)
    } catch (error) {
      return { completedIds, error, failedActionId: action.id }
    }
    completedIds.push(action.id)
  }
  return { completedIds, error: null, failedActionId: null }
}

export function normalizePendingOperations(queue, baseline) {
  const knownElements = new Set((baseline?.elements ?? []).map((element) => element.id))
  const knownRelations = new Set((baseline?.relations ?? []).map((relation) => relation.id))
  const seenElements = new Set()
  const seenRelations = new Set()

  return queue.map((action) => {
    if (action.type === 'SAVE_ELEMENT') {
      const id = action.payload?.id
      const isUpdate = knownElements.has(id) || seenElements.has(id)
      if (id) seenElements.add(id)
      return isUpdate ? { ...action, type: 'UPDATE_ELEMENT' } : action
    }
    if (action.type === 'SAVE_RELATION') {
      const id = action.payload?.id
      const isUpdate = knownRelations.has(id) || seenRelations.has(id)
      if (id) seenRelations.add(id)
      return isUpdate ? { ...action, type: 'UPDATE_RELATION' } : action
    }
    return action
  })
}

export function snapshotContentMatches(left, right) {
  if (!left || !right) return true
  const canonical = (snapshot) => JSON.stringify({
    elements: [...snapshot.elements].sort((a, b) => a.id.localeCompare(b.id)),
    relations: [...snapshot.relations].sort((a, b) => a.id.localeCompare(b.id))
  })
  return canonical(left) === canonical(right)
}
