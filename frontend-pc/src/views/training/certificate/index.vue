<template>
  <div class="page-container">
    <!-- 搜索栏：关键字、类型、审核状态、有效期状态 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="姓名/工号/证书名称/编号"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.certType" placeholder="全部类型" clearable style="width: 130px">
            <el-option label="外部培训" :value="1" />
            <el-option label="资质证书" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="待审核" :value="0" />
            <el-option label="已生效" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期">
          <el-select v-model="queryParams.expireStatus" placeholder="全部" clearable style="width: 130px">
            <el-option label="长期有效" value="long_term" />
            <el-option label="有效" value="valid" />
            <el-option label="临期" value="expiring" />
            <el-option label="已过期" value="expired" />
          </el-select>
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
          <el-icon><Plus /></el-icon> 登记证书
        </el-button>
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon> 导出 Excel
        </el-button>
        <span class="toolbar-tip">
          学员可在学员端自行申报外部培训与资质证书，经审核通过后生效；临期 30 天内自动标记为「临期」
        </span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="userName" label="姓名" width="100" show-overflow-tooltip />
        <el-table-column prop="employeeNo" label="工号" width="120" show-overflow-tooltip />
        <el-table-column prop="deptName" label="所属部门" width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.certType === 2 ? 'warning' : 'info'" size="small">{{ row.certTypeText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="certName" label="证书/培训名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="certNo" label="证书编号" width="160" show-overflow-tooltip />
        <el-table-column prop="issuer" label="发证机构" min-width="150" show-overflow-tooltip />
        <el-table-column label="发证日期" width="110" align="center">
          <template #default="{ row }">{{ row.issueDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="有效期至" width="130" align="center">
          <template #default="{ row }">
            <div>{{ row.expireDate || '长期有效' }}</div>
            <!-- 剩余天数仅在存在有效期时展示 -->
            <div v-if="row.remainDays !== null && row.remainDays !== undefined" class="sub-text">
              {{ row.remainDays >= 0 ? `剩余 ${row.remainDays} 天` : `已超期 ${-row.remainDays} 天` }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="有效期状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="expireTagType(row.expireStatus)" size="small">{{ row.expireStatusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" link type="success" @click="openAudit(row)">审核</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 登记 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="持证人" prop="userId">
          <el-select
            v-model="formData.userId"
            placeholder="请选择持证人"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="`${u.realName}（${u.username}）`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="certType">
          <el-radio-group v-model="formData.certType">
            <el-radio :label="1">外部培训</el-radio>
            <el-radio :label="2">资质证书</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="certName">
          <el-input v-model="formData.certName" placeholder="如：压力容器操作证" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="证书编号" prop="certNo">
          <el-input v-model="formData.certNo" placeholder="选填" maxlength="100" />
        </el-form-item>
        <el-form-item label="发证机构" prop="issuer">
          <el-input v-model="formData.issuer" placeholder="选填" maxlength="200" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="发证日期" prop="issueDate">
              <el-date-picker
                v-model="formData.issueDate"
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
                v-model="formData.expireDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="留空表示长期有效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="证明材料" prop="fileUrl">
          <el-input v-model="formData.fileUrl" placeholder="扫描件/照片的存储路径（选填）" maxlength="500" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 审核对话框 -->
    <el-dialog v-model="auditVisible" title="资质证书审核" width="520px" destroy-on-close>
      <el-descriptions v-if="currentRow" :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="持证人">{{ currentRow.userName }}（{{ currentRow.employeeNo }}）</el-descriptions-item>
        <el-descriptions-item label="证书名称">{{ currentRow.certName }}</el-descriptions-item>
        <el-descriptions-item label="有效期至">{{ currentRow.expireDate || '长期有效' }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="90px">
        <el-form-item label="审核结论" prop="status">
          <el-radio-group v-model="auditForm.status">
            <el-radio :label="1">通过</el-radio>
            <el-radio :label="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见" prop="auditRemark">
          <el-input
            v-model="auditForm.auditRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="驳回时必填，说明需补充的材料"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditLoading" @click="submitAudit">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 外部专项培训与资质证书登记（管理端）
 * 功能：台账查询、代录登记、审核（通过/驳回）、编辑、删除、Excel 导出
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import service, { get, post, put, del } from '@/utils/request'

/** 查询条件 */
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: '',
  certType: null,
  status: null,
  expireStatus: ''
})

/** 列表加载状态 */
const loading = ref(false)
/** 列表数据 */
const tableData = ref([])
/** 总条数 */
const total = ref(0)
/** 学员下拉选项 */
const userOptions = ref([])

/** 登记弹窗可见性 */
const dialogVisible = ref(false)
/** 提交中 */
const submitLoading = ref(false)
/** 表单引用 */
const formRef = ref(null)
/** 编辑中的记录ID，为空表示新增 */
const editingId = ref(null)

/** 登记表单 */
const formData = reactive({
  userId: null,
  certType: 2,
  certName: '',
  certNo: '',
  issuer: '',
  issueDate: '',
  expireDate: '',
  fileUrl: '',
  remark: ''
})

/** 登记表单校验规则 */
const formRules = {
  userId: [{ required: true, message: '请选择持证人', trigger: 'change' }],
  certType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  certName: [{ required: true, message: '请输入证书/培训名称', trigger: 'blur' }]
}

/** 弹窗标题 */
const dialogTitle = computed(() => (editingId.value ? '编辑资质证书' : '登记资质证书'))

