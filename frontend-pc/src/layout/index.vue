<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <el-icon size="24"><Reading /></el-icon>
        <span v-show="!isCollapse" class="logo-text">GMP培训系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <!-- 单级菜单：parent path 是 '/' 时需特殊处理，避免拼出 '//dashboard' 这样的双斜杠 -->
          <el-menu-item
            v-if="route.children && route.children.length === 1"
            :index="resolveMenuPath(route.path, route.children[0].path)"
          >
            <el-icon>
              <component :is="route.children[0].meta?.icon || route.meta?.icon" />
            </el-icon>
            <template #title>{{ route.children[0].meta?.title }}</template>
          </el-menu-item>
          <!-- 多级菜单 -->
          <el-sub-menu v-else :index="route.path">
            <template #title>
              <el-icon>
                <component :is="route.meta?.icon" />
              </el-icon>
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in route.children"
              :key="child.path"
              :index="resolveMenuPath(route.path, child.path)"
            >
              <el-icon>
                <component :is="child.meta?.icon" />
              </el-icon>
              <template #title>{{ child.meta?.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <!-- 右侧内容区 -->
    <el-container>
      <!-- 顶部导航栏 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <!-- 面包屑 -->
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="item in breadcrumbs"
              :key="item.path"
            >
              {{ item.meta?.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- 下拉菜单：显式指定 trigger="click"，避免默认 hover 模式导致用户点击时菜单关闭 -->
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" icon="UserFilled" />
              <span class="username">{{ userInfo?.username || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isCollapse = ref(false)

// 当前用户信息
const userInfo = computed(() => {
  const info = localStorage.getItem('userInfo')
  return info ? JSON.parse(info) : null
})

// 当前登录用户的角色编码列表
const userRoles = computed(() => {
  const info = localStorage.getItem('userInfo')
  if (!info) return []
  try {
    const roles = JSON.parse(info).roles
    return Array.isArray(roles) ? roles : []
  } catch (e) {
    return []
  }
})

// 当前激活菜单
const activeMenu = computed(() => route.path)

// 判断当前用户是否具备某路由所需的角色（未配置 roles 表示管理端登录即可见）
function canSeeRoute(meta) {
  // 普通员工仅允许在学员端学习，管理端所有菜单均隐藏
  if (userRoles.value.includes('EMPLOYEE')) return false
  const required = meta?.roles
  if (!required || required.length === 0) return true
  return userRoles.value.some((role) => required.includes(role))
}

// 过滤出当前角色可见的菜单路由（含子菜单按角色过滤）
const menuRoutes = computed(() => {
  return router.options.routes
    .filter((r) => {
      // 排除登录页、隐藏路由与非布局路由
      if (r.path === '/login' || r.meta?.hidden || !r.component) return false
      // 父级菜单需满足角色要求
      return canSeeRoute(r.meta)
    })
    .map((r) => {
      // 无子菜单的路由直接返回
      if (!r.children) return r
      // 子菜单同样按角色过滤，避免出现"看得到但点不进"的菜单项
      return { ...r, children: r.children.filter((c) => canSeeRoute(c.meta)) }
    })
    // 过滤掉角色可见子菜单为空的分组
    .filter((r) => !r.children || r.children.length > 0)
})

// 面包屑
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(
    (item) => item.meta && item.meta.title
  )
  return matched
})

// 切换侧边栏折叠
function toggleCollapse() {
  isCollapse.value = !isCollapse.value
}

/**
 * 拼接菜单完整路由路径
 * <p>
 * 直接 route.path + '/' + child.path 在 parent 为 '/' 时会拼出 "//dashboard" 这样的双斜杠，
 * 触发 Vue Router 警告并导致路由匹配异常；用 URL 规范化规则处理：
 *   - child.path 已经以 '/' 开头：直接返回（child 已是绝对路径）
 *   - parent 是 '/' 且 child 是相对路径：返回 '/' + child
 *   - 其它情况：返回 parent + '/' + child（不会产生双斜杠）
 */
function resolveMenuPath(parent, child) {
  if (child.startsWith('/')) return child
  if (parent === '/') return '/' + child
  return parent + '/' + child
}

// 下拉菜单命令
function handleCommand(command) {
  switch (command) {
    case 'profile':
      // 跳转个人中心（资料维护与自助改密）
      router.push('/profile')
      break
    case 'logout':
      ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        router.push('/login')
      })
      break
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow-x: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  gap: 8px;
  border-bottom: 1px solid #3a4a5c;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.el-menu {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #666;
}

.collapse-btn:hover {
  color: #409eff;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #333;
}

.username {
  font-size: 14px;
}

.layout-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

/* 路由过渡动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
