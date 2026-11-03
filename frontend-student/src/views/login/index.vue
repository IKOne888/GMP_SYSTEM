<template>
  <!-- 登录页：GMP 洁净车间主题背景 + 右侧登录卡片 -->
  <div class="login-container" :style="{ backgroundImage: `url(${loginBg})` }">
    <!-- 左上品牌标识 -->
    <div class="login-brand">
      <div class="login-brand-logo">
        <el-icon :size="26"><Stamp /></el-icon>
      </div>
      <div class="login-brand-text">
        <h1>GMP 培训学习中心</h1>
        <p>药品生产质量管理规范 · 员工培训平台</p>
      </div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card">
      <div class="login-header">
        <h2>学员登录</h2>
        <p class="login-subtitle">请使用企业工号登录学习中心</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <!-- 用户名 -->
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名 / 工号"
            size="large"
            :prefix-icon="User"
            :maxlength="50"
            clearable
          />
        </el-form-item>

        <!-- 密码 -->
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
            :maxlength="50"
          />
        </el-form-item>

        <!-- 图形验证码 -->
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="loginForm.captchaCode"
              placeholder="请输入验证码"
              size="large"
              :maxlength="4"
              class="captcha-input"
            />
            <div class="captcha-img" @click="loadCaptcha" title="点击刷新验证码">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <span v-else class="captcha-loading">加载中...</span>
            </div>
          </div>
        </el-form-item>

        <!-- 记住用户名 -->
        <el-form-item>
          <div class="login-options">
            <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
          </div>
        </el-form-item>

        <!-- 登录按钮 -->
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-tip">
        <el-icon><InfoFilled /></el-icon>
        连续 5 次输入错误将锁定账号 10 分钟
      </p>

      <!-- 切换入口：学员端登录页 → 管理端登录页 -->
      <div class="login-switch">
        <span class="switch-label">管理员账号登录？</span>
        <el-button link type="primary" class="switch-btn" @click="switchToAdmin">
          使用管理员账号登录 →
        </el-button>
      </div>
    </div>

    <!-- 页脚 -->
    <div class="login-footer">
      <p>GMP Training Learning Center &copy; 2026</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchCaptcha } from '@/api/auth'
import { useUserStore } from '@/store/user'
import loginBg from '@/assets/login-bg.png'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 表单实例 */
const loginFormRef = ref(null)
/** 登录按钮加载状态 */
const loading = ref(false)
/** 验证码图片（Base64） */
const captchaImage = ref('')
/** 验证码标识（提交时回传后端校验） */
const captchaKey = ref('')
/** 记住的用户名存储键 */
const REMEMBER_KEY = 'student_remembered_username'

/** 登录表单数据 */
const loginForm = reactive({
  username: '',
  password: '',
  captchaCode: '',
  remember: false
})

/** 表单校验规则 */
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

/**
 * 获取图形验证码
 */
async function loadCaptcha() {
  try {
    const res = await fetchCaptcha()
    const data = res.data || {}
    captchaKey.value = data.captchaKey || ''
    captchaImage.value = data.captchaImage || ''
    loginForm.captchaCode = ''
  } catch (e) {
    console.error('获取验证码失败:', e)
  }
}

/**
 * 读取本地记住的用户名
 */
function loadRememberedUser() {
  const remembered = localStorage.getItem(REMEMBER_KEY)
  if (remembered) {
    loginForm.username = remembered
    loginForm.remember = true
  }
}

/**
 * 保存 / 清除记住的用户名
 */
function saveRememberedUser() {
  if (loginForm.remember) {
    localStorage.setItem(REMEMBER_KEY, loginForm.username)
  } else {
    localStorage.removeItem(REMEMBER_KEY)
  }
}

/**
 * 提交登录
 */
async function handleLogin() {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({
      username: loginForm.username,
      password: loginForm.password,
      captchaKey: captchaKey.value,
      captchaCode: loginForm.captchaCode
    })
    saveRememberedUser()
    ElMessage.success('登录成功，欢迎回来')
    // 支持从被拦截页面回跳
    const redirect = route.query.redirect
    router.push(redirect ? String(redirect) : '/dashboard')
  } catch (e) {
    // 登录失败后刷新验证码，避免验证码复用与暴力破解
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRememberedUser()
  loadCaptcha()
})

/**
 * 跳转到管理端登录页（新标签页打开 http://localhost:3000）
 * 开发环境两端独立端口，生产环境可改为同域名不同路径
 */
function switchToAdmin() {
  const adminUrl = `${window.location.protocol}//${window.location.hostname}:3000/login`
  window.open(adminUrl, '_blank')
}
</script>

<style scoped>
.login-container {
  position: relative;
  width: 100%;
  height: 100vh;
  background-color: #e8f2fb;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 左上品牌标识：叠加在背景图上，使用白色文字 + 半透明底 */
.login-brand {
  position: absolute;
  top: 40px;
  left: 48px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #12406e;
}

.login-brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  color: #fff;
  background: var(--brand-gradient);
  box-shadow: 0 8px 20px rgba(22, 119, 255, 0.32);
}

.login-brand-text h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 1px;
}

.login-brand-text p {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(18, 64, 110, 0.7);
}

/* 登录卡片：右侧居中，避开背景图主体图形 */
.login-card {
  position: absolute;
  left: 66%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 396px;
  padding: 36px 32px 28px;
  background: rgba(255, 255, 255, 0.97);
  border-radius: 16px;
  box-shadow: 0 18px 48px rgba(31, 78, 138, 0.24);
  backdrop-filter: blur(3px);
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.login-header {
  text-align: center;
  margin-bottom: 22px;
}

.login-header h2 {
  margin: 0;
  font-size: 22px;
  color: var(--text-primary);
}

.login-subtitle {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.captcha-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  width: 118px;
  height: 40px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: pointer;
  flex-shrink: 0;
  background: #f5f7fa;
}

.captcha-img img {
  width: 100%;
  height: 100%;
  display: block;
}

.captcha-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  font-size: 12px;
  color: var(--text-secondary);
}

.login-options {
  width: 100%;
  display: flex;
  align-items: center;
}

/* 品牌渐变登录按钮 */
.login-btn {
  width: 100%;
  background: var(--brand-gradient);
  border: none;
  font-size: 16px;
  letter-spacing: 4px;
}

.login-btn:hover {
  opacity: 0.92;
  background: var(--brand-gradient);
}

.login-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

/* 两端切换入口：居中文案 + link 按钮 */
.login-switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--border-color);
  font-size: 13px;
}

.switch-label {
  color: var(--text-secondary);
}

.switch-btn {
  padding: 0;
  font-size: 13px;
  font-weight: 500;
}

.login-footer {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(31, 78, 138, 0.75);
  font-size: 13px;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.8);
}
</style>
