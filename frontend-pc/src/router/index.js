import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/layout/index.vue'

/**
 * 各功能模块允许访问的角色编码分组（与后端 SaTokenConfig 的拦截规则保持一致）
 * 未配置 roles 的路由表示所有已登录用户均可访问
 */
const ROLE_GROUPS = {
  /** 系统管理：系统管理员 / QA负责人 / 培训管理员 / 部门负责人 */
  SYSTEM: ['SUPER_ADMIN', 'QA_MANAGER', 'TRAINING_ADMIN', 'DEPT_LEADER'],
  /** 仅系统管理员：部门、岗位等基础档案由系统管理员统一维护 */
  SYSTEM_ADMIN: ['SUPER_ADMIN'],
  /** 培训管理：仅管理员级别角色 */
  TRAINING: ['SUPER_ADMIN', 'QA_MANAGER', 'TRAINING_ADMIN'],
  /** 培训任务：管理员级别角色 + 部门负责人（部门负责人仅参与一级审核，只读列表与审核接口） */
  TRAINING_REVIEW: ['SUPER_ADMIN', 'QA_MANAGER', 'TRAINING_ADMIN', 'DEPT_LEADER'],
  /** 考试管理：仅管理员级别角色 */
  EXAM: ['SUPER_ADMIN', 'QA_MANAGER', 'TRAINING_ADMIN'],
  /** 审计追踪：系统管理员 / QA负责人 */
  AUDIT: ['SUPER_ADMIN', 'QA_MANAGER']
}

/**
 * 读取当前登录用户的角色编码列表（登录时写入 localStorage.userInfo.roles）
 * @returns {string[]} 角色编码数组，未登录或无角色时返回空数组
 */
function getUserRoles() {
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return Array.isArray(info.roles) ? info.roles : []
  } catch (e) {
    return []
  }
}

/**
 * 判断当前用户是否有权访问目标路由
 * @param {object} to 目标路由对象（meta 为各匹配记录合并后的结果）
 * @returns {boolean} 有权访问返回 true
 */
