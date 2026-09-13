import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import config from '@/config'
import { _notice } from './index'

export const axiosInstance = axios.create({
  baseURL: config.baseUrl,
  timeout: 120000
})

// request拦截器
axiosInstance.interceptors.request.use(
  (config) => {
    // 如果没有设置Content-Type，默认application/json (FormData交由axios自动设置multipart)
    if (!config.headers['Content-Type'] && !(config.data instanceof FormData)) {
      config.headers['Content-Type'] = 'application/json'
    }
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/*
 * 响应拦截器，无论失败或者成功都会返回{ success: boolean, data: xxx }这种类型的数据，没有reject和抛error。
 * 如果有问题，拦截器里会进行提示。在then里面总是会接收到返回值
 * */
axiosInstance.interceptors.response.use(
  (response: AxiosResponse) => {
    // console.log('response',response)
    /*
     * 响应成功的拦截器，主要是对data作处理，如果没有返回data，那么会添加一个data字段，并把response.data的内容合并到data里面，然后返回
     * */
    const { data } = response
    // console.log(response)
    if (data === undefined || data === null || data === '') {
      _notice('请求失败，请稍后重试！')
      return { success: false, code: 500, data: [] }
    } else if (typeof data === 'string') {
      return { success: true, code: 200, data }
    } else {
      if (data.data === undefined || data.data === null) {
        data.data = { ...data }
      }
      let resCode = data.code
      if (resCode) {
        try {
          resCode = Number(resCode)
        } catch (e) {
          data.code = resCode = 500
          data.success = false
        }
        if (resCode === 0) {
          data.code = resCode = 200
          data.success = true
        }
        if (resCode !== 200) {
          data.success = false
          _notice(response.data.msg || response.data.message || '请求失败，请稍后重试！')
        } else {
          data.success = true
        }
      } else {
        data.code = 200
        data.success = true
      }
      return data
    }
  },
  (error: AxiosError) => {
    const status = error.response?.status || 0
    const payload: any = error.response?.data
    const defaultMessage = status >= 500
      ? '服务器出现错误'
      : status === 404
        ? '接口不存在'
        : status === 401
          ? '登录状态已失效'
          : status === 403
            ? '没有操作权限'
            : status === 429
              ? '操作过于频繁，请稍后重试'
              : status === 0
                ? '服务器响应超时'
                : '请求失败，请稍后重试！'
    const message = payload?.msg || payload?.message || defaultMessage

    if (status !== 401) _notice(message)
    return {
      success: false,
      code: Number(payload?.code) || status || 500,
      msg: message,
      message,
      data: payload?.data ?? []
    }
  }
)

export interface ApiResponse<T = any> {
  data: T
  success: boolean
  code?: number
  msg?: string
  message?: string
  count?: number
}

export async function request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  /*
   *  then和catch里面返回的数据必须加as const，否则调用方无法推断出类型
   *  拦截器返回 { code, msg, data: <实际数据>, success }，这里把内层 data 解包出来
   * */
  return axiosInstance
    .request<T>(config)
    .then((res: any) => {
      const success = res.success === true
      const innerData = res.data !== undefined ? res.data : res
      return {
        success,
        data: innerData,
        code: res.code,
        msg: res.msg,
        message: res.message,
        count: res.count
      } as ApiResponse<T>
    })
    .catch((err) => {
      return {
        success: false,
        code: 500,
        msg: err?.message || '请求失败，请稍后重试！',
        message: err?.message || '请求失败，请稍后重试！',
        data: err
      } as ApiResponse<T>
    })
}
