import { get, post } from '@/utils/request'

/**
 * 学员端「线下培训电子签到」接口封装
 * 对应后端 StudyOfflineController（/study/offline/**）。
 * 签到身份由后端从登录态解析，前端不传 userId。
 */

/**
 * 查询本人可参与的线下培训场次（含场次阶段与是否已签到）
 */
export function getSignableTrainings() {
  return get('/study/offline/signable')
}

/**
 * 学员自主签到
 * @param {object} data { trainingId, signCode }
 */
export function submitSign(data) {
  return post('/study/offline/sign', data)
}

/**
 * 查询本人签到历史
 */
export function getMySignRecords() {
  return get('/study/offline/records')
}
