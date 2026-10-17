<template>
  <div class="page">
    <el-row :gutter="14" class="kpi-row">
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card">
          <div class="kpi-label">任务总数</div>
          <div class="kpi-value">{{ summary.totalTasks }}</div>
          <div class="kpi-foot">已拣出 {{ summary.fulfilled }} · 缺货 {{ summary.stockout }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card">
          <div class="kpi-label">待下发</div>
          <div class="kpi-value" style="color: var(--warning)">{{ summary.pending }}</div>
          <div class="kpi-foot">等待补货 / 库存同步后自动下发</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card">
          <div class="kpi-label">已下发</div>
          <div class="kpi-value" style="color: var(--success)">{{ summary.dispatched }}</div>
          <div class="kpi-foot">库存已预占，等待拣货结果回传</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card">
          <div class="kpi-label">库存快照</div>
          <div class="kpi-value">{{ summary.inventoryRows }}</div>
          <div class="kpi-foot">
            过期 {{ summary.staleInventory }} 条 · 过期任务不会直接下发
          </div>
        </div>
      </el-col>
    </el-row>

    <section class="panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">待下发队列</h3>
          <p class="panel-subtitle">库存不足或快照过期的任务会在这里排队</p>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <div class="panel-body">
        <el-table v-if="summary.recentPending.length" :data="summary.recentPending" stripe>
          <el-table-column prop="taskNo" label="任务号" min-width="190">
            <template #default="{ row }"><span class="task-no">{{ row.taskNo }}</span></template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="130" />
          <el-table-column prop="goodsName" label="货物" min-width="110" />
          <el-table-column prop="requiredQty" label="数量" width="80" />
          <el-table-column label="待下发原因" min-width="120">
            <template #default="{ row }">
              <span class="reason-text">{{ reasonLabel(row.reason) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无待下发任务" :image-size="80" />
      </div>
    </section>

    <section class="panel section-gap">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">库存关注</h3>
          <p class="panel-subtitle">过期快照与低库存会优先展示</p>
        </div>
        <el-button text type="primary" @click="$router.push('/inventory')">查看全部</el-button>
      </div>
      <div class="panel-body">
        <el-table v-if="attentionRows.length" :data="attentionRows">
          <el-table-column prop="warehouseName" label="仓库" min-width="130" />
          <el-table-column prop="goodsName" label="货物" min-width="110" />
          <el-table-column label="可用库存" width="100">
            <template #default="{ row }">
              <span :class="{ 'low-stock': row.availableQty < 5 }">{{ row.availableQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最近同步" min-width="170">
            <template #default="{ row }">{{ formatTime(row.syncedAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.stale" type="warning" effect="light">快照过期</el-tag>
              <el-tag v-else-if="row.availableQty < 5" type="danger" effect="light">低库存</el-tag>
              <el-tag v-else type="success" effect="light">正常</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无库存数据" :image-size="80" />
      </div>
    </section>

    <section class="panel section-gap">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">下发流程</h3>
          <p class="panel-subtitle">把缺货拦截在拣货员出发之前</p>
        </div>
      </div>
      <div class="panel-body">
        <div class="flow-grid">
          <div class="flow-step">
            <span>1</span>
            <h4>查快照</h4>
            <p>创建任务时读取该仓库 + 货物的可用库存与同步时间。</p>
          </div>
          <div class="flow-step">
            <span>2</span>
            <h4>立即下发</h4>
            <p>库存足够且快照未过期，创建已下发任务并预占库存。</p>
          </div>
          <div class="flow-step">
            <span>3</span>
            <h4>进入队列</h4>
            <p>库存不足、无库存或快照过期，任务进入待下发队列。</p>
          </div>
          <div class="flow-step">
            <span>4</span>
            <h4>补货重扫</h4>
            <p>补货/同步后按 FIFO 扫描，满足条件的任务自动下发并通知。</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDashboard, fetchInventory } from '../api'
import { formatTime, reasonLabel } from '../utils/format'

const loading = ref(false)
const summary = ref({
  totalTasks: 0,
  pending: 0,
  dispatched: 0,
  fulfilled: 0,
  stockout: 0,
  inventoryRows: 0,
  staleInventory: 0,
  recentPending: []
})
const attentionRows = ref([])

async function load() {
  loading.value = true
  try {
    const [dashboard, inventory] = await Promise.all([fetchDashboard(), fetchInventory()])
    summary.value = dashboard
    attentionRows.value = inventory
      .filter((row) => row.stale || row.availableQty < 5)
      .slice(0, 6)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
