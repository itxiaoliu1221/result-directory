<template>
  <div class="page">
    <el-alert
      title="待下发任务不占用库存；补货或库存同步后，系统会按创建时间先进先出自动下发。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <section class="panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">待下发队列</h3>
          <p class="panel-subtitle">共 {{ tasks.length }} 条任务在等待补货 / 同步</p>
        </div>
        <div>
          <el-button @click="load">刷新</el-button>
          <el-button type="primary" @click="$router.push('/replenishment')">去补货</el-button>
        </div>
      </div>
      <div class="panel-body">
        <el-table v-loading="loading" :data="tasks" stripe>
          <el-table-column prop="taskNo" label="任务号" min-width="190">
            <template #default="{ row }"><span class="task-no">{{ row.taskNo }}</span></template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="130" />
          <el-table-column prop="goodsName" label="货物" min-width="110" />
          <el-table-column prop="requiredQty" label="数量" width="80">
            <template #default="{ row }"><span class="qty-strong">{{ row.requiredQty }}</span></template>
          </el-table-column>
          <el-table-column label="原因" min-width="130">
            <template #default="{ row }">
              <el-tag type="warning" effect="light">{{ reasonLabel(row.reason) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="已等待" width="100">
            <template #default="{ row }">{{ waitMinutes(row.createdAt) }} 分钟</template>
          </el-table-column>
          <el-table-column label="创建时快照年龄" width="140">
            <template #default="{ row }">{{ row.snapshotAgeMinutes ?? '-' }} 分钟</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !tasks.length" description="队列为空" :image-size="90" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTasks } from '../api'
import { formatTime, reasonLabel, waitMinutes } from '../utils/format'

const tasks = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await fetchTasks({ status: 'PENDING', page: 0, size: 100 })
    tasks.value = result.content
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
