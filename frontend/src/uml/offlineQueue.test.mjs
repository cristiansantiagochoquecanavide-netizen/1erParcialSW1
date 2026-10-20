import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizePendingOperations, replayPendingQueue, snapshotContentMatches } from './offlineQueue.mjs'

const action = (id, value = id) => ({ id, type: 'SAVE_ELEMENT', payload: { value }, timestamp: 1 })

test('clears the queue when every operation is confirmed', async () => {
  const sent = []
  const { completedIds } = await replayPendingQueue([action('a'), action('b')], async (item) => sent.push(item.id))
  assert.deepEqual(sent, ['a', 'b'])
  assert.deepEqual(completedIds, ['a', 'b'])
})

test('a failed operation and the unattempted tail remain pending', async () => {
  const queue = [action('a'), action('b'), action('c')]
  const applied = []
  const { completedIds: completed } = await replayPendingQueue(queue, async (current) => {
    if (current.id === 'b') throw new Error('network')
    applied.push(current.id)
  })
  assert.deepEqual(applied, ['a'])
  assert.deepEqual(completed, ['a'])
  assert.deepEqual(queue.filter((item) => !completed.includes(item.id)).map((item) => item.id), ['b', 'c'])
})

test('recovery retries the retained operation in order without replaying confirmed IDs', async () => {
  const queue = [action('a'), action('b'), action('c')]
  const { completedIds: confirmed } = await replayPendingQueue(queue, async (item) => {
    if (item.id === 'b') throw new Error('network')
  })
  const pending = queue.filter((item) => !confirmed.includes(item.id))
  const retried = []
  const { completedIds: retriedIds } = await replayPendingQueue(pending, async (item) => retried.push(item.id))
  assert.deepEqual(retried, ['b', 'c'])
  assert.deepEqual(retriedIds, ['b', 'c'])
})

test('retry preserves stable UML IDs and queue order after an uncertain response', async () => {
  const queued = [
    { ...action('queue-a'), payload: { id: 'element-client-id', nombre: 'A' } },
    { ...action('queue-edit'), type: 'UPDATE_ELEMENT', payload: { id: 'element-client-id', nombre: 'A edited' } },
    { ...action('queue-b'), type: 'SAVE_RELATION', payload: { id: 'relation-client-id', elementoOrigen: 'e1' } }
  ]
  const firstAttempt = []
  const first = await replayPendingQueue(queued, async (item) => {
    firstAttempt.push([item.payload.id, item.type])
    if (item.id === 'queue-b') throw new Error('response lost')
  })
  const pending = queued.filter((item) => !first.completedIds.includes(item.id))
  const retry = []
  await replayPendingQueue(pending, async (item) => retry.push([item.payload.id, item.type]))

  assert.deepEqual(firstAttempt, [
    ['element-client-id', 'SAVE_ELEMENT'],
    ['element-client-id', 'UPDATE_ELEMENT'],
    ['relation-client-id', 'SAVE_RELATION']
  ])
  assert.deepEqual(retry, [['relation-client-id', 'SAVE_RELATION']])
})

test('legacy queued edits remain updates while new queued objects remain creations', () => {
  const queue = [
    { ...action('edit-existing'), payload: { id: 'existing-element' } },
    { ...action('create-element'), payload: { id: 'new-element' } },
    { ...action('edit-new-element'), payload: { id: 'new-element' } },
    { ...action('edit-relation', 'r'), type: 'SAVE_RELATION', payload: { id: 'existing-relation' } }
  ]
  const normalized = normalizePendingOperations(queue, {
    elements: [{ id: 'existing-element' }],
    relations: [{ id: 'existing-relation' }]
  })

  assert.deepEqual(normalized.map(({ id, type }) => [id, type]), [
    ['edit-existing', 'UPDATE_ELEMENT'],
    ['create-element', 'SAVE_ELEMENT'],
    ['edit-new-element', 'UPDATE_ELEMENT'],
    ['edit-relation', 'UPDATE_RELATION']
  ])
  assert.deepEqual(normalized.map(({ payload }) => payload.id), queue.map(({ payload }) => payload.id))
})

test('stops on partial failure without dropping remaining operations', async () => {
  const attempted = []
  const result = await replayPendingQueue([action('a'), action('b'), action('c')], async (item) => {
    attempted.push(item.id)
    if (item.id === 'b') throw new Error('failed')
  })
  assert.deepEqual(attempted, ['a', 'b'])
  assert.deepEqual(result.completedIds, ['a'])
  assert.equal(result.failedActionId, 'b')
})

test('detects server content drift before replay and ignores array ordering', () => {
  const base = { elements: [{ id: 'e1', nombre: 'A' }], relations: [] }
  const same = { elements: [{ id: 'e1', nombre: 'A' }], relations: [] }
  const changed = { elements: [{ id: 'e1', nombre: 'B' }], relations: [] }
  assert.equal(snapshotContentMatches(base, same), true)
  assert.equal(snapshotContentMatches(base, changed), false)
  assert.equal(snapshotContentMatches({ elements: [{ id: 'b' }, { id: 'a' }], relations: [] }, { elements: [{ id: 'a' }, { id: 'b' }], relations: [] }), true)
})