/** 审核弹窗可见性 */
const auditVisible = ref(false)
/** 审核提交中 */
const auditLoading = ref(false)
/** 当前审核行 */
const currentRow = ref(null)
/** 审核表单引用 */
const auditFormRef = ref(null)
/** 审核表单 */
const auditForm = reactive({ status: 1, auditRemark: '' })
/** 审核校验规则：驳回时审核意见必填 */
const auditRules = {
  status: [{ required: true, message: '请选择审核结论', trigger: 'change' }],
  auditRemark: [
    {
      validator: (rule, value, callback) => {
        if (auditForm.status === 2 && !value) {
          callback(new Error('驳回时必须填写审核意见'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

/**
 * 有效期状态 → 标签颜色
 * @param {string} status long_term/valid/expiring/expired
 * @returns {string} Element Plus 标签类型
 */
function expireTagType(status) {
  return { long_term: 'info', valid: 'success', expiring: 'warning', expired: 'danger' }[status] || 'info'
}

/**
 * 审核状态 → 标签颜色
 * @param {number} status 0-待审核 1-已生效 2-已驳回
 * @returns {string} Element Plus 标签类型
 */
function statusTagType(status) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[status] || 'info'
}

/**
 * 分页查询证书台账
 */
async function fetchData() {
  loading.value = true
  try {
    const params = { page: queryParams.page, size: queryParams.size }
    if (queryParams.keyword) params.keyword = queryParams.keyword
    if (queryParams.certType !== null && queryParams.certType !== '') params.certType = queryParams.certType
    if (queryParams.status !== null && queryParams.status !== '') params.status = queryParams.status
    if (queryParams.expireStatus) params.expireStatus = queryParams.expireStatus
    const res = await get('/training/certificates', params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('查询资质证书失败', e)
  } finally {
    loading.value = false
  }
}

/** 搜索：回到第一页 */
function handleSearch() {
  queryParams.page = 1
  fetchData()
}

/** 重置查询条件 */
function handleReset() {
  queryParams.keyword = ''
  queryParams.certType = null
  queryParams.status = null
  queryParams.expireStatus = ''
  queryParams.page = 1
  fetchData()
}

/** 加载学员下拉选项 */
async function loadUsers() {
  try {
    const res = await get('/system/users/list')
    userOptions.value = res.data || []
  } catch (e) {
    console.error('加载学员列表失败', e)
  }
}

/** 打开新增弹窗 */
function handleAdd() {
  editingId.value = null
  Object.assign(formData, {
    userId: null,
    certType: 2,
    certName: '',
    certNo: '',
    issuer: '',
    issueDate: '',
    expireDate: '',
    fileUrl: '',
    remark: ''
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗
 * @param {object} row 行数据
 */
function handleEdit(row) {
  editingId.value = row.id
  Object.assign(formData, {
    userId: row.userId,
    certType: row.certType,
    certName: row.certName,
    certNo: row.certNo || '',
    issuer: row.issuer || '',
    issueDate: row.issueDate || '',
    expireDate: row.expireDate || '',
    fileUrl: row.fileUrl || '',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

/** 提交登记 / 编辑 */
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      // 空日期转 null，避免后端 LocalDate 解析空串失败
      const payload = {
        ...formData,
        issueDate: formData.issueDate || null,
        expireDate: formData.expireDate || null
      }
      if (editingId.value) {
        await put(`/training/certificates/${editingId.value}`, payload)
        ElMessage.success('编辑成功')
      } else {
        await post('/training/certificates', payload)
        ElMessage.success('登记成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e) {
      console.error('保存资质证书失败', e)
    } finally {
      submitLoading.value = false
    }
  })
}

/**
 * 打开审核弹窗
 * @param {object} row 行数据
 */
function openAudit(row) {
  currentRow.value = row
  auditForm.status = 1
  auditForm.auditRemark = ''
  auditVisible.value = true
}

/** 提交审核 */
async function submitAudit() {
  if (!auditFormRef.value) return
  await auditFormRef.value.validate(async (valid) => {
    if (!valid) return
    auditLoading.value = true
    try {
      await post(`/training/certificates/${currentRow.value.id}/audit`, {
        status: auditForm.status,
        auditRemark: auditForm.auditRemark || null
      })
      ElMessage.success('审核完成')
      auditVisible.value = false
      fetchData()
    } catch (e) {
      console.error('审核失败', e)
    } finally {
      auditLoading.value = false
    }
  })
}

/**
 * 删除登记
 * @param {object} row 行数据
 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.userName}」的《${row.certName}》登记吗？删除后数据将逻辑保留以便追溯。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch (e) {
    // 用户取消
    return
  }
  try {
    await del(`/training/certificates/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {
    console.error('删除失败', e)
  }
}

/** 导出 Excel（二进制流下载） */
async function handleExport() {
  try {
    const params = {}
    if (queryParams.keyword) params.keyword = queryParams.keyword
    if (queryParams.certType !== null && queryParams.certType !== '') params.certType = queryParams.certType
    if (queryParams.status !== null && queryParams.status !== '') params.status = queryParams.status
    if (queryParams.expireStatus) params.expireStatus = queryParams.expireStatus
    const blob = await service.get('/training/certificates/export', { params, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.download = '资质证书台账.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    console.error('导出失败', e)
  }
}

onMounted(() => {
  loadUsers()
  fetchData()
})
</script>

<style scoped>
.page-container {
  padding: 16px;
}

.search-card {
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar-tip {
  color: #909399;
  font-size: 12px;
}

.sub-text {
  color: #909399;
  font-size: 12px;
}
</style>
