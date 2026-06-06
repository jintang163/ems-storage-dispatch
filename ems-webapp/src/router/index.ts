import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '实时监控' }
  },
  {
    path: '/device',
    name: 'Device',
    component: () => import('@/views/Device.vue'),
    meta: { title: '设备管理' }
  },
  {
    path: '/price',
    name: 'Price',
    component: () => import('@/views/Price.vue'),
    meta: { title: '电价配置' }
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/History.vue'),
    meta: { title: '历史数据' }
  },
  {
    path: '/battery-config',
    name: 'BatteryConfig',
    component: () => import('@/views/BatteryConfig.vue'),
    meta: { title: '电池参数配置' }
  },
  {
    path: '/battery-degradation',
    name: 'BatteryDegradation',
    component: () => import('@/views/BatteryDegradation.vue'),
    meta: { title: '电池衰减模型' }
  },
  {
    path: '/transformer-demand',
    name: 'TransformerDemand',
    component: () => import('@/views/TransformerDemand.vue'),
    meta: { title: '变压器需量管理' }
  },
  {
    path: '/strategy-config',
    name: 'StrategyConfig',
    component: () => import('@/views/StrategyConfig.vue'),
    meta: { title: '策略配置' }
  },
  {
    path: '/dispatch-plan',
    name: 'DispatchPlan',
    component: () => import('@/views/DispatchPlan.vue'),
    meta: { title: '调度计划' }
  },
  {
    path: '/realtime-control',
    name: 'RealtimeControl',
    component: () => import('@/views/RealtimeControl.vue'),
    meta: { title: '实时控制' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
