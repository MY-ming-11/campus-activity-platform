// 这一行必须在 createWebHistory() 之前执行，原因见该文件顶部注释
import '@/utils/historyPatch'
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/', redirect: '/activities' },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue')
  },
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      {
        path: 'activities',
        name: 'activities',
        component: () => import('@/views/ActivityListView.vue')
      },
      {
        path: 'activities/:id',
        name: 'activity-detail',
        component: () => import('@/views/ActivityDetailView.vue')
      },
      {
        path: 'my-signups',
        name: 'my-signups',
        component: () => import('@/views/MySignupsView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'checkin',
        name: 'checkin',
        component: () => import('@/views/CheckinView.vue'),
        meta: { requiresAuth: true }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/AdminDashboardView.vue')
      },
      {
        path: 'activities',
        name: 'admin-activities',
        component: () => import('@/views/admin/AdminActivityListView.vue')
      },
      {
        path: 'activities/new',
        name: 'admin-activity-new',
        component: () => import('@/views/admin/AdminActivityFormView.vue')
      },
      {
        path: 'activities/:id/edit',
        name: 'admin-activity-edit',
        component: () => import('@/views/admin/AdminActivityFormView.vue')
      },
      {
        path: 'activities/:id/checkins',
        name: 'admin-activity-checkins',
        component: () => import('@/views/admin/AdminCheckinConsoleView.vue')
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/activities' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const user = useUserStore()

  if (to.meta.requiresAuth && !user.logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !user.isAdmin) {
    ElMessage.error('这个页面需要管理员权限')
    return { path: '/activities' }
  }
  return true
})

export default router
