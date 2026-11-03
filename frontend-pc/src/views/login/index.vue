<template>
  <!-- 登录页容器：使用 GMP 主题图片作为全屏背景 -->
  <div class="login-container" :style="{ backgroundImage: `url(${loginBg})` }">
    <div class="login-card">
      <div class="login-header">
        <el-icon size="40" color="#409EFF"><Reading /></el-icon>
        <h2>GMP培训管理系统</h2>
        <p class="login-subtitle">药品生产质量管理规范培训平台</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
            :maxlength="50"
            clearable
          />
        </el-form-item>
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
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="loginForm.captchaCode"
              placeholder="请输入验证码"
              size="large"
              :maxlength="4"
              class="captcha-input"
            />
            <div class="captcha-img" @click="fetchCaptcha" title="点击刷新验证码">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <span v-else class="captcha-loading">加载中...</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <div class="login-options">
            <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
          </div>
        </el-form-item>
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
    </div>
    <!-- 切换入口：管理端登录页 → 学员端登录页 -->
    <div class="login-switch">
      <span class="switch-label">学员账号登录？</span>
      <el-button link type="primary" class="switch-btn" @click="switchToStudent">
        使用学员账号登录 →
      </el-button>
    </div>
    <div class="login-footer">
      <p>GMP Training Management System &copy; 2026</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { post, get } from '@/utils/request'
// 登录页背景图（GMP 主题：洁净车间 + 数据看板 + 安全盾牌）
import loginBg from '@/assets/login-bg.png'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')

const loginForm = reactive({
  username: '',
  password: '',
  captchaCode: '',
  remember: false
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于6位', trigger: 'blur' }
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

// 获取图形验证码
async function fetchCaptcha() {
  try {
    const res = await get('/auth/captcha')
    const data = res.data || {}
    captchaKey.value = data.captchaKey
    captchaImage.value = data.captchaImage
    loginForm.captchaCode = ''
  } catch (error) {
    console.error('获取验证码失败:', error)
  }
}

// 读取记住的用户名
function loadRememberedUser() {
  const remembered = localStorage.getItem('rememberedUsername')
  if (remembered) {
    loginForm.username = remembered
    loginForm.remember = true
  }
}

// 记住/清除用户名
function saveRememberedUser() {
  if (loginForm.remember) {
    localStorage.setItem('rememberedUsername', loginForm.username)
  } else {
    localStorage.removeItem('rememberedUsername')
  }
}

async function handleLogin() {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await post('/auth/login', {
      username: loginForm.username,
      password: loginForm.password,
      captchaKey: captchaKey.value,
      captchaCode: loginForm.captchaCode
    })

    // 存储 token 和用户信息
    localStorage.setItem('token', res.data?.token || res.token)
    localStorage.setItem('userInfo', JSON.stringify(res.data?.user || res.user || { username: loginForm.username }))
    saveRememberedUser()

    ElMessage.success('登录成功')
    router.push('/')
  } catch (error) {
    console.error('登录失败:', error)
    // 登录失败后刷新验证码（防止连续爆破）
    fetchCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRememberedUser()
  fetchCaptcha()
})

/**
 * 跳转到学员端登录页（新标签页打开 http://localhost:3002）
 * 开发环境两端独立端口，生产环境可改为同域名不同路径
 */
function switchToStudent() {
  const studentUrl = `${window.location.protocol}//${window.location.hostname}:3002/login`
  window.open(studentUrl, '_blank')
}
</script>

<style scoped>
.login-container {
  /* 相对定位：作为卡片与版权文字的定位参照 */
  position: relative;
  width: 100%;
  height: 100vh;
  /* 背景图铺满全屏，居中显示；background-color 为图片加载前的兜底底色 */
  background-color: #e8f2fb;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.login-card {
  /* 定位到背景图右侧三分区、垂直居中，避开右侧盾牌图形，整体更均衡 */
  position: absolute;
  left: 66%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 384px;
  padding: 36px 32px;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 14px;
  box-shadow: 0 18px 48px rgba(31, 78, 138, 0.24);
  backdrop-filter: blur(2px);
}

.login-header {
  text-align: center;
  margin-bottom: 24px;
}

.login-header h2 {
  margin-top: 12px;
  font-size: 24px;
  color: #333;
}

.login-subtitle {
  margin-top: 8px;
  font-size: 14px;
  color: #999;
}

.login-form {
  margin-top: 14px;
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
  width: 120px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
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
  color: #999;
}

.login-options {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.login-btn {
  width: 100%;
}

/* 两端切换入口：居中显示在登录卡片下方 */
.login-switch {
  position: absolute;
  left: 66%;
  top: calc(50% + 260px);
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #555;
}

.switch-label {
  color: rgba(31, 78, 138, 0.7);
}

.switch-btn {
  padding: 0;
  font-size: 13px;
  font-weight: 500;
}

.login-footer {
  /* 固定在视口底部居中显示 */
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  /* 背景图为浅色，底部版权文字改用深色以保证可读性 */
  color: rgba(31, 78, 138, 0.75);
  font-size: 13px;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.8);
}
</style>
