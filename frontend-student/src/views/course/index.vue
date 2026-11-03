<template>
  <div class="course-page">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2 class="gmp-page-title">课程中心</h2>
        <p class="gmp-page-subtitle">浏览与您部门/岗位匹配的培训课程，可自主报名学习</p>
      </div>
      <el-button :icon="Refresh" @click="loadCourses">刷新</el-button>
    </div>

    <!-- 筛选栏：关键词 + 分类 + 报名状态 -->
    <div class="filter-bar gmp-card">
      <el-input
        v-model="query.keyword"
        placeholder="搜索课程名称"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>

      <el-select
        v-model="query.categoryId"
        placeholder="全部分类"
        clearable
        class="filter-select"
        @change="handleSearch"
      >
        <el-option
          v-for="item in categories"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </el-select>

      <el-radio-group v-model="query.enrolled" @change="handleSearch">
        <el-radio-button :value="''">全部</el-radio-button>
        <el-radio-button :value="true">已报名</el-radio-button>
        <el-radio-button :value="false">未报名</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 课程卡片网格 -->
    <div v-loading="loading" class="course-list">
      <el-empty v-if="!loading && courses.length === 0" description="暂无符合条件的课程" />

      <div class="course-grid">
        <div
          v-for="course in courses"
          :key="course.id"
          class="course-card gmp-card gmp-card--hoverable"
        >
          <!-- 视频封面区：有视频就用 video 抓帧，没有就显示占位 -->
          <div class="course-cover" @click="goDetail(course)">
            <!-- 隐藏 video 标签用于自动加载首帧（metadata 加载完自动 seek 1 秒） -->
            <video
              v-if="course.firstVideoId"
              :ref="el => videoRefs[course.id] = el"
              :src="getVideoStreamUrl(course.firstVideoId)"
              preload="metadata"
              muted
              playsinline
              @loadeddata="e => onVideoLoaded(e, course.id)"
              @seeked="e => onVideoSeeked(e, course.id)"
              @loadedmetadata="e => onVideoMetadata(e, course.id)"
            />
            <!-- 封面图片（video 抓帧后替换；无视频时显示占位） -->
            <div
              v-if="course.firstVideoId && coverImages[course.id]"
              class="cover-img"
              :style="{ backgroundImage: `url(${coverImages[course.id]})` }"
            ></div>
            <!-- 占位封面：无视频或视频尚未加载完成 -->
            <div
              v-else
              class="cover-placeholder"
            >
              <el-icon :size="48"><VideoPlay /></el-icon>
              <span v-if="!course.firstVideoId">📚 文档课程</span>
              <span v-else>视频封面加载中...</span>
            </div>
            <!-- 视频数量角标 -->
            <span
              v-if="course.videoCount && course.videoCount > 0"
              class="video-badge"
            >{{ course.videoCount }} 个视频</span>
          </div>

          <!-- 卡片头部：课程名 + 报名状态标签 -->
          <div class="course-head">
            <span class="course-name" @click="goDetail(course)">{{ course.name }}</span>
            <el-tag :type="course.enrolled ? 'success' : 'info'" effect="light" size="small">
              {{ course.enrolled ? '已报名' : '未报名' }}
            </el-tag>
          </div>

          <!-- 分类与编码 -->
          <div class="course-tags">
            <el-tag v-if="course.categoryName" type="primary" effect="plain" size="small">
              {{ course.categoryName }}
            </el-tag>
            <span v-if="course.code" class="course-code">课程编码：{{ course.code }}</span>
          </div>

          <!-- 培训目标摘要 -->
          <p class="course-objective" :title="course.objective">
            {{ course.objective || '暂无培训目标说明' }}
          </p>

          <!-- 关键参数 -->
          <div class="course-meta">
            <span><el-icon><Timer /></el-icon> 学时 {{ course.trainingHours ?? '-' }}</span>
            <span><el-icon><Aim /></el-icon> 及格 {{ course.passScore ?? '-' }} 分</span>
            <span>
              <el-icon><RefreshRight /></el-icon>
              复训 {{ course.retrainPeriod ? course.retrainPeriod + ' 天' : '不限' }}
            </span>
          </div>

          <!-- SOP 信息 -->
          <div v-if="course.sopCode" class="course-sop">
            SOP：{{ course.sopCode }}<span v-if="course.sopVersion"> / {{ course.sopVersion }}</span>
          </div>

          <!-- 操作区 -->
          <div class="course-footer">
            <el-button link type="primary" @click="goDetail(course)">课程详情</el-button>
            <el-button
              v-if="course.enrolled"
              type="primary"
              @click="goTask(course)"
            >
              继续学习
            </el-button>
            <el-button
              v-else
              type="primary"
              :loading="enrollingId === course.id"
              @click="handleEnroll(course)"
            >
              立即报名
            </el-button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[8, 12, 20]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadCourses"
          @size-change="handleSearch"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 课程中心页面
 * <p>
 * 数据来源：GET /study/courses（后端已实现按部门/岗位的推荐逻辑）。
 * 报名动作调用 POST /study/courses/{id}/enroll，成功后可直接进入任务详情开始学习。
 */
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search, VideoPlay } from '@element-plus/icons-vue'
import { getCourses, enrollCourse, getCourseCategories } from '@/api/course'
import request from '@/utils/request'

