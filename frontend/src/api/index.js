import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  register: (data) => request.post('/auth/register', data),
  me: () => request.get('/auth/me'),
  changePassword: (data) => request.put('/auth/password', data)
}

export const activityApi = {
  list: (params) => request.get('/activities', { params }),
  detail: (id) => request.get(`/activities/${id}`),

  adminList: (params) => request.get('/admin/activities', { params }),
  create: (data) => request.post('/admin/activities', data),
  update: (id, data) => request.put(`/admin/activities/${id}`, data),
  remove: (id) => request.delete(`/admin/activities/${id}`)
}

export const signupApi = {
  signup: (activityId) => request.post(`/activities/${activityId}/signup`),
  cancel: (activityId) => request.delete(`/activities/${activityId}/signup`),
  mine: () => request.get('/signups/my')
}

export const checkinApi = {
  /** 学生用签到码核销自己 */
  checkin: (activityId, code) => request.post(`/activities/${activityId}/checkin`, { code }),

  /** 管理端：当前签到码 */
  code: (activityId) => request.get(`/admin/activities/${activityId}/checkin-code`),

  /** 管理端：核销台数据 */
  board: (activityId) => request.get(`/admin/activities/${activityId}/checkins`),

  /** 管理端：手动核销某条报名 */
  manual: (activityId, signupId) =>
    request.post(`/admin/activities/${activityId}/checkins`, { signupId })
}

export const dashboardApi = {
  /** 跨活动看板：总览 + 报名趋势 + 活动排行 */
  overview: () => request.get('/admin/dashboard')
}
