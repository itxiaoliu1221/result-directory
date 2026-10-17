<template>
  <div class="page">
    <el-alert
      title="库存快照达到 10 分钟会被标记为过期；过期时即使数量足够，任务也会进入待下发，等待补货/同步。"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <section class="panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">库存列表</h3>
          <p class="panel-subtitle">共 {{ rows.length }} 条库存记录</p>
        </div>
        <div class="toolbar" style="margin-bottom: 0">
          <el-select v-model="warehouseId" clearable placeholder="全部仓库" style="width: 170px" @change="load">
            <el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>
      <div class="panel-body">
        <el-table v-loading="loading" :data="rows" stripe>
          <el-table-column prop="warehouseName" label="仓库" min-width="130" />
          <el-table-column prop="goodsName" label="货物" min-width="110" />
          <el-table-column prop="category" label="品类" min-width="100" />
          <el-table-column label="可用库存" width="100">
            <template #default="{ row }">
              <span :class="{ 'low-stock': row.availableQty < 5 }">{{ row.availableQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最近同步" min-width="170">
            <template #default="{ row }">{{ formatTime(row.syncedAt) }}</template>
          </el-table-column>
          <el-table-column label="快照年龄" width="110">
            <template #default="{ row }">{{ row.freshnessMinutes }} 分钟</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.stale" type="warning" effect="light">快照过期</el-tag>
              <el-tag v-else-if="row.availableQty < 5" type="danger" effect="light">低库存</el-tag>
              <el-tag v-else type="success" effect="light">正常</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !rows.length" description="暂无库存数据" :image-size="90" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchInventory, fetchWarehouses } from '../api'
import { formatTime } from '../utils/format'

const warehouses = ref([])
const rows = ref([])
const warehouseId = ref('')
const loading = ref(false)

async function loadMeta() {
  try {
    warehouses.value = await fetchWarehouses()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = await fetchInventory({
      warehouseId: warehouseId.value || undefined
    })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadMeta()
  await load()
})
</script>
