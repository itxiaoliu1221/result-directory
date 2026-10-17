<template>
  <router-view v-if="route.meta.standalone" />

  <el-container v-else class="app-shell">
    <el-aside width="228px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">W</div>
        <div>
          <h1>拣货调度</h1>
          <p>Pick Dispatch</p>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="nav-menu">
        <el-menu-item index="/">
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <span>任务管理</span>
        </el-menu-item>
        <el-menu-item index="/pending">
          <span>待下发队列</span>
        </el-menu-item>
        <el-menu-item index="/replenishment">
          <span>补货 / 同步</span>
        </el-menu-item>
        <el-menu-item index="/inventory">
          <span>库存</span>
        </el-menu-item>
        <el-menu-item index="/shop">
          <span>用户下单</span>
        </el-menu-item>
        <el-menu-item index="/pda">
          <span>PDA 拣货</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="pulse-dot"></div>
        <span>库存快照驱动 · 波次前置校验</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <h2>{{ pageTitle }}</h2>
          <p class="muted">库存不足的任务先排队，补货同步后自动下发</p>
        </div>
        <div class="topbar-actions">
          <el-tag :type="realtime.connected ? 'success' : 'warning'" effect="plain" round>
            {{ realtime.connected ? '实时已连接' : '实时重连中' }}
          </el-tag>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { connectRealtime, useRealtime } from './realtime'

const route = useRoute()
const realtime = useRealtime()

const titles = {
  '/': '仪表盘',
  '/tasks': '任务管理',
  '/pending': '待下发队列',
  '/replenishment': '补货 / 同步',
  '/inventory': '库存',
  '/shop': '用户下单',
  '/pda': 'PDA 拣货'
}
const pageTitle = computed(() => titles[route.path] || '拣货调度')

onMounted(connectRealtime)
</script>
