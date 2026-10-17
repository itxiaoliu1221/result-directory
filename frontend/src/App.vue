<template>
  <el-container class="app-shell">
    <el-aside width="236px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">W</div>
        <div>
          <h1>拣货调度</h1>
          <p>Pick Dispatch</p>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="nav-menu">
        <el-menu-item-group title="角色入口">
          <el-menu-item index="/">
            <span>仪表盘</span>
            <span class="nav-role">运营</span>
          </el-menu-item>
          <el-menu-item index="/shop">
            <span>用户下单</span>
            <span class="nav-role">用户</span>
          </el-menu-item>
          <el-menu-item index="/pda">
            <span>PDA 拣货</span>
            <span class="nav-role">仓库</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="运营管理">
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
        </el-menu-item-group>
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
          <p class="muted">{{ pageSubtitle }}</p>
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
  '/shop': '用户下单',
  '/pda': 'PDA 拣货',
  '/tasks': '任务管理',
  '/pending': '待下发队列',
  '/replenishment': '补货 / 同步',
  '/inventory': '库存'
}
const subtitles = {
  '/': '运营视角：库存、任务与履约全局',
  '/shop': '用户视角：下单与订单状态',
  '/pda': '仓库视角：实时任务与认领',
  '/tasks': '运营视角：任务创建、下发与结果回传',
  '/pending': '运营视角：等待补货或库存同步的任务',
  '/replenishment': '运营视角：补货后自动唤醒待下发任务',
  '/inventory': '运营视角：库存快照与低库存'
}
const pageTitle = computed(() => titles[route.path] || '拣货调度')
const pageSubtitle = computed(() => subtitles[route.path] || '库存快照驱动 · 波次前置校验')

onMounted(connectRealtime)
</script>
