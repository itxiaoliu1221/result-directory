import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import TaskView from '../views/TaskView.vue'
import PendingView from '../views/PendingView.vue'
import ReplenishmentView from '../views/ReplenishmentView.vue'
import InventoryView from '../views/InventoryView.vue'
import ShopView from '../views/ShopView.vue'
import PdaView from '../views/PdaView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/tasks', name: 'tasks', component: TaskView },
    { path: '/pending', name: 'pending', component: PendingView },
    { path: '/replenishment', name: 'replenishment', component: ReplenishmentView },
    { path: '/inventory', name: 'inventory', component: InventoryView },
    { path: '/shop', name: 'shop', component: ShopView },
    { path: '/pda', name: 'pda', component: PdaView }
  ]
})

export default router
