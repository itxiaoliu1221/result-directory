<template>
  <div class="page">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">补货 / 库存同步</h3>
          <p class="panel-subtitle">提交后更新库存快照，并立即扫描同仓库、同货物的待下发任务</p>
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
            <el-form-item label="补货单号">
              <el-input v-model="form.requestNo" placeholder="自动生成" />
            </el-form-item>
            <el-form-item label="来源">
              <el-input v-model="form.source" placeholder="manual / sync" />
            </el-form-item>
            <el-form-item label="操作">
              <el-button type="primary" :loading="submitting" @click="submit">提交补货</el-button>
            </el-form-item>
          </div>
        </el-form>

        <div class="section-gap">
          <div class="toolbar" style="justify-content: space-between">
            <div>
              <strong style="font-size: 14px">补货明细</strong>
              <span class="muted" style="margin-left: 8px">同一货物在一张单中只能出现一次</span>
            </div>
            <el-button text type="primary" @click="addItem">+ 添加一行</el-button>
          </div>

          <div v-for="(item, index) in form.items" :key="index" class="item-row">
            <el-select v-model="item.goodsId" placeholder="选择货物" style="width: 260px">
              <el-option
                v-for="goodsItem in goods"
                :key="goodsItem.id"
                :label="`${goodsItem.name}（${goodsItem.code}）`"
                :value="goodsItem.id"
              />
            </el-select>
            <el-input-number v-model="item.quantity" :min="1" :max="9999" />
            <el-button text type="danger" :disabled="form.items.length === 1" @click="removeItem(index)">
              删除
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="result"
          class="result-banner"
          type="success"
          :closable="false"
          show-icon
          :title="`补货单 ${result.requestNo || '-'} 已处理，自动下发 ${result.dispatchedTasks.length} 个任务`"
        >
          <span v-if="result.dispatchedTasks.length">下方列出了本次触发的已下发任务。</span>
          <span v-else>本次没有满足条件的待下发任务，库存已更新。</span>
        </el-alert>
      </div>
    </section>

    <section v-if="result" class="panel section-gap">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">本次补货结果</h3>
          <p class="panel-subtitle">库存变化与自动下发情况</p>
        </div>
      </div>
      <div class="panel-body">
        <el-table :data="result.items" style="margin-bottom: 16px">
          <el-table-column prop="goodsName" label="货物" min-width="120" />
          <el-table-column prop="replenishedQty" label="补货数量" width="100" />
          <el-table-column prop="remainingQty" label="剩余可用" width="100" />
          <el-table-column prop="dispatchedCount" label="自动下发" width="100" />
        </el-table>

        <el-table v-if="result.dispatchedTasks.length" :data="result.dispatchedTasks" stripe>
          <el-table-column prop="taskNo" label="任务号" min-width="190">
            <template #default="{ row }"><span class="task-no">{{ row.taskNo }}</span></template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="130" />
          <el-table-column prop="goodsName" label="货物" min-width="110" />
          <el-table-column prop="requiredQty" label="数量" width="80" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag type="success" effect="light">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下发时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.dispatchedAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="本次没有自动下发的任务" :image-size="80" />
      </div>
    </section>

    <section class="panel section-gap">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">最近补货记录</h3>
          <p class="panel-subtitle">最近 20 条</p>
        </div>
        <el-button :loading="historyLoading" @click="loadHistory">刷新</el-button>
      </div>
      <div class="panel-body">
        <el-table v-loading="historyLoading" :data="history">
          <el-table-column prop="requestNo" label="补货单号" min-width="190">
            <template #default="{ row }"><span class="task-no">{{ row.requestNo || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="130" />
          <el-table-column prop="source" label="来源" width="100" />
          <el-table-column label="明细" min-width="220">
            <template #default="{ row }">
              {{ row.items.map((item) => `${item.goodsName} × ${item.quantity}`).join('，') }}
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!historyLoading && !history.length" description="暂无补货记录" :image-size="80" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createReplenishment,
  fetchGoods,
  fetchReplenishments,
  fetchWarehouses
} from '../api'
import { formatTime, statusLabel } from '../utils/format'

const warehouses = ref([])
const goods = ref([])
const submitting = ref(false)
const historyLoading = ref(false)
const result = ref(null)
const history = ref([])

const form = reactive({
  warehouseId: null,
  requestNo: nextRequestNo(),
  source: 'manual',
  items: [newItem()]
})

function newItem() {
  return { goodsId: null, quantity: 1 }
}

function nextRequestNo() {
  return `REP-${Date.now()}`
}

function addItem() {
  form.items.push(newItem())
}

function removeItem(index) {
  if (form.items.length > 1) {
    form.items.splice(index, 1)
  }
}

async function submit() {
  if (!form.warehouseId) {
    ElMessage.warning('请选择仓库')
    return
  }
  const items = form.items.filter((item) => item.goodsId && item.quantity > 0)
  if (!items.length) {
    ElMessage.warning('至少填写一条有效补货明细')
    return
  }
  const goodsIds = items.map((item) => item.goodsId)
  if (new Set(goodsIds).size !== goodsIds.length) {
    ElMessage.warning('同一补货单中货物不能重复')
    return
  }

  submitting.value = true
  try {
    result.value = await createReplenishment({
      requestNo: form.requestNo || undefined,
      warehouseId: form.warehouseId,
      source: form.source || 'manual',
      items
    })
    ElMessage.success(`补货完成，自动下发 ${result.value.dispatchedTasks.length} 个任务`)
    form.requestNo = nextRequestNo()
    form.items = [newItem()]
    await loadHistory()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

async function loadMeta() {
  try {
    const [warehouseRows, goodsRows] = await Promise.all([fetchWarehouses(), fetchGoods()])
    warehouses.value = warehouseRows
    goods.value = goodsRows
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    history.value = await fetchReplenishments()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    historyLoading.value = false
  }
}

onMounted(async () => {
  await loadMeta()
  await loadHistory()
})
</script>

<style scoped>
.item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
</style>
