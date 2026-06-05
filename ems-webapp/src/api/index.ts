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

export const deviceApi = {
  list: (params: DeviceQuery) =>
    request.get<any, PageResult<Device>>('/device/list', { params }),
  get: (id: number) => request.get<any, Device>(`/device/${id}`),
  create: (data: Partial<Device>) => request.post<any, Device>('/device', data),
  update: (data: Device) => request.put<any, Device>('/device', data),
  delete: (id: number) => request.delete<any, void>(`/device/${id}`),
  getBySn: (deviceSn: string) => request.get<any, Device>(`/device/sn/${deviceSn}`)
}

export const priceApi = {
  list: () => request.get<any, TimeOfUsePrice[]>('/price/list'),
  get: (id: number) => request.get<any, TimeOfUsePrice>(`/price/${id}`),
  create: (data: Partial<TimeOfUsePrice>) =>
    request.post<any, TimeOfUsePrice>('/price', data),
  update: (data: TimeOfUsePrice) => request.put<any, TimeOfUsePrice>('/price', data),
  delete: (id: number) => request.delete<any, void>(`/price/${id}`),
  getCurrentPrice: () => request.get<any, TimeOfUsePrice>('/price/current')
}

export const dataApi = {
  getRealtime: (deviceType: string, deviceSn: string) =>
    request.get<any, RealtimeDataVO>(`/data/realtime/${deviceType}/${deviceSn}`),
  getHistory: (params: DataQuery) =>
    request.get<any, any[]>('/data/history', { params }),
  getLatest: (deviceType: string, deviceSn: string, limit: number = 100) =>
    request.get<any, any[]>(`/data/latest/${deviceType}/${deviceSn}`, {
      params: { limit }
    })
}

export const commandApi = {
  sendCommand: (deviceSn: string, command: string, params: Record<string, any>) =>
    request.post<any, void>('/command/send', { deviceSn, command, params }),
  charge: (deviceSn: string, power: number) =>
    request.post<any, void>('/command/charge', { deviceSn, power }),
  discharge: (deviceSn: string, power: number) =>
    request.post<any, void>('/command/discharge', { deviceSn, power }),
  stop: (deviceSn: string) =>
    request.post<any, void>('/command/stop', { deviceSn })
}

export default request
