// pages/profile/external-cert.js - 我的资质证书（外部专项培训与资质证书登记）
const { get, post } = require('../../utils/request')

Page({
  data: {
    // 证书列表
    certList: [],
    // 加载中
    loading: true,
    // 表单弹层可见性
    formVisible: false,
    // 提交中
    submitting: false,
    // 类型下拉：索引与选项（0-外部培训 1-资质证书，对应后端 certType 1/2）
    typeOptions: ['外部培训', '资质证书'],
    typeIndex: 1,
    // 登记表单
    form: {
      certType: 2,
      certName: '',
      certNo: '',
      issuer: '',
      issueDate: '',
      expireDate: '',
      remark: ''
    }
  },

  onLoad() {
    this.loadList()
  },

  onPullDownRefresh() {
    this.loadList()
    wx.stopPullDownRefresh()
  },

  /** 加载本人证书列表 */
  loadList() {
    this.setData({ loading: true })
    get('/study/certificates', {}, { loading: false }).then(res => {
      const list = (res.data || []).map(item => {
        item.statusClass = this.statusClass(item.status)
        item.expireClass = this.expireClass(item.expireStatus)
        return item
      })
      this.setData({ certList: list, loading: false })
    }).catch(() => {
      this.setData({ loading: false })
    })
  },

  /**
   * 审核状态 → 样式类
   * @param {number} status 0-待审核 1-已生效 2-已驳回
   */
  statusClass(status) {
    if (status === 1) return 'status-valid'
    if (status === 2) return 'status-rejected'
    return 'status-pending'
  },

  /**
   * 有效期状态 → 文字颜色类
   * @param {string} expireStatus long_term/valid/expiring/expired
   */
  expireClass(expireStatus) {
    if (expireStatus === 'expired') return 'text-danger'
    if (expireStatus === 'expiring') return 'text-warning'
    if (expireStatus === 'valid') return 'text-success'
    return ''
  },

  /** 打开登记表单 */
  openForm() {
    this.setData({
      formVisible: true,
      typeIndex: 1,
      form: {
        certType: 2,
        certName: '',
        certNo: '',
        issuer: '',
        issueDate: '',
        expireDate: '',
        remark: ''
      }
    })
  },

  /** 关闭登记表单 */
  closeForm() {
    this.setData({ formVisible: false })
  },

  /** 类型切换：索引 0 → certType 1，索引 1 → certType 2 */
  onTypeChange(e) {
    const index = Number(e.detail.value)
    this.setData({ typeIndex: index, 'form.certType': index === 0 ? 1 : 2 })
  },

  /** 名称输入 */
  onNameInput(e) {
    this.setData({ 'form.certName': e.detail.value })
  },

  /** 编号输入 */
  onNoInput(e) {
    this.setData({ 'form.certNo': e.detail.value })
  },

  /** 发证机构输入 */
  onIssuerInput(e) {
    this.setData({ 'form.issuer': e.detail.value })
  },

  /** 发证日期选择 */
  onIssueDateChange(e) {
    this.setData({ 'form.issueDate': e.detail.value })
  },

  /** 有效期选择 */
  onExpireDateChange(e) {
    this.setData({ 'form.expireDate': e.detail.value })
  },

  /** 备注输入 */
  onRemarkInput(e) {
    this.setData({ 'form.remark': e.detail.value })
  },

  /** 提交登记 */
  submitForm() {
    const form = this.data.form
    if (!form.certName) {
      wx.showToast({ title: '请填写证书名称', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    post('/study/certificates', {
      certType: form.certType,
      certName: form.certName,
      certNo: form.certNo || null,
      issuer: form.issuer || null,
      issueDate: form.issueDate || null,
      expireDate: form.expireDate || null,
      remark: form.remark || null
    }).then(() => {
      wx.showToast({ title: '提交成功，等待审核', icon: 'none' })
      this.setData({ formVisible: false, submitting: false })
      this.loadList()
    }).catch(() => {
      this.setData({ submitting: false })
    })
  }
})
