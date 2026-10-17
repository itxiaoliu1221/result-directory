/**
 * 拣货调度后端 API 端到端测试脚本。
 *
 * 前置条件：
 *   1. 用 database/init-test.sql 初始化一个独立测试库；
 *   2. 以 APP_DEMO_ENABLED=false 启动后端（默认 http://localhost:8099）；
 *   3. 脚本假定数据库是刚初始化的干净状态，运行过程中会修改数据。
 *
 * 运行：node scripts/e2e-api-test.mjs
 * 可通过 E2E_BASE / E2E_WS 覆盖地址。
 */

const BASE = process.env.E2E_BASE || 'http://localhost:8099/backend/api'
const WS_URL = process.env.E2E_WS || BASE.replace(/^http/, 'ws').replace(/\/api$/, '') + '/ws/picking'

let passed = 0
let failed = 0
const failures = []

function check(name, condition, detail = '') {
  if (condition) {
    passed++
    console.log(`PASS  ${name}`)
  } else {
    failed++
    failures.push({ name, detail })
    console.log(`FAIL  ${name}  ${detail}`)
  }
}

function eq(name, actual, expected) {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  check(name, a === e, `actual=${a} expected=${e}`)
}

async function api(method, path, body) {
  const response = await fetch(BASE + path, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let json = null
  try {
    json = await response.json()
  } catch {
    // ignore non-JSON response
  }
  return { status: response.status, body: json, data: json?.data }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function inventoryRow(rows, goodsId) {
  return rows.find((row) => row.goodsId === goodsId)
}

async function main() {
  // ---------------------------------------------------------------- read-only
  let r = await api('GET', '/meta/warehouses')
  check('GET /meta/warehouses returns 200', r.status === 200)
  eq('warehouse count', r.data.length, 2)
  eq('warehouse names', r.data.map((w) => w.name).sort(), ['测试仓库A', '测试仓库B'])

  r = await api('GET', '/meta/goods')
  check('GET /meta/goods returns 200', r.status === 200)
  eq('goods count', r.data.length, 8)

  r = await api('GET', '/dashboard/summary')
  check('GET /dashboard/summary returns 200', r.status === 200)
  eq('dashboard totalTasks', r.data.totalTasks, 7)
  eq('dashboard pending', r.data.pending, 3)
  eq('dashboard dispatched', r.data.dispatched, 2)
  eq('dashboard fulfilled', r.data.fulfilled, 1)
  eq('dashboard stockout', r.data.stockout, 1)
  eq('dashboard inventoryRows', r.data.inventoryRows, 15)
  eq('dashboard staleInventory', r.data.staleInventory, 1)

  r = await api('GET', '/inventory?warehouseId=2')
  eq('warehouse B inventory rows', r.data.length, 7)
  const bGoods3 = inventoryRow(r.data, 3)
  const bGoods2 = inventoryRow(r.data, 2)
  check('B 商品03 marked stale', bGoods3.stale === true)
  eq('B 商品03 available qty', bGoods3.availableQty, 25)
  eq('B 商品02 available qty', bGoods2.availableQty, 6)

  r = await api('GET', '/pick-tasks?status=PENDING&size=100')
  eq('seeded pending task count', r.data.content.length, 3)
  eq('seeded pending task ids', r.data.content.map((t) => t.id).sort((a, b) => a - b), [2, 3, 4])
  eq(
    'seeded pending reasons',
    r.data.content.map((t) => t.reason).sort(),
    ['INSUFFICIENT_STOCK', 'NO_INVENTORY', 'SNAPSHOT_STALE']
  )

  r = await api('GET', '/orders')
  eq('seeded order count', r.data.length, 7)
  const orderStatusCounts = r.data.reduce((acc, order) => {
    acc[order.status] = (acc[order.status] || 0) + 1
    return acc
  }, {})
  eq('seeded order status count',
    Object.entries(orderStatusCounts).sort(),
    [['DISPATCHED', 1], ['PENDING', 3], ['PICKED', 1], ['PICKING', 1], ['STOCKOUT', 1]])

  r = await api('GET', '/replenishments')
  eq('seeded replenishment count', r.data.length, 1)

  // --------------------------------------------------------------- validation
  r = await api('POST', '/pick-tasks', { warehouseId: 1, goodsId: 1, requiredQty: 0 })
  eq('reject requiredQty=0', r.status, 400)

  r = await api('POST', '/pick-tasks', { warehouseId: 999, goodsId: 1, requiredQty: 1 })
  eq('reject unknown warehouse', r.status, 404)

  r = await api('GET', '/pick-tasks/99999')
  eq('unknown task returns 404', r.status, 404)

  r = await api('POST', '/orders', { goodsId: 1, quantity: 0 })
  eq('reject order quantity=0', r.status, 400)

  r = await api('POST', '/orders', { goodsId: 999, quantity: 1 })
  eq('reject unknown goods', r.status, 404)

  r = await api('POST', '/pick-tasks/7/claim', { pickerName: 'E2E-PICKER' })
  eq('reject claiming an already-claimed task', r.status, 409)

  r = await api('POST', '/pick-tasks/2/result', { result: 'FULFILLED' })
  eq('reject completing a pending task', r.status, 409)

  // ----------------------------------------------------- create-task decisions
  r = await api('POST', '/pick-tasks', {
    warehouseId: 1,
    goodsId: 7,
    requiredQty: 1,
    requestNo: 'E2E-TASK-001'
  })
  eq('fresh + sufficient -> DISPATCHED', r.data.status, 'DISPATCHED')
  eq('direct dispatch snapshot qty', r.data.snapshotQty, 20)
  const directTaskId = r.data.id

  r = await api('POST', '/pick-tasks', {
    warehouseId: 1,
    goodsId: 7,
    requiredQty: 1,
    requestNo: 'E2E-TASK-001'
  })
  eq('same requestNo returns same task', r.data.id, directTaskId)

  r = await api('GET', '/inventory?warehouseId=1')
  eq('idempotent create does not deduct twice', inventoryRow(r.data, 7).availableQty, 19)

  r = await api('POST', '/pick-tasks', {
    warehouseId: 1,
    goodsId: 7,
    requiredQty: 2,
    requestNo: 'E2E-TASK-001'
  })
  eq('same requestNo with different payload -> 409', r.status, 409)

  r = await api('POST', '/pick-tasks', {
    warehouseId: 2,
    goodsId: 5,
    requiredQty: 1,
    requestNo: 'E2E-TASK-INSUFFICIENT'
  })
  eq('zero stock -> PENDING / INSUFFICIENT_STOCK', [r.data.status, r.data.reason], ['PENDING', 'INSUFFICIENT_STOCK'])
  const insufficientTaskId = r.data.id

  r = await api('POST', '/pick-tasks', {
    warehouseId: 2,
    goodsId: 3,
    requiredQty: 1,
    requestNo: 'E2E-TASK-STALE'
  })
  eq('stale snapshot -> PENDING / SNAPSHOT_STALE', [r.data.status, r.data.reason], ['PENDING', 'SNAPSHOT_STALE'])
  const staleTaskId = r.data.id

  r = await api('POST', '/pick-tasks', {
    warehouseId: 2,
    goodsId: 8,
    requiredQty: 4,
    requestNo: 'E2E-TASK-NOINV'
  })
  eq('no inventory -> PENDING / NO_INVENTORY', [r.data.status, r.data.reason], ['PENDING', 'NO_INVENTORY'])
  const noInventoryTaskId = r.data.id

  // Two pending tasks for the same warehouse+goods to verify strict FIFO.
  r = await api('POST', '/pick-tasks', {
    warehouseId: 2,
    goodsId: 7,
    requiredQty: 10,
    requestNo: 'E2E-FIFO-001'
  })
  eq('first FIFO task is pending', r.data.status, 'PENDING')
  const fifoTaskA = r.data.id

  r = await api('POST', '/pick-tasks', {
    warehouseId: 2,
    goodsId: 7,
    requiredQty: 10,
    requestNo: 'E2E-FIFO-002'
  })
  eq('second FIFO task is pending', r.data.status, 'PENDING')
  const fifoTaskB = r.data.id

  // ------------------------------------------------------------- replenishment
  r = await api('POST', '/replenishments', {
    requestNo: 'E2E-REP-001',
    warehouseId: 2,
    source: 'e2e',
    items: [
      { goodsId: 2, quantity: 10 },
      { goodsId: 3, quantity: 5 },
      { goodsId: 8, quantity: 8 }
    ]
  })
  check('batch replenishment returns 200', r.status === 200)
  eq('batch dispatched task count', r.data.dispatchedTasks.length, 5)
  const itemResults = Object.fromEntries(r.data.items.map((item) => [item.goodsId, item]))
  eq('B 商品02 remaining after dispatch', itemResults[2].remainingQty, 6)
  eq('B 商品02 dispatched count', itemResults[2].dispatchedCount, 1)
  eq('B 商品03 remaining after dispatch', itemResults[3].remainingQty, 24)
  eq('B 商品03 dispatched count', itemResults[3].dispatchedCount, 2)
  eq('B 商品08 remaining after dispatch', itemResults[8].remainingQty, 0)
  eq('B 商品08 dispatched count', itemResults[8].dispatchedCount, 2)

  r = await api('GET', '/pick-tasks?status=DISPATCHED&size=100')
  const dispatchedIds = r.data.content.map((t) => t.id)
  check('seeded pending tasks dispatched', [2, 3, 4].every((id) => dispatchedIds.includes(id)))
  check('new stale/no-inventory tasks dispatched', [staleTaskId, noInventoryTaskId].every((id) => dispatchedIds.includes(id)))

  r = await api('GET', '/inventory?warehouseId=2')
  eq('B 商品02 stock after dispatches', inventoryRow(r.data, 2).availableQty, 6)
  eq('B 商品03 stock after dispatches', inventoryRow(r.data, 3).availableQty, 24)
  eq('B 商品03 no longer stale', inventoryRow(r.data, 3).stale, false)
  eq('B 商品08 stock after dispatches', inventoryRow(r.data, 8).availableQty, 0)

  r = await api('POST', '/replenishments', {
    requestNo: 'E2E-REP-001',
    warehouseId: 2,
    source: 'e2e',
    items: [{ goodsId: 2, quantity: 1 }]
  })
  eq('duplicate replenishment requestNo -> 409', r.status, 409)

  // Strict FIFO: 10 units fulfil only the first 10-unit task.
  r = await api('POST', '/replenishments', {
    requestNo: 'E2E-REP-FIFO-1',
    warehouseId: 2,
    source: 'e2e',
    items: [{ goodsId: 7, quantity: 10 }]
  })
  eq('partial FIFO dispatch count', r.data.dispatchedTasks.length, 1)
  eq('partial FIFO dispatches oldest task', r.data.dispatchedTasks[0].id, fifoTaskA)

  r = await api('GET', `/pick-tasks/${fifoTaskB}`)
  eq('second FIFO task still pending', r.data.status, 'PENDING')

  r = await api('POST', '/replenishments', {
    requestNo: 'E2E-REP-FIFO-2',
    warehouseId: 2,
    source: 'e2e',
    items: [{ goodsId: 7, quantity: 1 }]
  })
  eq('second FIFO dispatch count', r.data.dispatchedTasks.length, 1)
  eq('second FIFO dispatches remaining task', r.data.dispatchedTasks[0].id, fifoTaskB)

  r = await api('POST', '/replenishments', {
    requestNo: 'E2E-REP-INSUFFICIENT',
    warehouseId: 2,
    source: 'e2e',
    items: [{ goodsId: 5, quantity: 1 }]
  })
  eq('insufficient task dispatched after replenishment', r.data.dispatchedTasks.map((t) => t.id), [insufficientTaskId])

  // ------------------------------------------------------------------- orders
  r = await api('POST', '/orders', { goodsId: 1, quantity: 2, customerName: 'E2E-CUSTOMER' })
  eq('order with fresh stock -> DISPATCHED', r.data.status, 'DISPATCHED')
  eq('order task -> DISPATCHED', r.data.taskStatus, 'DISPATCHED')
  eq('order auto-picks warehouse A', r.data.warehouseId, 1)
  const normalOrder = r.data
  const normalOrderTaskId = r.data.taskId

  r = await api('GET', '/inventory?warehouseId=1')
  eq('order deducted inventory A 商品01', inventoryRow(r.data, 1).availableQty, 45)

  r = await api('POST', `/pick-tasks/${normalOrderTaskId}/claim`, { pickerName: 'E2E-PICKER' })
  eq('claim task succeeds', r.status, 200)
  eq('task claimed by picker', r.data.claimedBy, 'E2E-PICKER')

  r = await api('GET', `/orders/${normalOrder.id}`)
  eq('order -> PICKING after claim', r.data.status, 'PICKING')

  r = await api('POST', `/pick-tasks/${normalOrderTaskId}/result`, { result: 'FULFILLED' })
  eq('complete task -> FULFILLED', r.data.status, 'FULFILLED')

  r = await api('GET', `/orders/${normalOrder.id}`)
  eq('order -> PICKED after completion', r.data.status, 'PICKED')

  // Stockout path.
  r = await api('POST', '/orders', { goodsId: 4, quantity: 1, customerName: 'E2E-CUSTOMER-STOCKOUT' })
  eq('stockout order initially dispatched', r.data.status, 'DISPATCHED')
  const stockoutOrder = r.data
  await api('POST', `/pick-tasks/${stockoutOrder.taskId}/claim`, { pickerName: 'E2E-PICKER' })
  r = await api('POST', `/pick-tasks/${stockoutOrder.taskId}/result`, { result: 'STOCKOUT' })
  eq('complete task -> STOCKOUT', r.data.status, 'STOCKOUT')
  r = await api('GET', `/orders/${stockoutOrder.id}`)
  eq('order -> STOCKOUT after completion', r.data.status, 'STOCKOUT')

  // Pending order -> replenishment -> dispatch.
  r = await api('POST', '/orders', {
    goodsId: 2,
    quantity: 100,
    customerName: 'E2E-CUSTOMER-PENDING',
    warehouseId: 2
  })
  eq('order without enough stock -> PENDING', [r.data.status, r.data.taskStatus], ['PENDING', 'PENDING'])
  const pendingOrder = r.data

  r = await api('POST', '/replenishments', {
    requestNo: 'E2E-REP-ORDER',
    warehouseId: 2,
    source: 'e2e',
    items: [{ goodsId: 2, quantity: 100 }]
  })
  eq('pending order task auto-dispatched', r.data.dispatchedTasks.map((t) => t.id), [pendingOrder.taskId])

  r = await api('GET', `/orders/${pendingOrder.id}`)
  eq('pending order -> DISPATCHED after replenishment', r.data.status, 'DISPATCHED')

  await api('POST', `/pick-tasks/${pendingOrder.taskId}/claim`, { pickerName: 'E2E-PICKER' })
  r = await api('POST', `/pick-tasks/${pendingOrder.taskId}/result`, { result: 'FULFILLED' })
  eq('pending order task completed', r.data.status, 'FULFILLED')
  r = await api('GET', `/orders/${pendingOrder.id}`)
  eq('pending order -> PICKED', r.data.status, 'PICKED')

  // -------------------------------------------------------------- websocket
  const events = []
  const ws = new WebSocket(WS_URL)
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WebSocket open timeout')), 5000)
    ws.onopen = () => {
      clearTimeout(timer)
      resolve()
    }
    ws.onerror = () => {
      clearTimeout(timer)
      reject(new Error('WebSocket connection failed'))
    }
    ws.onmessage = (message) => events.push(JSON.parse(message.data))
  })
  check('WebSocket /backend/ws/picking connected', true)

  async function waitForEvent(type, predicate = () => true, timeout = 8000) {
    const start = Date.now()
    while (Date.now() - start < timeout) {
      const event = events.find((item) => item.type === type && predicate(item))
      if (event) return event
      await sleep(100)
    }
    throw new Error(`timeout waiting for ${type}`)
  }

  r = await api('POST', '/orders', { goodsId: 6, quantity: 1, customerName: 'E2E-CUSTOMER-WS' })
  const wsOrder = r.data
  eq('WebSocket order dispatched', wsOrder.status, 'DISPATCHED')
  const dispatchedEvent = await waitForEvent(
    'TASK_DISPATCHED',
    (event) => event.data.orderId === wsOrder.id
  )
  eq('TASK_DISPATCHED event orderNo', dispatchedEvent.data.orderNo, wsOrder.orderNo)

  await api('POST', `/pick-tasks/${wsOrder.taskId}/claim`, { pickerName: 'E2E-WS-PICKER' })
  const claimedEvent = await waitForEvent(
    'TASK_CLAIMED',
    (event) => event.data.taskId === wsOrder.taskId
  )
  eq('TASK_CLAIMED event picker', claimedEvent.data.pickerName, 'E2E-WS-PICKER')

  await api('POST', `/pick-tasks/${wsOrder.taskId}/result`, { result: 'FULFILLED' })
  const completedEvent = await waitForEvent(
    'TASK_COMPLETED',
    (event) => event.data.taskId === wsOrder.taskId
  )
  eq('TASK_COMPLETED event result', completedEvent.data.result, 'FULFILLED')

  r = await api('GET', `/orders/${wsOrder.id}`)
  eq('WebSocket order -> PICKED', r.data.status, 'PICKED')
  ws.close()

  // ------------------------------------------------------------------ final
  r = await api('GET', '/dashboard/summary')
  eq('final pending count', r.data.pending, 0)
  eq('final stale inventory count', r.data.staleInventory, 0)
  eq('final total tasks', r.data.totalTasks, 17)

  r = await api('GET', '/pick-tasks?size=100')
  eq('task list total matches dashboard', r.data.totalElements, 17)

  console.log('')
  console.log(`RESULT passed=${passed} failed=${failed}`)
  if (failed > 0) {
    console.log(JSON.stringify(failures, null, 2))
    process.exitCode = 1
  }
}

main().catch((error) => {
  console.error('FATAL', error)
  process.exitCode = 1
})
