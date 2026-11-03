import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import Layout from '@/layout/index.vue'

/**
 * 学员端路由表
 * 说明：学员端仅面向普通员工，所有页面登录后即可访问，不做角色分组限制；
 * 数据隔离由后端在 Service 层以登录用户为边界强制保证。
 */
const routes = [
  {
    // 登录页（无需登录）
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    // 主框架：顶部品牌栏 + 左侧菜单 + 内容区
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '我的任务', icon: 'HomeFilled' }
      },
      {
        // 学习看板：指标卡 + ECharts 图表
        path: 'analysis',
        name: 'Analysis',
        component: () => import('@/views/analysis/index.vue'),
        meta: { title: '学习看板', icon: 'DataAnalysis' }
      },
      {
        // 课程中心：浏览课程并自主报名
        path: 'courses',
        name: 'Courses',
        component: () => import('@/views/course/index.vue'),
        meta: { title: '课程中心', icon: 'Collection' }
      },
      {
        // 课程详情（由课程卡片进入，不在菜单中展示，高亮归位到「课程中心」）
        path: 'courses/:id',
        name: 'CourseDetail',
        component: () => import('@/views/course/detail.vue'),
        meta: { title: '课程详情', hidden: true, activeMenu: '/courses' }
      },
      {
        // 培训日历：月历展示任务截止与考试节点
        path: 'calendar',
        name: 'Calendar',
        component: () => import('@/views/calendar/index.vue'),
        meta: { title: '培训日历', icon: 'Calendar' }
      },
      {
        // 错题本：错题统计 + 列表
        path: 'wrong-book',
        name: 'WrongBook',
        component: () => import('@/views/wrongbook/index.vue'),
        meta: { title: '错题本', icon: 'WarningFilled' }
      },
      {
        // 错题练习（单题答题卡，高亮归位到「错题本」）
        path: 'wrong-book/practice',
        name: 'WrongPractice',
        component: () => import('@/views/wrongbook/practice.vue'),
        meta: { title: '错题练习', hidden: true, activeMenu: '/wrong-book' }
      },
      {
        // 资料中心：汇总已授权课程资料
        path: 'materials',
        name: 'Materials',
        component: () => import('@/views/material/index.vue'),
        meta: { title: '资料中心', icon: 'FolderOpened' }
      },
      {
        // 需求申报：提交培训诉求并跟踪处理结果（仅展示本人数据）
        path: 'demands',
        name: 'Demands',
        component: () => import('@/views/demand/index.vue'),
        meta: { title: '需求申报', icon: 'ChatLineSquare' }
      },
      {
        // 线下培训签到：查看可签到场次并输入签到码完成电子签到
        path: 'offline',
        name: 'OfflineSign',
        component: () => import('@/views/offline/index.vue'),
        meta: { title: '线下培训签到', icon: 'Checked' }
      },
      {
        // 消息中心：独立消息页面
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/message/index.vue'),
        meta: { title: '消息中心', icon: 'Bell' }
      },
      {
        // 帮助中心：纯静态内容
        path: 'help',
        name: 'Help',
        component: () => import('@/views/help/index.vue'),
        meta: { title: '帮助中心', icon: 'QuestionFilled' }
      },
      {
        // 任务详情（由任务列表进入，不在菜单中展示）
        path: 'task/:id',
        name: 'TaskDetail',
        component: () => import('@/views/task/detail.vue'),
        meta: { title: '任务详情', hidden: true, activeMenu: '/dashboard' }
      },
      {
        // 在线学习（由任务进入）
        path: 'learn/:taskId/:videoId?',
        name: 'LearnPlay',
        component: () => import('@/views/learn/play.vue'),
        meta: { title: '在线学习', hidden: true, activeMenu: '/dashboard' }
      },
      {
        // 考试须知（由任务进入）
        path: 'exam/enter/:taskId',
        name: 'ExamEnter',
        component: () => import('@/views/exam/enter.vue'),
        meta: { title: '考试须知', hidden: true, activeMenu: '/dashboard' }
      },
      {
        // 在线答题（全屏专注模式：不显示左侧菜单）
        path: 'exam/test/:recordId',
        name: 'ExamTest',
        component: () => import('@/views/exam/test.vue'),
        meta: { title: '在线答题', hidden: true, fullscreen: true }
      },
      {
        // 成绩与解析
        path: 'exam/result/:recordId',
        name: 'ExamResult',
        component: () => import('@/views/exam/result.vue'),
        meta: { title: '考试成绩', hidden: true, activeMenu: '/dashboard' }
      },
      {
        // 个人资料
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人资料', icon: 'User' }
      },
      {
        // 学习记录 + 成绩单
        path: 'profile/record',
        name: 'ProfileRecord',
        component: () => import('@/views/profile/record.vue'),
        meta: { title: '学习记录', icon: 'Tickets' }
      },
      {
        // 培训档案 / 证书
        path: 'profile/certificate',
        name: 'ProfileCertificate',
        component: () => import('@/views/profile/certificate.vue'),
        meta: { title: '培训档案', icon: 'Medal' }
      }
    ]
  },
  {
    // 兜底：未匹配路由统一回到工作台
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：登录校验
router.beforeEach((to, from, next) => {
  const token = getToken()

  // 已登录用户访问登录页 → 直接进入工作台
  if (to.path === '/login') {
    if (token) {
      next('/dashboard')
      return
    }
    next()
    return
  }

  // 未登录访问受保护页面 → 跳转登录页
  if (!token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  next()
})

// 全局后置钩子：设置浏览器标题
router.afterEach((to) => {
  const baseTitle = import.meta.env.VITE_APP_TITLE || 'GMP培训学习中心'
  document.title = to.meta?.title ? `${to.meta.title} - ${baseTitle}` : baseTitle
})

export default router