const router = useRouter()

/** 加载状态 */
const loading = ref(false)
/** 正在报名的课程ID（用于按钮局部 loading，避免重复提交） */
const enrollingId = ref(null)
/** 课程列表 */
const courses = ref([])
/** 总条数 */
const total = ref(0)
/** 课程分类下拉选项 */
const categories = ref([])
/** 视频 DOM 引用（courseId -> video element） */
const videoRefs = reactive({})
/** 封面图 dataURL 映射（courseId -> dataURL） */
const coverImages = reactive({})

/**
 * 拼接视频流完整 URL（后端白名单免登录，baseURL + /study/videos/{id}/stream）
 * @param {number} videoId 视频ID
 * @returns {string} 完整可访问 URL
 */
function getVideoStreamUrl(videoId) {
  return `${request.defaults.baseURL}/study/videos/${videoId}/stream`
}

/**
 * video metadata 加载完毕：尝试 seek 到 1 秒位置用于抓帧
 * （preload="metadata" 时 duration 已确定，seek 不会触发完整下载）
 * @param {Event} _e 事件对象（未使用）
 * @param {number} courseId 课程ID
 */
function onVideoMetadata(_e, courseId) {
  const video = videoRefs[courseId]
  if (!video) return
  // 跳到 1 秒位置（或视频总时长的 10%，避免 0 秒黑屏）
  const seekTarget = Math.min(1, Math.max(0.1, video.duration * 0.1))
  try {
    video.currentTime = seekTarget
  } catch (err) {
    // 部分浏览器不允许直接设 currentTime，忽略即可
  }
}

/**
 * seek 完成事件：canvas 截帧生成封面 dataURL
 * @param {Event} _e 事件对象（未使用）
 * @param {number} courseId 课程ID
 */
function onVideoSeeked(_e, courseId) {
  const video = videoRefs[courseId]
  if (!video) return
  try {
    const canvas = document.createElement('canvas')
    // 封面尺寸：240 × 135（16:9 缩略图）
    const W = 240
    const H = 135
    canvas.width = W
    canvas.height = H
    const ctx = canvas.getContext('2d')
    // 先把 canvas 背景设成视频当前帧（drawImage 按视频宽高比例填充到 240×135）
    const videoW = video.videoWidth || W
    const videoH = video.videoHeight || H
    const scale = Math.max(W / videoW, H / videoH)
    const dw = videoW * scale
    const dh = videoH * scale
    const dx = (W - dw) / 2
    const dy = (H - dh) / 2
    ctx.drawImage(video, dx, dy, dw, dh)
    coverImages[courseId] = canvas.toDataURL('image/jpeg', 0.7)
    // 抓帧成功后把 video 标签隐藏掉，减少性能消耗
    video.style.display = 'none'
  } catch (err) {
    // CORS 跨域视频可能无法 drawImage，忽略（占位图继续显示）
    console.warn('视频封面抓帧失败:', courseId, err)
  }
}

