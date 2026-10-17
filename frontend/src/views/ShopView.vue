<template>
  <div class="page shop-page">
    <div class="shop-content">
      <section class="shop-hero">
        <div class="shop-hero-copy">
          <span class="eyebrow">MULTI-WAREHOUSE FULFILLMENT</span>
          <h2>下单后自动匹配有货仓<br />库存不足先排队，补货后自动下发</h2>
          <p>
            系统在创建拣货任务前读取库存快照：库存足够且新鲜就立即下发；
            否则进入待下发队列，补货或同步后自动唤醒，并实时通知仓库 PDA。
          </p>
        </div>
        <div class="shop-stats">
          <div class="shop-stat">
            <strong>{{ orders.length }}</strong>
            <span>最近订单</span>
          </div>
          <div class="shop-stat">
            <strong>{{ pendingCount }}</strong>
            <span>待分配</span>
          </div>
          <div class="shop-stat">
            <strong>{{ activeCount }}</strong>
            <span>履约中</span>
          </div>
        </div>
      </section>

      <div class="shop-grid">
        <section class="panel shop-form-panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">快速下单</h3>
              <p class="panel-subtitle">不选仓库时，后端自动选择有货且快照新鲜的仓</p>
            </div>
          </div>
          <div class="panel-body">
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="收货人">
                <el-input v-model="form.customerName" placeholder="例如：张三" maxlength="64" />
              </el-form-item>
              <el-form-item label="商品">
                <el-select v-model="form.goodsId" placeholder="选择商品" style="width: 100%">
                  <el-option
                    v-for="item in goods"
                    :key="item.id"
                    :label="`${item.name}（${item.code}）`"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="数量">
                <el-input-number v-model="form.quantity" :min="1" :max="999" style="width: 100%" />
              </el-form-item>
              <el-form-item label="发货仓">
                <el-select v-model="form.warehouseId" clearable placeholder="自动选择有货仓" style="width: 100%">
                  <el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-button type="primary" size="large" :loading="submitting" @click="submit">
                提交订单
              </el-button>
            </el-form>

            <el-alert
              v-if="lastOrder"
              class="result-banner"
              :type="lastOrder.status === 'PENDING' ? 'warning' : 'success'"
              :closable="false"
              show-icon
            >
              <template #title>
                订单 {{ lastOrder.orderNo }} 已创建：{{ orderStatusLabel(lastOrder.status) }}
              </template>
              <template #default>
                {{ statusHint(lastOrder.status) }}
              </template>
            </el-alert>
          </div>
        </section>

        <section class="panel shop-orders-panel">
          <div class="panel-header">
            <div>
              <h3 class="panel-title">我的订单</h3>
              <p class="panel-subtitle">状态会随仓库认领、拣货完成实时更新</p>
            </div>
            <el-button :loading="loading" @click="loadOrders">刷新</el-button>
          </div>
          <div class="panel-body">
            <div v-if="orders.length" class="order-list">
              <article v-for="order in orders" :key="order.id" class="order-card">
                <div class="order-card-head">
                  <span class="task-no">{{ order.orderNo }}</span>
                  <el-tag :type="orderStatusType(order.status)" effect="light">
                    {{ orderStatusLabel(order.status) }}
                  </el-tag>
                </div>
                <div class="order-card-main">
                  <div class="order-goods">
                    <strong>{{ order.goodsName }}</strong>
                    <span>× {{ order.quantity }}</span>
                  </div>
                  <div class="muted">
                    {{ order.warehouseName }} · {{ order.customerName }}
                  </div>
                </div>
                <div class="order-hint">{{ statusHint(order.status) }}</div>
                <div class="order-card-foot">
                  <span>任务 {{ order.taskNo || '-' }}</span>
                  <span>{{ formatTime(order.createdAt) }}</span>
                </div>
              </article>
            </div>
            <el-empty v-else description="还没有订单，先下一单试试" :image-size="90" />
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createOrder,
  fetchGoods,
  fetchOrders,
  fetchWarehouses
} from '../api'
import { onRealtime } from '../realtime'
import {
  formatTime,
  orderStatusLabel,
  orderStatusType
} from '../utils/format'

const goods = ref([])
const warehouses = ref([])
const orders = ref([])
const loading = ref(false)
const submitting = ref(false)
const lastOrder = ref(null)
let pollTimer = null
let unsubscribeRealtime = null

const form = reactive({
  customerName: '张三',
  goodsId: null,
  quantity: 1,
  warehouseId: ''
})

const pendingCount = computed(() => orders.value.filter((order) => order.status === 'PENDING').length)
const activeCount = computed(() =>
  orders.value.filter((order) => ['DISPATCHED', 'PICKING'].includes(order.status)).length
)

const statusHints = {
  PENDING: '等待库存或补货；任务分配后会自动通知仓库。',
  DISPATCHED: '已分配到仓库，等待拣货员认领。',
  PICKING: '拣货员已认领，正在拣货。',
  PICKED: '订单已完成。',
  STOCKOUT: '拣货时发现缺货，请联系客服处理。',
  CANCELLED: '订单已取消。'
}

function statusHint(status) {
  return statusHints[status] || '-'
}

async function loadMeta() {
  try {
    const [goodsRows, warehouseRows] = await Promise.all([fetchGoods(), fetchWarehouses()])
    goods.value = goodsRows
    warehouses.value = warehouseRows
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await fetchOrders()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.goodsId) {
    ElMessage.warning('请选择商品')
    return
  }
  submitting.value = true
  try {
    const order = await createOrder({
      goodsId: form.goodsId,
      quantity: form.quantity,
      customerName: form.customerName || undefined,
      warehouseId: form.warehouseId || undefined
    })
    lastOrder.value = order
    if (order.status === 'PENDING') {
      ElMessage.warning('库存不足或快照过期，订单已进入待分配队列')
    } else {
      ElMessage.success('下单成功，拣货任务已下发仓库')
    }
    await loadOrders()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadMeta()
  await loadOrders()
  pollTimer = setInterval(loadOrders, 5000)
  unsubscribeRealtime = onRealtime((event) => {
    if (event.type && event.type.startsWith('TASK_')) {
      loadOrders()
    }
  })
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (unsubscribeRealtime) unsubscribeRealtime()
})
</script>
