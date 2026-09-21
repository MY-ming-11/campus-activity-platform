import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 后端所有响应都是 { code, msg, data }，code 为 0 才算成功。
 * 这里统一把 data 拆出来返回，业务代码就不用每次都写 res.data.data。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 401) {
      redirectToLogin()
      return Promise.reject(new Error(body.msg))
    }
    // 业务失败统一在这里提示，页面只管正常流程
    ElMessage.error(body.msg || '操作失败')
    return Promise.reject(new Error(body.msg))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      redirectToLogin()
      return Promise.reject(error)
    }
    ElMessage.error(status === 403 ? '没有权限执行该操作' : '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

function redirectToLogin() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

export default request
