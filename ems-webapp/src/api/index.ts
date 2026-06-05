import axios from 'axios'
import type { AxiosResponse } from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    return Promise.reject(error)
  }
)

export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface Device {
  id: number
  deviceSn: string
  deviceTypeId: number
  deviceTypeName: string
  name: string
  protocol: string
  host: string
  port: number
  slaveId: number
  location: string
  samplingInterval: number
  status: number
  remark: string
  createTime: string
  updateTime: string
}

export interface DeviceQuery {
  deviceSn?: string
  name?: string
  deviceTypeId?: number
  status?: number
  page?: number
  pageSize?: number
}

export interface TimeOfUsePrice {
  id: number
  periodName: string
  periodType: string
  startTime: string
  endTime: string
  price: number
  status: number
  createTime: string
  updateTime: string
}

export interface RealtimeDataVO {
  deviceSn: string
  deviceType: string
  data: Record<string, any>
  timestamp: number
}

export interface DataQuery {
  deviceSn: string
  deviceType: string
  startTime: string
  endTime: string
  field?: string
  limit?: number
}

/**
 * 设备管理API接口
 * 后端路径：/api/devices
 */
export const deviceApi = {
  list: (params: DeviceQuery) =>
    request.get<any, PageResult<Device>>('/devices/list', { params }),
  get: (id: number) => request.get<any, Device>(`/devices/${id}`),
  create: (data: Partial<Device>) => request.post<any, Device>('/devices', data),
  update: (data: Device) => request.put<any, Device>(`/devices/${data.id}`, data),
  delete: (id: number) => request.delete<any, void>(`/devices/${id}`),
  getBySn: (deviceSn: string) => request.get<any, Device>(`/devices/sn/${deviceSn}`)
}

/**
 * 电价配置API接口
 * 后端路径：/api/prices
 */
export const priceApi = {
  list: () => request.get<any, TimeOfUsePrice[]>('/prices'),
  get: (id: number) => request.get<any, TimeOfUsePrice>(`/prices/${id}`),
  create: (data: Partial<TimeOfUsePrice>) =>
    request.post<any, TimeOfUsePrice>('/prices', data),
  update: (data: TimeOfUsePrice) => request.put<any, TimeOfUsePrice>(`/prices/${data.id}`, data),
  delete: (id: number) => request.delete<any, void>(`/prices/${id}`),
  getCurrentPrice: () => request.get<any, TimeOfUsePrice>('/prices/current')
}

/**
 * 数据查询API接口
 * 后端路径：/api/data
 */
export const dataApi = {
  getRealtime: (deviceType: string, deviceSn: string) =>
    request.get<any, RealtimeDataVO>(`/data/realtime/${deviceType}/${deviceSn}`),
  getHistory: (params: DataQuery) =>
    request.post<any, any[]>(`/data/${params.deviceType}/query`, {
      deviceSn: params.deviceSn,
      startTime: params.startTime,
      endTime: params.endTime,
      field: params.field,
      limit: params.limit
    }),
  getLatest: (deviceType: string, deviceSn: string, limit: number = 100) =>
    request.get<any, any[]>(`/data/${deviceType}/${deviceSn}/latest`, {
      params: { limit }
    })
}

/**
 * 控制命令API接口
 * 后端路径：/api/commands
 */
export const commandApi = {
  sendCommand: (deviceSn: string, command: string, params: Record<string, any>) =>
    request.post<any, void>('/commands/custom', { deviceSn, commandType: command, params }),
  charge: (deviceSn: string, power: number) =>
    request.post<any, void>('/commands/charge', { deviceSn, power }),
  discharge: (deviceSn: string, power: number) =>
    request.post<any, void>('/commands/discharge', { deviceSn, power }),
  stop: (deviceSn: string) =>
    request.post<any, void>('/commands/stop', { deviceSn })
}

export default request