test('queued actions retain expectedRevision and replay preserves queue on 409 conflict', async () => {
  const queue = [
    { ...action('op-1'), expectedRevision: 5, payload: { id: 'e1', nombre: 'ClaseA' } },
    { ...action('op-2'), expectedRevision: 6, payload: { id: 'e2', nombre: 'ClaseB' } },
    { ...action('op-3'), expectedRevision: 7, payload: { id: 'e3', nombre: 'ClaseC' } }
  ]

  const sentRevisions = []
  const error409 = new Error('HTTP 409 Conflict: Diagram revision changed')
  error409.status = 409

  const result = await replayPendingQueue(queue, async (item) => {
    sentRevisions.push({ id: item.id, rev: item.expectedRevision })
    if (item.id === 'op-2') {
      throw error409
    }
  })

  // op-1 succeeded, op-2 failed with conflict, op-3 was not attempted
  assert.deepEqual(sentRevisions, [
    { id: 'op-1', rev: 5 },
    { id: 'op-2', rev: 6 }
  ])
  assert.deepEqual(result.completedIds, ['op-1'])
  assert.equal(result.failedActionId, 'op-2')
  assert.equal(result.error.status, 409)

  const pending = queue.filter((item) => !result.completedIds.includes(item.id))
  assert.equal(pending.length, 2)
  assert.equal(pending[0].id, 'op-2')
  assert.equal(pending[0].expectedRevision, 6)
  assert.equal(pending[1].id, 'op-3')
  assert.equal(pending[1].expectedRevision, 7)
})

test('simulates 3 queued operations: tracks sequential revisions, halts on 409, and prevents duplication on retry', async () => {
  const queue = [
    { ...action('op-1'), type: 'SAVE_ELEMENT', payload: { id: 'elem-1', nombre: 'ClaseA' } },
    { ...action('op-2'), type: 'SAVE_ELEMENT', payload: { id: 'elem-2', nombre: 'ClaseB' } },
    { ...action('op-3'), type: 'SAVE_RELATION', payload: { id: 'rel-1', elementoOrigen: 'elem-1', elementoDestino: 'elem-2' } }
  ]

  let serverCurrentRevision = 10
  const serverReceived = []

  // Simulate server endpoint
  const mockServerHandler = async (action, expectedRevision, simulateConflictOnId = null) => {
    if (action.id === simulateConflictOnId) {
      const err = new Error('HTTP 409 Conflict')
      err.status = 409
      throw err
    }
    // Server verifies optimistic concurrency
    if (expectedRevision !== serverCurrentRevision) {
      const err = new Error(`HTTP 409 Conflict: expected ${expectedRevision} but was ${serverCurrentRevision}`)
      err.status = 409
      throw err
    }
    serverCurrentRevision++
    serverReceived.push({ id: action.id, verifiedRevision: expectedRevision, newRevision: serverCurrentRevision })
    return { revision: serverCurrentRevision }
  }

  // Attempt 1: op-1 succeeds (rev 10 -> 11), op-2 fails with 409
  let clientRevision = 10
  const attempt1 = await replayPendingQueue(queue, async (item) => {
    const res = await mockServerHandler(item, clientRevision, 'op-2')
    clientRevision = res.revision
  })

  // 1. op-1 confirmed, op-2 failed, op-3 unattempted
  assert.deepEqual(attempt1.completedIds, ['op-1'])
  assert.equal(attempt1.failedActionId, 'op-2')
  assert.equal(attempt1.error.status, 409)

  // 2. Queue retained remaining operations (op-2 and op-3)
  const remainingQueue = queue.filter((item) => !attempt1.completedIds.includes(item.id))
  assert.equal(remainingQueue.length, 2)
  assert.deepEqual(remainingQueue.map((it) => it.id), ['op-2', 'op-3'])

  // 3. Retry after syncing latest revision from server (say server is at 11 now)
  clientRevision = serverCurrentRevision // 11
  const attempt2 = await replayPendingQueue(remainingQueue, async (item) => {
    const res = await mockServerHandler(item, clientRevision, null)
    clientRevision = res.revision
  })

  // 4. op-2 and op-3 now succeed with sequential revisions 11 and 12
  assert.deepEqual(attempt2.completedIds, ['op-2', 'op-3'])
  assert.equal(attempt2.error, null)

  // 5. Total server mutations processed: exactly op-1 (rev 10), op-2 (rev 11), op-3 (rev 12). No duplication of op-1!
  assert.deepEqual(serverReceived, [
    { id: 'op-1', verifiedRevision: 10, newRevision: 11 },
    { id: 'op-2', verifiedRevision: 11, newRevision: 12 },
    { id: 'op-3', verifiedRevision: 12, newRevision: 13 }
  ])
})

