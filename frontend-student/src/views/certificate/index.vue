<template>
  <div class="cert-page">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h2 class="gmp-page-title">我的资质证书</h2>
        <p class="gmp-page-subtitle">
          登记外部机构培训与特殊岗位上岗资质，提交后由培训管理员或 QA 审核；临期 30 天内自动提醒
        </p>
      </div>
      <el-button type="primary" @click="openForm()">
        <el-icon><Plus /></el-icon> 登记证书
      </el-button>
    </div>

    <!-- 统计条：已生效 / 待审核 / 已驳回 / 临期或过期 -->
    <div class="stat-row">
      <div class="stat-card gmp-card">
        <div class="stat-num stat-num--valid">{{ stats.approved }}</div>
        <div class="stat-label">已生效</div>
      </div>
      <div class="stat-card gmp-card">
        <div class="stat-num stat-num--pending">{{ stats.pending }}</div>
        <div class="stat-label">待审核</div>
      </div>
      <div class="stat-card gmp-card">
        <div class="stat-num stat-num--rejected">{{ stats.rejected }}</div>
        <div class="stat-label">已驳回</div>
      </div>
      <div class="stat-card gmp-card">
        <div class="stat-num stat-num--warn">{{ stats.expiring }}</div>
        <div class="stat-label">临期/已过期</div>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar gmp-card">
      <el-radio-group v-model="filterType" @change="loadList">
        <el-radio-button :label="''">全部</el-radio-button>
        <el-radio-button :label="1">外部培训</el-radio-button>
        <el-radio-button :label="2">资质证书</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 证书列表 -->
    <div v-loading="loading" class="list-card gmp-card">
      <template v-if="list.length > 0">
        <div v-for="item in list" :key="item.id" class="cert-item">
          <div class="item-main">
            <div class="item-title-line">
              <el-tag :type="item.certType === 2 ? 'warning' : 'info'" size="small" effect="light">
                {{ item.certTypeText }}
              </el-tag>
              <span class="item-title">{{ item.certName }}</span>
              <el-tag :type="statusTag(item.status)" size="small">{{ item.statusText }}</el-tag>
            </div>
            <div class="item-sub">
              <span>编号：{{ item.certNo || '—' }}</span>
              <span>发证机构：{{ item.issuer || '—' }}</span>
            </div>
            <div class="item-sub">
              <span>发证日期：{{ item.issueDate || '—' }}</span>
              <span>
                有效期至：{{ item.expireDate || '长期有效' }}
                <el-tag :type="expireTag(item.expireStatus)" size="small" style="margin-left: 6px">
                  {{ item.expireStatusText }}
                </el-tag>
              </span>
            </div>
            <!-- 驳回原因：让学员知道该补充什么材料 -->
            <div v-if="item.status === 2 && item.auditRemark" class="item-audit">
              驳回原因：{{ item.auditRemark }}
            </div>
          </div>
          <div class="item-actions">
            <el-button
              v-if="item.status !== 1"
              link
              type="primary"
              @click="openForm(item)"
            >修改</el-button>
            <el-button
              v-if="item.status !== 1"
              link
              type="danger"
              @click="handleDelete(item)"
            >删除</el-button>
          </div>
        </div>
      </template>
      <el-empty v-else description="暂未登记证书，点击右上角「登记证书」上传" />
    </div>

    <!-- 登记 / 修改弹窗 -->
    <el-dialog v-model="formVisible" :title="editingId ? '修改证书登记' : '登记证书'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="类型" prop="certType">
          <el-radio-group v-model="form.certType">
            <el-radio :label="1">外部培训</el-radio>
            <el-radio :label="2">资质证书</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="certName">
          <el-input v-model="form.certName" placeholder="如：压力容器操作证" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="证书编号" prop="certNo">
          <el-input v-model="form.certNo" placeholder="选填" maxlength="100" />
        </el-form-item>
        <el-form-item label="发证机构" prop="issuer">
          <el-input v-model="form.issuer" placeholder="选填" maxlength="200" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发证日期" prop="issueDate">
              <el-date-picker
                v-model="form.issueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期至" prop="expireDate">
              <el-date-picker
                v-model="form.expireDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="留空表示长期有效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="证明材料" prop="fileUrl">
          <el-input v-model="form.fileUrl" placeholder="扫描件/照片的存储路径（选填）" maxlength="500" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 我的资质证书（学员端）
 * 功能：查看本人外部培训与资质证书、登记新证书、修改/删除待审核或被驳回的记录
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, put, del } from '@/utils/request'

