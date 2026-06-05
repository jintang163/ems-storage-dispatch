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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
