<template>
  <!-- 左侧菜单：浅色底，选中项主色文字 + 浅蓝底 -->
  <aside class="side-menu">
    <el-menu
      :default-active="activeMenu"
      class="menu"
      @select="handleSelect"
    >
      <!-- 我的任务（首页工作台） -->
      <el-menu-item index="/dashboard">
        <el-icon><HomeFilled /></el-icon>
        <span>我的任务</span>
      </el-menu-item>

      <!-- 学习看板：学习数据可视化 -->
      <el-menu-item index="/analysis">
        <el-icon><DataAnalysis /></el-icon>
        <span>学习看板</span>
      </el-menu-item>

      <!-- 课程中心：自主选学与报名 -->
      <el-menu-item index="/courses">
        <el-icon><Collection /></el-icon>
        <span>课程中心</span>
      </el-menu-item>

      <!-- 培训日历：任务截止与考试节点 -->
      <el-menu-item index="/calendar">
        <el-icon><Calendar /></el-icon>
        <span>培训日历</span>
      </el-menu-item>

      <!-- 错题本：错题收集与重做 -->
      <el-menu-item index="/wrong-book">
        <el-icon><WarningFilled /></el-icon>
        <span>错题本</span>
      </el-menu-item>

      <!-- 资料中心：课程资料统一下载 -->
      <el-menu-item index="/materials">
        <el-icon><FolderOpened /></el-icon>
        <span>资料中心</span>
      </el-menu-item>

      <!-- 需求申报：提交培训诉求并跟踪处理结果 -->
      <el-menu-item index="/demands">
        <el-icon><ChatLineSquare /></el-icon>
        <span>需求申报</span>
      </el-menu-item>

      <!-- 线下培训签到：凭签到码完成电子签到 -->
      <el-menu-item index="/offline">
        <el-icon><Checked /></el-icon>
        <span>线下培训签到</span>
      </el-menu-item>

      <!-- 消息中心：站内消息列表 -->
      <el-menu-item index="/messages">
        <el-icon><Bell /></el-icon>
        <span>消息中心</span>
      </el-menu-item>

      <!-- 帮助中心：制度要点与常见问题 -->
      <el-menu-item index="/help">
        <el-icon><QuestionFilled /></el-icon>
        <span>帮助中心</span>
      </el-menu-item>

      <!-- 个人中心分组 -->
      <el-sub-menu index="profile-group">
        <template #title>
          <el-icon><User /></el-icon>
          <span>个人中心</span>
        </template>
        <el-menu-item index="/profile">
          <el-icon><Postcard /></el-icon>
          <span>个人资料</span>
        </el-menu-item>
        <el-menu-item index="/profile/record">
          <el-icon><Tickets /></el-icon>
          <span>学习记录</span>
        </el-menu-item>
        <el-menu-item index="/profile/certificate">
          <el-icon><Medal /></el-icon>
          <span>培训档案</span>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>

    <!-- 底部学习提示卡 -->
    <div class="menu-tip">
      <el-icon :size="16"><InfoFilled /></el-icon>
      <p>请从「我的任务」进入视频学习与在线考试，系统会自动记录学习进度与成绩。</p>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

/**
 * 当前高亮菜单项
 * 详情类页面（任务详情/学习/考试）通过 meta.activeMenu 归一化到「我的任务」
 */
const activeMenu = computed(() => route.meta?.activeMenu || route.path)

/**
 * 菜单选中处理：仅做路由跳转
 * @param {string} index 菜单项标识（即目标路由路径）
 */
function handleSelect(index) {
  if (index !== route.path) {
    router.push(index)
  }
}
</script>

<style scoped>
.side-menu {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 216px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
}

.menu {
  border-right: none;
  padding: 12px 10px;
}

/* 菜单项统一圆角与间距，选中态使用主色浅底 + 左侧主色竖条 */
.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  margin-bottom: 4px;
  border-radius: var(--radius-md);
  color: var(--text-regular);
  font-size: 14px;
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  background: var(--brand-primary-light);
  color: var(--brand-primary);
}

.menu :deep(.el-menu-item.is-active) {
  position: relative;
  background: var(--brand-primary-light);
  color: var(--brand-primary);
  font-weight: 600;
}

/* 选中项左侧主色竖条 */
.menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 2px;
  background: var(--brand-primary);
}

.menu :deep(.el-sub-menu .el-menu-item) {
  height: 40px;
  line-height: 40px;
  font-size: 13px;
}

.menu-tip {
  margin: 12px;
  padding: 12px;
  border-radius: var(--radius-md);
  background: var(--brand-primary-light);
  color: var(--text-regular);
  font-size: 12px;
  line-height: 1.6;
}

.menu-tip p {
  margin: 6px 0 0;
}

.menu-tip .el-icon {
  color: var(--brand-primary);
}
</style>
