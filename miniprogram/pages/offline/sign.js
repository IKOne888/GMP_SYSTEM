// pages/offline/sign.js - 线下培训电子签到
//
// 对应后端接口（学员端 /study/** 前缀，见 StudyOfflineController）：
//   GET  /study/offline/signable          本人可参与的线下培训场次（含是否已签到、场次阶段）
//   POST /study/offline/sign              学员自主签到（trainingId + signCode）
//   GET  /study/offline/records           本人签到历史
// 数据隔离由后端 Service 层通过登录态强制实现，前端不传 userId。
// 签到时间窗口、签到码与防重复签到均由后端校验，前端只做输入格式的前置拦截。
const app = getApp()
const { get, post, formatTime } = require('../../utils/request')

/** 场次阶段字典：与后端 resolvePhase() 返回值保持一致 */
const PHASE_MAP = {
  upcoming: { text: '未开始', cls: 'tag-pending' },
  signing: { text: '签到中', cls: 'tag-done' },
  ended: { text: '已结束', cls: 'tag-overdue' },
  cancelled: { text: '已取消', cls: 'tag-rejected' }
}

/**
 * 截取「月-日 时:分」用于卡片上的紧凑展示
 * @param {string} dateStr 后端返回的时间字符串
 * @returns {string} 形如 11-03 09:00，无效值返回空串
 */
function shortTime(dateStr) {
  const full = formatTime(dateStr)
  return full ? full.substring(5, 16) : ''
}

Page({
  data: {
    // 当前页签：signable-可签到场次 records-我的签到记录
    tab: 'signable',
    // 可签到场次列表
    signableList: [],
    // 我的签到记录列表
    recordList: [],
    // 加载标记（避免重复请求）
    loadingSignable: false,
    loadingRecord: false
  },

  onLoad() {
    // 未登录重定向到登录页
    if (!app.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/index' })
      return
    }
    this.loadSignable()
    this.loadRecords()
  },

  onPullDownRefresh() {
    // 下拉刷新：同时刷新两个页签的数据，保证阶段与签到状态最新
    this.loadSignable()
    this.loadRecords()
    wx.stopPullDownRefresh()
  },

  /** 切换页签 */
  onTabTap(e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.tab) return
    this.setData({ tab: tab })
    // 首次进入页签时按需拉取，减少无效请求
    if (tab === 'signable' && this.data.signableList.length === 0) {
      this.loadSignable()
    }
    if (tab === 'records' && this.data.recordList.length === 0) {
      this.loadRecords()
    }
  },

  /** 加载可签到场次 */
  loadSignable() {
    if (this.data.loadingSignable) return
    this.setData({ loadingSignable: true })

    get('/study/offline/signable', {}, { loading: false }).then(res => {
      // 后端返回 List<StudyOfflineTrainingVO>
      const records = res.data || []
      const list = records.map(item => {
        const phaseMeta = PHASE_MAP[item.phase] || { text: '未知', cls: '' }
        return {
          id: item.id,
          title: item.title,
          trainer: item.trainer || '待定',
          location: item.location || '待定',
          phase: item.phase,
          phaseText: phaseMeta.text,
          phaseClass: phaseMeta.cls,
          signed: !!item.signed,
          signTimeText: formatTime(item.signTime),
          // 培训时间段与签到窗口（月-日 时:分）
          timeRangeText: shortTime(item.startTime) + ' ~ ' + shortTime(item.endTime),
          signWindowText: shortTime(item.signStartTime) + ' ~ ' + shortTime(item.signEndTime),
          // 是否可点击签到：仅「签到中」且未签到时允许
          canSign: item.phase === 'signing' && !item.signed
        }
      })
      this.setData({ signableList: list, loadingSignable: false })
    }).catch(() => {
      this.setData({ loadingSignable: false })
    })
  },

  /** 加载我的签到记录 */
  loadRecords() {
    if (this.data.loadingRecord) return
    this.setData({ loadingRecord: true })

    get('/study/offline/records', {}, { loading: false }).then(res => {
      // 后端返回 List<StudyOfflineRecordVO>，已按签到时间倒序
      const records = res.data || []
      const list = records.map(item => {
        return {
          id: item.id,
          trainingTitle: item.trainingTitle,
          trainer: item.trainer || '待定',
          location: item.location || '待定',
          timeRangeText: shortTime(item.startTime) + ' ~ ' + shortTime(item.endTime),
          signTimeText: formatTime(item.signTime),
          signTypeText: item.signTypeText || (item.signType === 2 ? '管理员补签' : '自主签到')
        }
      })
      this.setData({ recordList: list, loadingRecord: false })
    }).catch(() => {
      this.setData({ loadingRecord: false })
    })
  },

  /** 点击「去签到」：输入 6 位签到码后提交 */
  onSignTap(e) {
    const id = e.currentTarget.dataset.id
    const target = this.data.signableList.find(item => item.id === id)
    if (!target) return
    // 已签到或不在签到窗口内直接提示，避免无谓的接口调用
    if (target.signed) {
      wx.showToast({ title: '您已签到', icon: 'none' })
      return
    }
    if (!target.canSign) {
      wx.showToast({ title: '当前不在签到时间窗口内', icon: 'none' })
      return
    }

    wx.showModal({
      title: '电子签到',
      editable: true,
      placeholderText: '请输入6位签到码',
      content: '',
      success: (res) => {
        if (!res.confirm) return
        // 去除首尾空白，兼容复制粘贴带入的空格
        const signCode = String(res.content || '').trim()
        if (!/^\d{6}$/.test(signCode)) {
          wx.showToast({ title: '签到码为6位数字', icon: 'none' })
          return
        }
        this.doSign(id, signCode)
      }
    })
  },

  /**
   * 提交签到请求
   * @param {number} trainingId 场次ID
   * @param {string} signCode 6位签到码
   */
  doSign(trainingId, signCode) {
    post('/study/offline/sign', { trainingId: trainingId, signCode: signCode }, { loading: true }).then(() => {
      wx.showToast({ title: '签到成功', icon: 'success' })
      // 刷新两个页签：场次状态变为已签到，记录页新增一条
      this.loadSignable()
      this.loadRecords()
    }).catch(() => {
      // 失败原因（窗口未开始/已结束、签到码错误、重复签到）由 request 统一提示
    })
  }
})