/** 旧版兼容：部分浏览器触发 loadeddata 而非 loadedmetadata */
function onVideoLoaded(_e, courseId) {
  const video = videoRefs[courseId]
  if (!video || video.seeking) return
  if (video.duration > 0) {
    onVideoMetadata(_e, courseId)
  }
}

/** 查询条件（enrolled 为空串表示全部） */
const query = reactive({
  page: 1,
  size: 8,
  keyword: '',
  categoryId: null,
  enrolled: ''
})

/**
 * 加载课程列表
 */
async function loadCourses() {
  loading.value = true
  try {
    const res = await getCourses({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined,
      // 仅在三态明确选择时传布尔值，避免把空串误传为 false
      enrolled: query.enrolled === '' ? undefined : query.enrolled
    })
    courses.value = res.data?.records || []
    total.value = Number(res.data?.total) || 0
  } finally {
    loading.value = false
  }
}

/**
 * 加载分类下拉选项（只读字典，失败不阻断主流程）
 */
async function loadCategories() {
  try {
    const res = await getCourseCategories()
    categories.value = res.data || []
  } catch (e) {
    categories.value = []
  }
}

/**
 * 条件变更后重新查询（回到第一页）
 */
function handleSearch() {
  query.page = 1
  loadCourses()
}

/**
 * 报名课程
 * @param {object} course 课程对象
 */
async function handleEnroll(course) {
  enrollingId.value = course.id
  try {
    const res = await enrollCourse(course.id)
    const taskId = res.data?.taskId
    ElMessage.success('报名成功，已加入「我的任务」')
    await loadCourses()
    if (taskId) {
      router.push(`/task/${taskId}`)
    }
  } finally {
    enrollingId.value = null
  }
}

/**
 * 跳转课程详情
 * @param {object} course 课程对象
 */
function goDetail(course) {
  router.push(`/courses/${course.id}`)
}

/**
 * 已报名课程跳转任务详情
 * @param {object} course 课程对象
 */
function goTask(course) {
  if (course.taskId) {
    router.push(`/task/${course.taskId}`)
  } else {
    ElMessage.info('暂未查询到关联任务，请前往「我的任务」查看')
    router.push('/dashboard')
  }
}

onMounted(() => {
  loadCategories()
  loadCourses()
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

/* ---------- 筛选栏 ---------- */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search-input {
  width: 280px;
}

.filter-select {
  width: 180px;
}

/* ---------- 课程网格 ---------- */
.course-list {
  min-height: 240px;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.course-card {
  display: flex;
  flex-direction: column;
  padding: 0;
  min-height: 360px;
  overflow: hidden;
}

/* ---------- 视频封面区 ---------- */
.course-cover {
  position: relative;
  width: 100%;
  height: 160px;
  background: linear-gradient(135deg, #f0f4ff 0%, #e8eef8 100%);
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;
}

/* video 标签：绝对定位铺满，抓帧后隐藏 */
.course-cover video {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.01; /* 极淡，仅用于加载 metadata 和 seek */
  pointer-events: none;
}

/* 封面图片（canvas 抓帧生成 dataURL 后作为背景） */
.cover-img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  transition: transform 0.25s ease;
}

/* hover 封面时微微放大 */
.course-cover:hover .cover-img {
  transform: scale(1.05);
}

/* 占位封面（无视频或加载中） */
.cover-placeholder {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf3 100%);
}

.cover-placeholder .el-icon {
  color: var(--brand-primary);
  opacity: 0.5;
}

/* 视频数量角标 */
.video-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  backdrop-filter: blur(4px);
}

/* ---------- 卡片内容区 ---------- */
.course-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px 0;
}

.course-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
  transition: color 0.2s;
  line-height: 1.4;
}

.course-name:hover {
  color: var(--brand-primary);
}

.course-tags {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  flex-wrap: wrap;
  padding: 0 16px;
}

.course-code {
  font-size: 12px;
  color: var(--text-secondary);
}

.course-objective {
  margin: 12px 16px;
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 44px;
}

.course-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--text-secondary);
  flex-wrap: wrap;
  padding: 0 16px;
}

.course-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.course-sop {
  margin: 8px 16px 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.course-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding: 14px 16px;
  border-top: 1px dashed var(--border-color);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1320px) {
  .course-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 900px) {
  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>