function hasRoutePermission(to) {
  const roles = getUserRoles()
  // 普通员工仅允许在学员端学习，禁止进入管理端任何菜单（即使路由未设 roles 也需排除）
  if (roles.includes('EMPLOYEE')) {
    return false
  }
  const required = to.meta?.roles
  // 未限制角色的路由，管理端登录即可访问（员工端已在上方拦截）
  if (!required || required.length === 0) {
    return true
  }
  return roles.some((role) => required.includes(role))
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      }
    ]
  },
  // 待办中心（一级菜单，位于首页之后）：
  // 聚合待审核任务、待处理需求、待审批计划，内容按登录人角色在 Service 层过滤
  {
    path: '/todo',
    component: Layout,
    redirect: '/todo/index',
    children: [
      {
        path: 'index',
        name: 'Todo',
        component: () => import('@/views/todo/index.vue'),
        meta: { title: '待办中心', icon: 'Bell' }
      }
    ]
  },
  // 个人中心（隐藏菜单，登录即可访问）：资料维护与自助改密，消除侧栏下拉的 TODO
  {
    path: '/profile',
    component: Layout,
    redirect: '/profile/index',
    meta: { hidden: true },
    children: [
      {
        path: 'index',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User' }
      }
    ]
  },
  // 系统管理
  {
    path: '/system',
    component: Layout,
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'Setting', roles: ROLE_GROUPS.SYSTEM },
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        // 角色管理：角色基础信息 CRUD 与菜单权限分配，仅限超级管理员操作
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', icon: 'UserFilled', roles: ROLE_GROUPS.SYSTEM_ADMIN }
      },
      {
        path: 'department',
        name: 'SystemDepartment',
        component: () => import('@/views/system/department/index.vue'),
        // 部门档案仅系统管理员可维护（与后端 SaTokenConfig 写操作校验一致）
        meta: { title: '部门管理', icon: 'OfficeBuilding', roles: ROLE_GROUPS.SYSTEM_ADMIN }
      },
      {
        path: 'position',
        name: 'SystemPosition',
        component: () => import('@/views/system/position/index.vue'),
        // 岗位档案仅系统管理员可维护（与后端 SaTokenConfig 写操作校验一致）
        meta: { title: '岗位管理', icon: 'Postcard', roles: ROLE_GROUPS.SYSTEM_ADMIN }
      },
      {
        // 通知公告：管理员手工发布全员/按部门/按岗位公告
        // 权限与管理端后端 SaTokenConfig 收紧规则一致，不含部门负责人
        path: 'notice',
        name: 'SystemNotice',
        component: () => import('@/views/system/notice/index.vue'),
        meta: { title: '通知公告', icon: 'ChatDotSquare', roles: ROLE_GROUPS.TRAINING }
      }
    ]
  },
  // 培训管理
  {
    path: '/training',
    component: Layout,
    redirect: '/training/course',
    // 父级菜单对部门负责人开放（其可见子菜单仅「培训任务」，见下方子路由的 roles 配置）
    meta: { title: '培训管理', icon: 'Reading', roles: ROLE_GROUPS.TRAINING_REVIEW },
    children: [
      {
        path: 'course',
        name: 'TrainingCourse',
        component: () => import('@/views/training/course/index.vue'),
        meta: { title: '课程管理', icon: 'Notebook', roles: ROLE_GROUPS.TRAINING }
      },
      {
        path: 'category',
        name: 'TrainingCategory',
        component: () => import('@/views/training/category/index.vue'),
        meta: { title: '课程分类', icon: 'FolderOpened', roles: ROLE_GROUPS.TRAINING }
      },
      {
        path: 'video',
        name: 'TrainingVideo',
        component: () => import('@/views/training/video/index.vue'),
        meta: { title: '视频课件', icon: 'VideoCamera', roles: ROLE_GROUPS.TRAINING }
      },
      {
        // PDF 课件：课程 PDF 课件的上传、预览、启停与删除
        path: 'pdf',
        name: 'TrainingPdf',
        component: () => import('@/views/training/pdf/index.vue'),
        meta: { title: 'PDF课件', icon: 'Document', roles: ROLE_GROUPS.TRAINING }
      },
      {
        // 培训需求：部门培训需求的登记与处理（采纳/拒绝）
        path: 'demand',
        name: 'TrainingDemand',
        component: () => import('@/views/training/demand/index.vue'),
        meta: { title: '培训需求', icon: 'Notification', roles: ROLE_GROUPS.TRAINING }
      },
      {
        path: 'plan',
        name: 'TrainingPlan',
        component: () => import('@/views/training/plan/index.vue'),
        meta: { title: '培训计划', icon: 'Calendar', roles: ROLE_GROUPS.TRAINING }
      },
      {
        // 线下培训登记与电子签到：场次登记、签到码生成、签到明细与补签
        path: 'offline',
        name: 'TrainingOffline',
        component: () => import('@/views/training/offline/index.vue'),
        meta: { title: '线下培训', icon: 'Location', roles: ROLE_GROUPS.TRAINING }
      },
      {
        // 外部专项培训与资质证书：台账登记、审核、有效期跟踪与导出
        path: 'certificate',
        name: 'TrainingCertificate',
        component: () => import('@/views/training/certificate/index.vue'),
        meta: { title: '资质证书', icon: 'Postcard', roles: ROLE_GROUPS.TRAINING }
      },
      {
        // 员工培训档案：按员工查看学习/考试/证书三合一档案
        path: 'archive',
        name: 'TrainingArchive',
        component: () => import('@/views/training/archive/index.vue'),
        meta: { title: '培训档案', icon: 'Medal', roles: ROLE_GROUPS.TRAINING_REVIEW }
      },
      {
        path: 'task',
        name: 'TrainingTask',
        component: () => import('@/views/training/task/index.vue'),
        // 培训任务同时承载「待我审核」页签，需对部门负责人开放
        meta: { title: '培训任务', icon: 'List', roles: ROLE_GROUPS.TRAINING_REVIEW }
      }
    ]
  },
  // 考试管理
  {
    path: '/exam',
    component: Layout,
    redirect: '/exam/question',
    meta: { title: '考试管理', icon: 'EditPen', roles: ROLE_GROUPS.EXAM },
    children: [
      {
        path: 'question',
        name: 'ExamQuestion',
        component: () => import('@/views/exam/question/index.vue'),
        meta: { title: '题库管理', icon: 'Collection' }
      },
      {
        path: 'paper',
        name: 'ExamPaper',
        component: () => import('@/views/exam/paper/index.vue'),
        meta: { title: '试卷管理', icon: 'Document' }
      }
    ]
  },
  // 审计追踪
  {
    path: '/audit',
    component: Layout,
    redirect: '/audit/log',
    meta: { title: '审计追踪', icon: 'View', roles: ROLE_GROUPS.AUDIT },
    children: [
      {
        path: 'log',
        name: 'AuditLog',
        component: () => import('@/views/audit/log/index.vue'),
        meta: { title: '操作日志', icon: 'Tickets' }
      }
    ]
  },
  // 统计报表
  {
    path: '/report',
    component: Layout,
    redirect: '/report/index',
    meta: { title: '统计报表', icon: 'DataAnalysis' },
    children: [
      {
        path: 'index',
        name: 'Report',
        component: () => import('@/views/report/index.vue'),
        meta: { title: '报表概览', icon: 'TrendCharts' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫 - 登录校验 + 角色权限校验
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  // 登录页直接放行
  if (to.path === '/login') {
    next()
    return
  }
  // 未登录跳转登录页
  if (!token) {
    next('/login')
    return
  }
  // 普通员工（EMPLOYEE）不允许进入管理端任何页面：提示并清除管理端登录态，跳转登录页
  // （员工端与管理端共用账号，但前端各自独立；学员端用 student_token，管理端用 token）
  const roles = getUserRoles()
  if (roles.includes('EMPLOYEE')) {
    // 清除管理端登录态，避免后续自动登录继续报错
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    ElMessage.warning('您的角色是普通员工，请使用学员端（http://localhost:3002）观看学习。如需管理员账号请联系系统管理员')
    next('/login')
    return
  }
  // 已登录但无该路由的访问权限，提示并回退（避免停留在无权页面）
  if (!hasRoutePermission(to)) {
    ElMessage.error('无访问权限，该功能仅对特定角色开放')
    next(from.path && from.path !== to.path ? from.path : '/dashboard')
    return
  }
  next()
})

export default router
