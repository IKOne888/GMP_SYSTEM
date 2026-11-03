<template>
  <div class="page-container">
    <!-- 搜索栏：按培训主题、场次阶段、培训日期范围筛选 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="培训主题">
          <!-- 支持按主题关键字模糊查询 -->
          <el-input v-model="queryParams.title" placeholder="请输入培训主题" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="场次阶段">
          <!-- 阶段由后端按「当前时间 + 签到窗口」动态计算，非数据库固定状态 -->
          <el-select v-model="queryParams.phase" placeholder="请选择阶段" clearable style="width: 140px">
            <el-option label="未开始" value="upcoming" />
            <el-option label="签到中" value="signing" />
            <el-option label="已结束" value="ended" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item label="培训日期">
          <!-- daterange 选择结果拆分为 startDate / endDate 传给后端 -->
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增线下培训
        </el-button>
        <span class="toolbar-tip">线下培训场次登记后，学员可在学员端 / 小程序凭签到码完成电子签到</span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="title" label="培训主题" min-width="180" show-overflow-tooltip />
        <el-table-column label="关联内容" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 计划与课程均为可选关联，未关联时显示占位符 -->
            <div v-if="row.planName">计划：{{ row.planName }}</div>
            <div v-if="row.courseName">课程：{{ row.courseName }}</div>
            <span v-if="!row.planName && !row.courseName" class="placeholder">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="trainer" label="培训师" width="100" show-overflow-tooltip />
        <el-table-column prop="location" label="地点" width="140" show-overflow-tooltip />
        <el-table-column label="培训时间" width="190">
          <template #default="{ row }">
            <div>{{ formatDateTime(row.startTime) }}</div>
            <div class="sub-text">至 {{ formatDateTime(row.endTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="签到窗口" width="190">
          <template #default="{ row }">
            <div class="sub-text">{{ formatDateTime(row.signStartTime) }}</div>
            <div class="sub-text">至 {{ formatDateTime(row.signEndTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="签到码" width="110" align="center">
          <template #default="{ row }">
            <!-- 签到码由系统生成，管理员现场公布给学员 -->
            <el-tag type="warning" effect="dark">{{ row.signCode || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="signCount" label="签到人数" width="90" align="center" />
        <el-table-column label="阶段" width="90" align="center">
          <template #default="{ row }">
            <!-- 阶段标签颜色：未开始灰、签到中绿、已结束蓝、已取消红 -->
            <el-tag :type="phaseTagType(row.phase)" size="small">{{ row.phaseText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAttendance(row)">签到明细</el-button>
            <el-button link type="primary" :disabled="row.phase === 'ended' || row.phase === 'cancelled'"
                       @click="handleEdit(row)">编辑</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleMoreCommand(cmd, row)">
              <el-button link type="primary">更多<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="refreshCode">重新生成签到码</el-dropdown-item>
                  <el-dropdown-item command="finish" :disabled="row.phase === 'ended' || row.phase === 'cancelled'">
                    结束场次
                  </el-dropdown-item>
                  <el-dropdown-item command="cancel" :disabled="row.phase === 'ended' || row.phase === 'cancelled'">
                    取消场次
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除场次</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 新增 / 编辑场次对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="培训主题" prop="title">
          <el-input v-model="formData.title" placeholder="如：洁净区操作规程专项培训" maxlength="200" show-word-limit />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="关联计划" prop="planId">
              <!-- 关联计划为可选，便于线下培训归集到年度培训计划 -->
              <el-select v-model="formData.planId" placeholder="可不选" clearable filterable style="width: 100%">
                <el-option v-for="item in planOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联课程" prop="courseId">
              <el-select v-model="formData.courseId" placeholder="可不选" clearable filterable style="width: 100%">
                <el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="培训师" prop="trainer">
              <el-input v-model="formData.trainer" placeholder="请输入培训师姓名" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训地点" prop="location">
              <el-input v-model="formData.location" placeholder="如：一车间培训室" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker
                v-model="formData.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择开始时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker
                v-model="formData.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择结束时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="签到开始" prop="signStartTime">
              <el-date-picker
                v-model="formData.signStartTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空默认提前30分钟"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签到结束" prop="signEndTime">
              <el-date-picker
                v-model="formData.signEndTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空默认培训结束时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="500" show-word-limit
                    placeholder="可填写培训要求、注意事项等" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="签到窗口默认从培训开始前 30 分钟开放，至培训结束时间关闭；保存后系统自动生成 6 位签到码"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 签到明细对话框 -->
    <el-dialog v-model="attendanceVisible" :title="`签到明细 - ${currentTraining?.title || ''}`" width="900px" destroy-on-close>
      <div class="attendance-toolbar">
        <el-input
          v-model="attendanceQuery.keyword"
          placeholder="搜索姓名 / 工号"
          clearable
          style="width: 200px"
          @keyup.enter="fetchAttendance"
          @clear="fetchAttendance"
        />
        <el-button type="primary" @click="fetchAttendance">查询</el-button>
        <el-button type="success" @click="handleExport">导出 Excel</el-button>
        <el-button @click="openManualSign">补签</el-button>
        <span class="toolbar-tip">
          签到码：<el-tag type="warning">{{ currentTraining?.signCode || '—' }}</el-tag>
        </span>
      </div>

      <el-table v-loading="attendanceLoading" :data="attendanceData" border stripe style="margin-top: 12px">
        <el-table-column prop="userName" label="姓名" width="110" />
        <el-table-column prop="employeeNo" label="工号" width="130" />
        <el-table-column prop="deptName" label="所属部门" min-width="140" show-overflow-tooltip />
        <el-table-column label="签到时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.signTime) }}</template>
        </el-table-column>
        <el-table-column label="签到方式" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.signType === 2 ? 'warning' : 'success'" size="small">{{ row.signTypeText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="signIp" label="来源IP" width="140" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>

      <el-pagination
        v-model:current-page="attendanceQuery.page"
        v-model:page-size="attendanceQuery.size"
        :page-sizes="[10, 20, 50]"
        :total="attendanceTotal"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="fetchAttendance"
        @current-change="fetchAttendance"
      />
    </el-dialog>

    <!-- 管理员补签对话框 -->
    <el-dialog v-model="manualVisible" title="管理员补签" width="480px" destroy-on-close>
      <el-form ref="manualFormRef" :model="manualForm" :rules="manualRules" label-width="90px">
        <el-form-item label="学员" prop="userId">
          <!-- 补签对象从启用用户列表中选择 -->
          <el-select v-model="manualForm.userId" placeholder="请选择学员" filterable style="width: 100%">
            <el-option
              v-for="item in userOptions"
              :key="item.id"
              :label="`${item.realName || item.username}（${item.username}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="补签原因" prop="remark">
          <el-input v-model="manualForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="如：学员手机故障未能自主签到" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualLoading" @click="submitManualSign">确定补签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 通用请求方法 + 默认实例（导出需要 responseType: 'blob'，故直接使用 service）
import service, { get, post, put, del } from '@/utils/request'
// 时间格式化工具：统一将后端时间渲染为 yyyy-MM-dd HH:mm:ss
import { formatDateTime } from '@/utils/format'

/** 列表查询参数 */
const queryParams = reactive({
  title: '',
  phase: '',
  dateRange: [],
  page: 1,
  size: 10
})
/** 列表加载状态 */
const loading = ref(false)
/** 列表数据 */
const tableData = ref([])
/** 总条数 */
const total = ref(0)

/** 计划下拉选项 */
const planOptions = ref([])
/** 课程下拉选项 */
const courseOptions = ref([])
/** 用户下拉选项（补签用） */
const userOptions = ref([])

/** 弹窗可见性 */
const dialogVisible = ref(false)
/** 提交按钮加载状态 */
const submitLoading = ref(false)
/** 表单实例 */
const formRef = ref(null)
/** 当前编辑的场次ID（为空表示新增） */
const editingId = ref(null)

/** 表单数据 */
const formData = reactive({
  title: '',
  planId: null,
  courseId: null,
  trainer: '',
  location: '',
  startTime: '',
  endTime: '',
  signStartTime: '',
  signEndTime: '',
  remark: ''
})

/** 表单校验规则 */
const formRules = {
  title: [{ required: true, message: '请输入培训主题', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择培训开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择培训结束时间', trigger: 'change' },
    {
      // 前端先行校验，避免提交后才收到后端错误提示
      validator: (rule, value, callback) => {
        if (value && formData.startTime && value <= formData.startTime) {
          callback(new Error('结束时间必须晚于开始时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

/** 弹窗标题：新增 / 编辑 */
const dialogTitle = computed(() => (editingId.value ? '编辑线下培训' : '新增线下培训'))

/** 签到明细弹窗 */
const attendanceVisible = ref(false)
/** 签到明细加载状态 */
const attendanceLoading = ref(false)
/** 当前操作的场次 */
const currentTraining = ref(null)
/** 签到明细数据 */
const attendanceData = ref([])
/** 签到明细总数 */
const attendanceTotal = ref(0)
/** 签到明细查询参数 */
const attendanceQuery = reactive({ page: 1, size: 10, keyword: '' })

/** 补签弹窗 */
const manualVisible = ref(false)
/** 补签提交状态 */
const manualLoading = ref(false)
/** 补签表单实例 */
const manualFormRef = ref(null)
/** 补签表单数据 */
const manualForm = reactive({ userId: null, remark: '' })
/** 补签校验规则 */
const manualRules = {
  userId: [{ required: true, message: '请选择补签学员', trigger: 'change' }]
}

/**
 * 阶段 → 标签颜色映射
 * @param {string} phase 阶段编码
 * @returns {string} Element Plus 标签类型
 */
function phaseTagType(phase) {
  return { upcoming: 'info', signing: 'success', ended: 'primary', cancelled: 'danger' }[phase] || 'info'
}

/**
 * 拉取线下培训场次列表
 */
async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: queryParams.page,
      size: queryParams.size
    }
    if (queryParams.title) params.title = queryParams.title
    if (queryParams.phase) params.phase = queryParams.phase
    // 日期范围拆分为起止日期
    if (queryParams.dateRange && queryParams.dateRange.length === 2) {
      params.startDate = queryParams.dateRange[0]
      params.endDate = queryParams.dateRange[1]
    }
    const res = await get('/training/offline', params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('查询线下培训失败', e)
  } finally {
    loading.value = false
  }
}

/**
 * 搜索：重置到第一页
 */
function handleSearch() {
  queryParams.page = 1
  fetchData()
}

/**
 * 重置查询条件
 */
function handleReset() {
  queryParams.title = ''
  queryParams.phase = ''
  queryParams.dateRange = []
  queryParams.page = 1
  fetchData()
}

/**
 * 加载计划、课程、用户下拉选项
 */
async function loadOptions() {
  try {
    const [planRes, courseRes, userRes] = await Promise.all([
      get('/training/plans/list'),
      get('/training/courses/list'),
      get('/system/users/list')
    ])
    planOptions.value = planRes.data || []
    courseOptions.value = courseRes.data || []
    userOptions.value = userRes.data || []
  } catch (e) {
    console.error('加载下拉选项失败', e)
  }
}

/**
 * 打开新增弹窗：清空表单
 */
function handleAdd() {
  editingId.value = null
  Object.assign(formData, {
    title: '',
    planId: null,
    courseId: null,
    trainer: '',
    location: '',
    startTime: '',
    endTime: '',
    signStartTime: '',
    signEndTime: '',
    remark: ''
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗：回填场次数据
 * @param {object} row 行数据
 */
function handleEdit(row) {
  editingId.value = row.id
  Object.assign(formData, {
    title: row.title,
    planId: row.planId,
    courseId: row.courseId,
    trainer: row.trainer,
    location: row.location,
    startTime: row.startTime,
    endTime: row.endTime,
    // 后端返回的是「实际生效」的签到窗口，回填后即固化，避免二次默认计算产生偏差
    signStartTime: row.signStartTime,
    signEndTime: row.signEndTime,
    remark: row.remark
  })
  dialogVisible.value = true
}

/**
 * 提交新增 / 编辑
 */
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      // 空字符串转为 null，避免后端 LocalDateTime 解析空串失败
      const payload = {
        ...formData,
        signStartTime: formData.signStartTime || null,
        signEndTime: formData.signEndTime || null
      }
      if (editingId.value) {
        await put(`/training/offline/${editingId.value}`, payload)
        ElMessage.success('编辑成功')
      } else {
        await post('/training/offline', payload)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e) {
      console.error('保存线下培训失败', e)
    } finally {
      submitLoading.value = false
    }
  })
}

/**
 * 「更多」下拉命令分发
 * @param {string} command 命令
 * @param {object} row 行数据
 */
function handleMoreCommand(command, row) {
  switch (command) {
    case 'refreshCode':
      handleRefreshCode(row)
      break
    case 'finish':
      handleFinish(row)
      break
    case 'cancel':
      handleCancel(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

/**
 * 重新生成签到码
 * @param {object} row 行数据
 */
async function handleRefreshCode(row) {
  try {
    await ElMessageBox.confirm('重新生成后原签到码立即失效，确定继续？', '提示', { type: 'warning' })
    const res = await put(`/training/offline/${row.id}/refresh-code`)
    ElMessage.success(`新签到码：${res.data}`)
    fetchData()
  } catch (e) {
    // 用户取消或接口异常：取消时不提示
    if (e !== 'cancel') console.error(e)
  }
}

/**
 * 结束场次
 * @param {object} row 行数据
 */
async function handleFinish(row) {
  try {
    await ElMessageBox.confirm('结束后学员将无法继续签到，确定结束该场次？', '提示', { type: 'warning' })
    await put(`/training/offline/${row.id}/finish`)
    ElMessage.success('场次已结束')
    fetchData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

/**
 * 取消场次
 * @param {object} row 行数据
 */
async function handleCancel(row) {
  try {
    await ElMessageBox.confirm('取消后学员将无法签到，确定取消该场次？', '提示', { type: 'warning' })
    await put(`/training/offline/${row.id}/cancel`)
    ElMessage.success('场次已取消')
    fetchData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

/**
 * 删除场次（已产生签到记录时后端会拒绝）
 * @param {object} row 行数据
 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除该线下培训场次？', '提示', { type: 'warning' })
    await del(`/training/offline/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {
    // 后端拒绝删除（已产生签到记录）时，错误提示已由请求拦截器统一展示
    if (e !== 'cancel') console.error(e)
  }
}

/**
 * 打开签到明细弹窗
 * @param {object} row 行数据
 */
function openAttendance(row) {
  currentTraining.value = row
  attendanceQuery.page = 1
  attendanceQuery.size = 10
  attendanceQuery.keyword = ''
  attendanceData.value = []
  attendanceTotal.value = 0
  attendanceVisible.value = true
  fetchAttendance()
}

/**
 * 查询签到明细
 */
async function fetchAttendance() {
  if (!currentTraining.value) return
  attendanceLoading.value = true
  try {
    const params = {
      page: attendanceQuery.page,
      size: attendanceQuery.size
    }
    if (attendanceQuery.keyword) params.keyword = attendanceQuery.keyword
    const res = await get(`/training/offline/${currentTraining.value.id}/attendance`, params)
    attendanceData.value = res.data?.records || []
    attendanceTotal.value = res.data?.total || 0
  } catch (e) {
    console.error('查询签到明细失败', e)
  } finally {
    attendanceLoading.value = false
  }
}

/**
 * 导出签到明细（二进制流下载）
 */
async function handleExport() {
  if (!currentTraining.value) return
  try {
    const blob = await service.get(`/training/offline/${currentTraining.value.id}/attendance/export`, {
      responseType: 'blob'
    })
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.download = `线下培训签到明细-${currentTraining.value.title}.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    console.error('导出签到明细失败', e)
  }
}

/**
 * 打开补签弹窗
 */
function openManualSign() {
  manualForm.userId = null
  manualForm.remark = ''
  manualVisible.value = true
}

/**
 * 提交补签
 */
async function submitManualSign() {
  if (!manualFormRef.value) return
  await manualFormRef.value.validate(async (valid) => {
    if (!valid) return
    manualLoading.value = true
    try {
      await post(`/training/offline/${currentTraining.value.id}/attendance/manual`, {
        userId: manualForm.userId,
        remark: manualForm.remark || null
      })
      ElMessage.success('补签成功')
      manualVisible.value = false
      fetchAttendance()
      // 同步刷新列表中的签到人数
      fetchData()
    } catch (e) {
      console.error('补签失败', e)
    } finally {
      manualLoading.value = false
    }
  })
}

onMounted(() => {
  loadOptions()
  fetchData()
})
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card :deep(.el-card__body) {
  padding-bottom: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-tip {
  font-size: 12px;
  color: #909399;
}

.attendance-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.sub-text {
  font-size: 12px;
  color: #909399;
}

.placeholder {
  color: #c0c4cc;
}
</style>
