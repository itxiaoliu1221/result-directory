<template>
  <div class="page">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">创建拣货任务</h3>
          <p class="panel-subtitle">提交后系统立即判断：库存够就下发，不够就进入待下发</p>
        </div>
      </div>
      <div class="panel-body">
        <el-form label-position="top" @submit.prevent>
          <div class="form-grid">
            <el-form-item label="仓库">
              <el-select v-model="form.warehouseId" placeholder="选择仓库" style="width: 100%">
                <el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="货物">
              <el-select v-model="form.goodsId" placeholder="选择货物" style="width: 100%">
                <el-option
                  v-for="item in goods"
                  :key="item.id"
                  :label="`${item.name}（${item.code}）`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="拣货数量">
              <el-input-number v-model="form.requiredQty" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
            <el-form-item label="操作">
              <el-button type="primary" :loading="submitting" @click="handleCreate">创建任务</el-button>
            </el-form-item>
          </div>
        </el-form>

        <el-alert
          v-if="lastResult"
          class="result-banner"
          :type="lastResult.status === 'DISPATCHED' ? 'success' : 'warning'"
          :closable="false"
          show-icon
        >
          <template #title>
            任务 {{ lastResult.taskNo }} 已创建：
            {{ lastResult.status === 'DISPATCHED' ? '库存充足，已立即下发' : `进入待下发（${reasonLabel(lastResult.reason)}）` }}
          </template>
          <template #default>
            <span v-if="lastResult.status === 'DISPATCHED'">
              创建时快照库存 {{ lastResult.snapshotQty }}，快照年龄 {{ lastResult.snapshotAgeMinutes ?? '-' }} 分钟。
            </span>
            <span v-else>
              补货或库存同步后，系统会自动重扫并下发，无需手动操作。
            </span>
          </template>
        </el-alert>
      </div>
    </section>

    <section class="panel section-gap">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">任务列表</h3>
          <p class="panel-subtitle">共 {{ total }} 条任务</p>
        </div>
        <el-button :loading="loading" @click="loadTasks">刷新</el-button>
      </div>
      <div class="panel-body">
        <div class="toolbar">
          <el-select v-model="filters.status" clearable placeholder="全部状态" @change="resetAndLoad">
            <el-option v-for="(meta, key) in statusMeta" :key="key" :label="meta.label" :value="key" />
          </el-select>
          <el-select v-model="filters.warehouseId" clearable placeholder="全部仓库" @change="resetAndLoad">
            <el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-select v-model="filters.goodsId" clearable placeholder="全部货物" @change="resetAndLoad">
            <el-option v-for="item in goods" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-button text @click="resetFilters">重置</el-button>
        </div>

        <el-table v-loading="loading" :data="tasks" stripe>
          <el-table-column prop="taskNo" label="任务号" min-width="190">
            <template #default="{ row }"><span class="task-no">{{ row.taskNo }}</span></template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="130" />
          <el-table-column prop="goodsName" label="货物" min-width="110" />
          <el-table-column prop="requiredQty" label="数量" width="80">
            <template #default="{ row }"><span class="qty-strong">{{ row.requiredQty }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="120">
            <template #default="{ row }">
              <span v-if="row.reason" class="reason-text">{{ reasonLabel(row.reason) }}</span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'DISPATCHED'">
                <el-button link type="success" @click="handleComplete(row, 'FULFILLED')">拣出</el-button>
                <el-button link type="danger" @click="handleComplete(row, 'STOCKOUT')">缺货</el-button>
              </template>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
        </el-table>

        <div style="display: flex; justify-content: flex-end; margin-top: 16px">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadTasks"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  completeTask,
  createTask,
  fetchGoods,
  fetchTasks,
  fetchWarehouses
} from '../api'
import {
  formatTime,
  reasonLabel,
  statusLabel,
  statusMeta,
  statusType
} from '../utils/format'

const warehouses = ref([])
const goods = ref([])
const tasks = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const submitting = ref(false)
const lastResult = ref(null)

const form = reactive({
  warehouseId: null,
  goodsId: null,
  requiredQty: 1
})
const filters = reactive({
  status: '',
  warehouseId: '',
  goodsId: ''
})

async function loadMeta() {
  try {
    const [warehouseRows, goodsRows] = await Promise.all([fetchWarehouses(), fetchGoods()])
    warehouses.value = warehouseRows
    goods.value = goodsRows
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function loadTasks() {
  loading.value = true
  try {
    const result = await fetchTasks({
      status: filters.status || undefined,
      warehouseId: filters.warehouseId || undefined,
      goodsId: filters.goodsId || undefined,
      page: page.value - 1,
      size: size.value
    })
    tasks.value = result.content
    total.value = result.totalElements
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  page.value = 1
  loadTasks()
}

function resetFilters() {
  filters.status = ''
  filters.warehouseId = ''
  filters.goodsId = ''
  resetAndLoad()
}

function handleSizeChange() {
  page.value = 1
  loadTasks()
}

async function handleCreate() {
  if (!form.warehouseId || !form.goodsId) {
    ElMessage.warning('请选择仓库和货物')
    return
  }
  submitting.value = true
  try {
    const result = await createTask({
      warehouseId: form.warehouseId,
      goodsId: form.goodsId,
      requiredQty: form.requiredQty
    })
    lastResult.value = result
    if (result.status === 'DISPATCHED') {
      ElMessage.success('库存充足，任务已下发')
    } else {
      ElMessage.warning(`任务进入待下发：${reasonLabel(result.reason)}`)
    }
    await loadTasks()
    await loadMeta()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

async function handleComplete(row, result) {
  try {
    await completeTask(row.id, result)
    ElMessage.success(result === 'FULFILLED' ? '已回传拣出' : '已回传缺货')
    await loadTasks()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(async () => {
  await loadMeta()
  await loadTasks()
})
</script>
