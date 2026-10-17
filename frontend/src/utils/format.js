export const statusMeta = {
  PENDING: { label: '待下发', type: 'warning' },
  DISPATCHED: { label: '已下发', type: 'success' },
  FULFILLED: { label: '已拣出', type: 'info' },
  STOCKOUT: { label: '缺货', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export const reasonLabels = {
  INSUFFICIENT_STOCK: '库存不足',
  SNAPSHOT_STALE: '快照已过期',
  NO_INVENTORY: '无库存记录'
}

export const orderStatusMeta = {
  PENDING: { label: '待分配', type: 'warning' },
  DISPATCHED: { label: '已分配', type: 'primary' },
  PICKING: { label: '拣货中', type: 'success' },
  PICKED: { label: '已完成', type: 'info' },
  STOCKOUT: { label: '缺货', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export function orderStatusLabel(status) {
  return orderStatusMeta[status]?.label || status || '-'
}

export function orderStatusType(status) {
  return orderStatusMeta[status]?.type || 'info'
}

export function statusLabel(status) {
  return statusMeta[status]?.label || status || '-'
}

export function statusType(status) {
  return statusMeta[status]?.type || 'info'
}

export function reasonLabel(reason) {
  return reasonLabels[reason] || reason || '-'
}

export function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

export function waitMinutes(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return Math.max(0, Math.floor((Date.now() - date.getTime()) / 60000))
}
