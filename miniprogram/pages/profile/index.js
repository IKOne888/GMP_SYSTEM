// pages/profile/index.js - 个人中心（tabBar 第 3 项）
const app = getApp()
const { get, post, put, formatTime, clearAuth } = require('../../utils/request')

Page({
  data: {
    // 个人信息（UserInfoVO）
    profile: {},
    // 头像首字（WXML 表达式不支持 substring 等 JS 方法调用，故在 JS 中预先计算）
    avatarText: 'U',
    // 未读消息数
    unreadCount: 0,
    // 修改资料弹窗
    showEditModal: false,
    // 编辑表单
    editForm: { phone: '', email: '' },
    // 修改密码弹窗
    showPwdModal: false,
    // 密码表单
    pwdForm: { oldPassword: '', newPassword: '', confirmPassword: '' }
  },

  onLoad() {
    // 未登录重定向
    if (!app.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/index' })
      return
    }
    this.loadProfile()
    this.loadUnreadCount()
  },

  onShow() {
    // 回到本页刷新
    if (app.isLoggedIn()) {
      this.loadProfile()
      this.loadUnreadCount()
    }
  },

  onPullDownRefresh() {
    this.loadProfile()
    this.loadUnreadCount()
    wx.stopPullDownRefresh()
  },

  /** 加载个人信息 */
  loadProfile() {
    get('/study/profile', {}, { loading: false }).then(res => {
      const profile = res.data || {}
      profile.lastLoginTimeText = formatTime(profile.lastLoginTime)
      // 计算头像首字：优先真实姓名，其次用户名，兜底为 U
      const nameSource = profile.realName || profile.username || 'U'
      const avatarText = String(nameSource).substring(0, 1)
      this.setData({ profile: profile, avatarText: avatarText })
      // 同步全局用户信息
      app.globalData.userInfo = profile
    }).catch(() => {})
  },

  /** 加载未读消息数 */
  loadUnreadCount() {
    get('/study/messages/unread-count', {}, { loading: false, showError: false }).then(res => {
      this.setData({ unreadCount: Number(res.data) || 0 })
    }).catch(() => {})
  },

  // ==================== 修改资料 ====================

  /** 打开修改资料弹窗 */
  onEditProfile() {
    this.setData({
      showEditModal: true,
      editForm: {
        phone: this.data.profile.phone || '',
        email: this.data.profile.email || ''
      }
    })
  },

  /** 输入手机号 */
  onPhoneInput(e) {
    this.setData({ 'editForm.phone': e.detail.value })
  },

  /** 输入邮箱 */
  onEmailInput(e) {
    this.setData({ 'editForm.email': e.detail.value })
  },

  /** 提交修改资料 */
  onSubmitProfile() {
    const { phone, email } = this.data.editForm
    put('/study/profile', { phone: phone.trim(), email: email.trim() }, { loading: true }).then(() => {
      wx.showToast({ title: '保存成功', icon: 'success' })
      this.setData({ showEditModal: false })
      this.loadProfile()
    }).catch(() => {})
  },

  /** 空方法：用于弹窗内容区阻止点击穿透 */
  noop() {},

  /** 关闭修改资料弹窗 */
  onCloseEditModal() {
    this.setData({ showEditModal: false })
  },

  // ==================== 修改密码 ====================

  /** 打开修改密码弹窗 */
  onEditPassword() {
    this.setData({
      showPwdModal: true,
      pwdForm: { oldPassword: '', newPassword: '', confirmPassword: '' }
    })
  },

  /** 输入原密码 */
  onOldPwdInput(e) {
    this.setData({ 'pwdForm.oldPassword': e.detail.value })
  },

  /** 输入新密码 */
  onNewPwdInput(e) {
    this.setData({ 'pwdForm.newPassword': e.detail.value })
  },

  /** 输入确认密码 */
  onConfirmPwdInput(e) {
    this.setData({ 'pwdForm.confirmPassword': e.detail.value })
  },

  /** 提交修改密码 */
  onSubmitPassword() {
    const { oldPassword, newPassword, confirmPassword } = this.data.pwdForm

    // 前置校验
    if (!oldPassword) {
      wx.showToast({ title: '请输入原密码', icon: 'none' })
      return
    }
    // 密码强度：6-20 位且包含字母与数字
    if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,20}$/.test(newPassword)) {
      wx.showToast({ title: '新密码需6-20位且包含字母和数字', icon: 'none' })
      return
    }
    if (newPassword !== confirmPassword) {
      wx.showToast({ title: '两次输入的密码不一致', icon: 'none' })
      return
    }

    put('/study/profile/password', { oldPassword: oldPassword, newPassword: newPassword }, { loading: true }).then(() => {
      wx.showToast({ title: '密码修改成功，请重新登录', icon: 'none' })
      this.setData({ showPwdModal: false })
      // 修改密码后需重新登录
      setTimeout(() => {
        this.onLogout()
      }, 1200)
    }).catch(() => {})
  },

  /** 关闭修改密码弹窗 */
  onClosePwdModal() {
    this.setData({ showPwdModal: false })
  },

  // ==================== 页面跳转 ====================

  /** 学习记录 */
  onGoRecord() {
    wx.navigateTo({ url: '/pages/profile/record' })
  },

  /** 培训档案 / 证书 */
  onGoCertificate() {
    wx.navigateTo({ url: '/pages/profile/certificate' })
  },

  /** 线下培训电子签到 */
  onGoOffline() {
    wx.navigateTo({ url: '/pages/offline/sign' })
  },

  /** 培训需求申报（进入我的申报列表） */
  onGoDemand() {
    wx.navigateTo({ url: '/pages/demand/list' })
  },

  /** 消息中心 */
  onGoMessage() {
    wx.navigateTo({ url: '/pages/message/list' })
  },

  // ==================== 退出登录 ====================

  /** 退出登录（二次确认） */
  onLogout() {
    wx.showModal({
      title: '提示',
      content: '确认退出登录吗？',
      success: (res) => {
        if (!res.confirm) return
        // 调用后端登出（失败也不阻塞本地清理）
        post('/auth/logout', {}, { loading: false, showError: false }).catch(() => {})
        // 清理本地登录态
        app.clearLoginInfo()
        clearAuth()
        wx.reLaunch({ url: '/pages/login/index' })
      }
    })
  }
})