/** 列表数据 */
const list = ref([])
/** 加载中 */
const loading = ref(false)
/** 类型筛选（'' 表示全部） */
const filterType = ref('')

/** 登记弹窗可见性 */
const formVisible = ref(false)
/** 提交中 */
const submitting = ref(false)
/** 表单引用 */
const formRef = ref(null)
/** 编辑中的记录ID */
const editingId = ref(null)

/** 登记表单 */
const form = reactive({
  certType: 2,
  certName: '',
  certNo: '',
  issuer: '',
  issueDate: '',
  expireDate: '',
  fileUrl: '',
  remark: ''
})

/** 校验规则 */
const rules = {
  certType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  certName: [{ required: true, message: '请输入证书/培训名称', trigger: 'blur' }]
}

/** 统计：按审核状态与有效期状态汇总 */
const stats = computed(() => {
  const result = { approved: 0, pending: 0, rejected: 0, expiring: 0 }
  list.value.forEach((item) => {
    if (item.status === 1) result.approved += 1
    if (item.status === 0) result.pending += 1
    if (item.status === 2) result.rejected += 1
    // 临期与已过期合并提示，提醒学员及时换证
    if (item.expireStatus === 'expiring' || item.expireStatus === 'expired') result.expiring += 1
  })
  return result
})

/**
 * 审核状态 → 标签颜色
 * @param {number} status 0-待审核 1-已生效 2-已驳回
 * @returns {string} Element Plus 标签类型
 */
function statusTag(status) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[status] || 'info'
}

/**
 * 有效期状态 → 标签颜色
 * @param {string} status long_term/valid/expiring/expired
 * @returns {string} Element Plus 标签类型
 */
function expireTag(status) {
  return { long_term: 'info', valid: 'success', expiring: 'warning', expired: 'danger' }[status] || 'info'
}

/** 加载本人证书列表 */
async function loadList() {
  loading.value = true
  try {
    const params = {}
    if (filterType.value !== '') params.certType = filterType.value
    const res = await get('/study/certificates', params)
    list.value = res.data || []
  } catch (e) {
    console.error('查询我的证书失败', e)
  } finally {
    loading.value = false
  }
}

/**
 * 打开登记 / 修改弹窗
 * @param {object} row 行数据；为空表示新增
 */
function openForm(row) {
  editingId.value = row ? row.id : null
  Object.assign(form, {
    certType: row ? row.certType : 2,
    certName: row ? row.certName : '',
    certNo: row ? row.certNo || '' : '',
    issuer: row ? row.issuer || '' : '',
    issueDate: row ? row.issueDate || '' : '',
    expireDate: row ? row.expireDate || '' : '',
    fileUrl: row ? row.fileUrl || '' : '',
    remark: row ? row.remark || '' : ''
  })
  formVisible.value = true
}

/** 提交登记 / 修改 */
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      // 空日期转 null，避免后端 LocalDate 解析空串失败
      const payload = {
        ...form,
        issueDate: form.issueDate || null,
        expireDate: form.expireDate || null
      }
      if (editingId.value) {
        await put(`/study/certificates/${editingId.value}`, payload)
        ElMessage.success('修改成功，等待重新审核')
      } else {
        await post('/study/certificates', payload)
        ElMessage.success('提交成功，等待审核')
      }
      formVisible.value = false
      loadList()
    } catch (e) {
      console.error('提交证书登记失败', e)
    } finally {
      submitting.value = false
    }
  })
}

/**
 * 删除证书登记（仅待审核 / 已驳回可删）
 * @param {object} row 行数据
 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除《${row.certName}》的登记吗？`, '删除确认', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await del(`/study/certificates/${row.id}`)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    console.error('删除失败', e)
  }
}

onMounted(loadList)
</script>

<style scoped>
.cert-page {
  padding: 4px;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

/* 统计卡片：四列等宽 */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  padding: 16px;
  text-align: center;
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
}

.stat-num--valid {
  color: #10b981;
}

.stat-num--pending {
  color: #f59e0b;
}

.stat-num--rejected {
  color: #ef4444;
}

.stat-num--warn {
  color: #f97316;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
}

.filter-bar {
  padding: 12px 16px;
  margin-bottom: 16px;
}

.list-card {
  padding: 8px 16px;
}

.cert-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color, #ebeef5);
}

.cert-item:last-child {
  border-bottom: none;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1f2937);
}

.item-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary, #6b7280);
  line-height: 1.9;
}

.item-audit {
  margin-top: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
}

.item-actions {
  flex-shrink: 0;
}
</style>
