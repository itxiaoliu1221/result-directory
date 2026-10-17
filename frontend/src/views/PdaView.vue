<template>
  <div class="pda-page">
    <div class="pda-shell">
      <header class="pda-header">
        <div class="pda-top">
          <div class="pda-brand">
            <span class="pda-live" :class="{ online: realtime.connected }"></span>
            仓库 PDA
          </div>
          <div class="pda-links">
            <router-link to="/shop">商城</router-link>
            <router-link to="/">调度台</router-link>
          </div>
        </div>

        <div class="pda-picker">
          <label>拣货员</label>
          <el-input v-model="pickerName" size="small" maxlength="20" />
          <span class="pda-conn">{{ realtime.connected ? '在线' : '重连中' }}</span>
        </div>

        <div class="pda-summary">
          <div>
            <strong>{{ unclaimed.length }}</strong>
            <span>待认领</span>
          </div>
          <div>
            <strong>{{ mine.length }}</strong>
            <span>我的任务</span>
          </div>
          <div>
            <strong>{{ realtime.connected ? '实时' : '轮询' }}</strong>
            <span>通知方式</span>
          </div>
        </div>
      </header>

      <main class="pda-body">
        <div class="pda-section-head">
          <h3>待认领</h3>
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        </div>

        <div v-if="unclaimed.length" class="pda-task-list">
          <article v-for="task in unclaimed" :key="task.id" class="pda-task">
            <div class="pda-task-head">
              <span class="task-no">{{ task.orderNo || task.taskNo }}</span>
              <el-tag type="warning" effect="light">待认领</el-tag>
            </div>
            <div class="pda-task-goods">
              {{ task.goodsName }}
              <strong>× {{ task.requiredQty }}</strong>
            </div>
            <div class="pda-task-meta">{{ task.warehouseName }} · {{ task.category }}</div>
            <div class="pda-task-meta">下发时间：{{ formatTime(task.dispatchedAt) }}</div>
            <el-button type="primary" class="pda-full-btn" @click="claim(task)">认领任务</el-button>
          </article>
        </div>
        <el-empty v-else description="暂无待认领任务" :image-size="70" />

        <div class="pda-section-head">
          <h3>我的任务</h3>
        </div>

        <div v-if="mine.length" class="pda-task-list">
          <article v-for="task in mine" :key="task.id" class="pda-task mine">
            <div class="pda-task-head">
              <span class="task-no">{{ task.orderNo || task.taskNo }}</span>
              <el-tag type="success" effect="light">拣货中</el-tag>
            </div>
            <div class="pda-task-goods">
              {{ task.goodsName }}
              <strong>× {{ task.requiredQty }}</strong>
            </div>
            <div class="pda-task-meta">{{ task.warehouseName }} · {{ task.category }}</div>
            <div class="pda-task-meta">认领时间：{{ formatTime(task.claimedAt) }}</div>
            <div class="pda-actions">
              <el-button type="success" class="pda-full-btn" @click="complete(task, 'FULFILLED')">
                拣出完成
              </el-button>
              <el-button type="danger" plain class="pda-full-btn" @click="complete(task, 'STOCKOUT')">
                缺货
              </el-button>
            </div>
          </article>
        </div>
        <el-empty v-else description="还没有认领任务" :image-size="70" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { claimTask, completeTask, fetchTasks } from '../api'
import { onRealtime, useRealtime } from '../realtime'
import { formatTime } from '../utils/format'

const realtime = useRealtime()
const pickerName = ref(localStorage.getItem('pickerName') || 'PDA-01')
const tasks = ref([])
const loading = ref(false)
const handledEvents = new Set()
let unsubscribeRealtime = null

const unclaimed = computed(() => tasks.value.filter((task) => !task.claimedBy))
const mine = computed(() => tasks.value.filter((task) => task.claimedBy === pickerName.value))

watch(pickerName, (value) => {
  localStorage.setItem('pickerName', value || 'PDA-01')
})

async function load() {
  loading.value = true
  try {
    const result = await fetchTasks({ status: 'DISPATCHED', page: 0, size: 100 })
    tasks.value = result.content
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function claim(task) {
  const name = (pickerName.value || '').trim()
  if (!name) {
    ElMessage.warning('请先填写拣货员名称')
    return
  }
  try {
    await claimTask(task.id, name)
    ElMessage.success('认领成功，开始拣货')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
    await load()
  }
}

async function complete(task, result) {
  try {
    await completeTask(task.id, result)
    ElMessage.success(result === 'FULFILLED' ? '已回传拣出完成' : '已回传缺货')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function handleRealtime(event) {
  if (!event || handledEvents.has(event.eventId)) return
  handledEvents.add(event.eventId)
  if (handledEvents.size > 200) {
    handledEvents.clear()
    handledEvents.add(event.eventId)
  }

  if (event.type === 'TASK_DISPATCHED') {
    const data = event.data || {}
    ElNotification({
      title: '新拣货任务待认领',
      message: `${data.orderNo || data.taskNo || ''} · ${data.goodsName || ''} × ${data.quantity || ''}`,
      type: 'success',
      duration: 6000
    })
    load()
  } else if (event.type === 'TASK_CLAIMED' || event.type === 'TASK_COMPLETED') {
    load()
  }
}

onMounted(async () => {
  await load()
  unsubscribeRealtime = onRealtime(handleRealtime)
})

onBeforeUnmount(() => {
  if (unsubscribeRealtime) unsubscribeRealtime()
})
</script>
