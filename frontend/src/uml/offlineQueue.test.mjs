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
