import axios from 'axios'

const client = axios.create({
  // Backend context-path is /backend; the frontend is served under /front/.
  baseURL: '/backend/api',
  timeout: 15000
})

client.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 0) {
      return body.data
    }
    return Promise.reject(new Error(body?.message || '请求失败'))
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  }
)

export default client
