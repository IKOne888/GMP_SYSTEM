<template>
  <div class="offline-page">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2 class="gmp-page-title">线下培训签到</h2>
        <p class="gmp-page-subtitle">在签到窗口内输入培训管理员提供的签到码完成电子签到，签到记录将纳入个人培训档案</p>
      </div>
      <el-button :loading="loading" @click="refreshAll">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-tabs v-model="tab" class="offline-tabs">
      <!-- 可签到场次 -->
      <el-tab-pane label="可签到场次" name="signable">
        <div v-loading="loadingSignable" class="table-card gmp-card">
          <el-table :data="signableList" border stripe empty-text="暂无可签到的线下培训场次">
            <el-table-column prop="title" label="培训主题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="trainer" label="培训师" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.trainer || '待定' }}</template>
            </el-table-column>
            <el-table-column prop="location" label="培训地点" width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.location || '待定' }}</template>
            </el-table-column>
            <el-table-column label="培训时间" width="230">
              <template #default="{ row }">
                {{ formatDateTime(row.startTime) }} ~ {{ formatDateTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="签到窗口" width="230">
              <template #default="{ row }">
                {{ formatDateTime(row.signStartTime) }} ~ {{ formatDateTime(row.signEndTime) }}
              </template>
            </el-table-column>
            <el-table-column label="阶段" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="phaseTagType(row.phase)" size="small" effect="light">{{ row.phaseText }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="签到状态" width="150" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.signed" type="success" size="small" effect="light">
                  已签到 {{ formatDateTime(row.signTime) }}
                </el-tag>
                <span v-else class="text-muted">未签到</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.phase === 'signing' && !row.signed"
                  type="primary"
                  link
                  @click="openSign(row)"
                >去签到</el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 我的签到记录 -->
      <el-tab-pane label="我的签到记录" name="records">
        <div v-loading="loadingRecords" class="table-card gmp-card">
          <el-table :data="recordList" border stripe empty-text="暂无签到记录">
            <el-table-column prop="trainingTitle" label="培训主题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="trainer" label="培训师" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.trainer || '待定' }}</template>
            </el-table-column>
            <el-table-column prop="location" label="培训地点" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.location || '待定' }}</template>
            </el-table-column>
            <el-table-column label="培训时间" width="230">
              <template #default="{ row }">
                {{ formatDateTime(row.startTime) }} ~ {{ formatDateTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="签到时间" width="150">
              <template #default="{ row }">{{ formatDateTime(row.signTime) }}</template>
            </el-table-column>
            <el-table-column label="签到方式" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.signType === 2 ? 'warning' : 'success'" size="small" effect="light">
                  {{ row.signTypeText || (row.signType === 2 ? '管理员补签' : '自主签到') }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 签到弹窗 -->
    <el-dialog v-model="signVisible" title="电子签到" width="460px" destroy-on-close>
      <div class="sign-target">
        <div class="sign-target-title">{{ current.title }}</div>
        <div class="sign-target-sub">
          签到窗口：{{ formatDateTime(current.signStartTime) }} ~ {{ formatDateTime(current.signEndTime) }}
        </div>
      </div>
      <el-form :model="signForm" label-width="80px">
        <el-form-item label="签到码">
          <el-input
            v-model="signForm.signCode"
            placeholder="请输入6位签到码"
            maxlength="6"
            clearable
            @keyup.enter="handleSign"
          />
        </el-form-item>
      </el-form>
      <div class="sign-tip">签到码由培训管理员在签到现场提供；签到时间窗口与重复签到由系统校验</div>
      <template #footer>
        <el-button @click="signVisible = false">取消</el-button>
        <el-button type="primary" :loading="signing" @click="handleSign">确认签到</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 学员端「线下培训电子签到」页面
 * <p>
 * 调用 /study/offline 系列接口：可签到场次、自主签到、我的签到记录；
 * 学员身份由后端从登录态解析，前端不传 userId。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSignableTrainings, submitSign, getMySignRecords } from '@/api/offline'
import { formatDateTime } from '@/utils/format'

/** 当前页签 */
const tab = ref('signable')
/** 可签到场次列表 */
const signableList = ref([])
/** 我的签到记录 */
const recordList = ref([])
/** 加载状态 */
const loading = ref(false)
const loadingSignable = ref(false)
const loadingRecords = ref(false)

/** 签到弹窗与表单 */
const signVisible = ref(false)
const signing = ref(false)
const current = ref({})
const signForm = reactive({ signCode: '' })

/**
 * 场次阶段 → ElTag 配色
 * @param {string} phase upcoming/signing/ended/cancelled
 */
function phaseTagType(phase) {
  return { upcoming: 'info', signing: 'success', ended: 'warning', cancelled: 'danger' }[phase] || 'info'
}

/** 加载可签到场次 */
async function loadSignable() {
  loadingSignable.value = true
  try {
    const res = await getSignableTrainings()
    signableList.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    loadingSignable.value = false
  }
}

/** 加载我的签到记录 */
async function loadRecords() {
  loadingRecords.value = true
  try {
    const res = await getMySignRecords()
    recordList.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    loadingRecords.value = false
  }
}

/** 刷新两个页签数据（签到后阶段与状态可能变化） */
async function refreshAll() {
  loading.value = true
  try {
    await Promise.all([loadSignable(), loadRecords()])
  } finally {
    loading.value = false
  }
}

/** 打开签到弹窗 */
function openSign(row) {
  if (row.signed) {
    ElMessage.info('您已签到，请勿重复签到')
    return
  }
  if (row.phase !== 'signing') {
    ElMessage.warning('当前不在签到时间窗口内')
    return
  }
  current.value = row
  signForm.signCode = ''
  signVisible.value = true
}

/** 提交签到 */
async function handleSign() {
  const code = String(signForm.signCode || '').trim()
  if (!/^\d{6}$/.test(code)) {
    ElMessage.warning('签到码为6位数字')
    return
  }
  signing.value = true
  try {
    await submitSign({ trainingId: current.value.id, signCode: code })
    ElMessage.success('签到成功')
    signVisible.value = false
    await refreshAll()
  } catch (e) {
    // 失败原因（窗口未开始/已结束、签到码错误、重复签到）由 request 拦截器统一提示
    console.error(e)
  } finally {
    signing.value = false
  }
}

onMounted(() => {
  loadSignable()
  loadRecords()
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.offline-tabs {
  margin-top: 4px;
}

.table-card {
  padding: 8px 12px;
  min-height: 320px;
}

.text-muted {
  color: var(--text-secondary);
  font-size: 13px;
}

.sign-target {
  padding: 12px 14px;
  margin-bottom: 14px;
  background: #f7faff;
  border-radius: 8px;
}

.sign-target-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.sign-target-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.sign-tip {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
}
</style>
