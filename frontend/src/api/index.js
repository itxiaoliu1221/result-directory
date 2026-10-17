import client from './client'

export const fetchWarehouses = () => client.get('/meta/warehouses')
export const fetchGoods = () => client.get('/meta/goods')

export const createTask = (payload) => client.post('/pick-tasks', payload)
export const fetchTasks = (params) => client.get('/pick-tasks', { params })
export const completeTask = (id, result) => client.post(`/pick-tasks/${id}/result`, { result })

export const fetchInventory = (params) => client.get('/inventory', { params })
export const createReplenishment = (payload) => client.post('/replenishments', payload)
export const fetchReplenishments = () => client.get('/replenishments')
export const fetchDashboard = () => client.get('/dashboard/summary')

export const createOrder = (payload) => client.post('/orders', payload)
export const fetchOrders = (params) => client.get('/orders', { params })
export const fetchOrder = (id) => client.get(`/orders/${id}`)
export const claimTask = (id, pickerName) => client.post(`/pick-tasks/${id}/claim`, { pickerName })
