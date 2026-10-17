import { reactive } from 'vue'

const state = reactive({
  connected: false,
  lastEvent: null,
  events: []
})

const listeners = new Set()
let socket = null
let retryTimer = null
let manualClose = false

function wsUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const base = import.meta.env.BASE_URL.endsWith('/')
    ? import.meta.env.BASE_URL
    : `${import.meta.env.BASE_URL}/`
  return `${protocol}//${window.location.host}${base}ws/picking`
}

function notify(event) {
  listeners.forEach((listener) => {
    try {
      listener(event)
    } catch (error) {
      console.error('realtime listener failed', error)
    }
  })
}

export function connectRealtime() {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return
  }
  manualClose = false
  socket = new WebSocket(wsUrl())

  socket.onopen = () => {
    state.connected = true
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  socket.onmessage = (message) => {
    try {
      const event = JSON.parse(message.data)
      if (!event || !event.eventId) return
      state.lastEvent = event
      state.events.unshift(event)
      if (state.events.length > 50) state.events.pop()
      notify(event)
    } catch (error) {
      console.warn('invalid realtime message', message.data)
    }
  }

  socket.onclose = () => {
    state.connected = false
    if (!manualClose && !retryTimer) {
      retryTimer = setTimeout(() => {
        retryTimer = null
        connectRealtime()
      }, 3000)
    }
  }

  socket.onerror = () => {
    state.connected = false
  }
}

export function disconnectRealtime() {
  manualClose = true
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = null
  }
  if (socket) {
    socket.close()
    socket = null
  }
  state.connected = false
}

export function onRealtime(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function useRealtime() {
  return state
}
