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

/**
 * 电池参数配置类型定义
 * 用于定义储能电池的各项物理参数和运行约束
 */
export interface BatteryConfig {
  id: number
  deviceSn: string
  batteryName: string
  ratedCapacity: number
  ratedPower: number
  chargeEfficiency: number
  dischargeEfficiency: number
  roundTripEfficiency?: number
  minSoc: number
  maxSoc: number
  optimalSocMin?: number
  optimalSocMax?: number
  nominalVoltage?: number
  maxChargeCurrent?: number
  maxDischargeCurrent?: number
  maxChargePower?: number
  maxDischargePower?: number
  minTemperature?: number
  maxTemperature?: number
  optimalTempMin?: number
  optimalTempMax?: number
  initialSoh: number
  currentSoh?: number
  cycleCount?: number
  batteryType?: string
  manufacturer?: string
  installationDate?: string
  warrantyPeriodMonths?: number
  enabled: boolean
  description?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 电池衰减数据点类型定义
 * 用于记录电池在特定循环次数下的健康状态数据
 */
export interface BatteryDegradationPoint {
  id?: number
  cycleCount: number
  soh: number
  capacityRetention?: number
  internalResistanceRatio?: number
  temperature?: number
  depthOfDischarge?: number
  chargeRate?: number
  dischargeRate?: number
  remarks?: string
}

/**
 * 电池衰减模型类型定义
 * 用于定义电池健康状态随循环次数衰减的数学模型
 */
export interface BatteryDegradationModel {
  id: number
  modelName: string
  modelType: 'LINEAR' | 'EXPONENTIAL' | 'PIECEWISE' | 'EMPIRICAL'
  batteryType?: string
  degradationRatePerCycle?: number
  decayConstant?: number
  endOfLifeSoh: number
  warrantyCycleCount?: number
  warrantySoh?: number
  calendarAgingRatePerYear?: number
  temperatureFactor?: number
  socFactor?: number
  chargeRateFactor?: number
  dischargeRateFactor?: number
  depthOfDischargeFactor?: number
  maxCycleCount?: number
  estimatedLifespanYears?: number
  defaultModel: boolean
  enabled: boolean
  description?: string
  degradationPoints?: BatteryDegradationPoint[]
  createdAt?: string
  updatedAt?: string
}

/**
 * 变压器需量管理配置类型定义
 * 用于定义变压器的参数配置和需量控制策略
 */
export interface TransformerDemandConfig {
  id: number
  transformerCode: string
  transformerName: string
  ratedCapacity: number
  ratedVoltage?: number
  ratedCurrent?: number
  demandThreshold: number
  demandWarningThreshold?: number
  demandLimit?: number
  assessmentCycleMinutes: number
  demandBillingMethod?: 'DEMAND' | 'CAPACITY'
  demandPrice?: number
  capacityPrice?: number
  maxDemandCurrent?: number
  maxDemandPrevious?: number
  demandControlEnabled: boolean
  controlStrategy?: string
  dischargePriority?: number
  loadSheddingPriority?: number
  pvSelfUsePriority?: number
  minSocProtection?: number
  warningNotificationEnabled?: boolean
  notificationThresholdPercent?: number
  peakShavingEnabled?: boolean
  peakShavingThreshold?: number
  energyManagementEnabled?: boolean
  location?: string
  installationDate?: string
  manufacturer?: string
  enabled: boolean
  description?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 电池参数配置API
 * 后端路径：/api/battery/config
 */
export const batteryConfigApi = {
  list: () => request.get<any, BatteryConfig[]>('/battery/config/list'),
  listEnabled: () => request.get<any, BatteryConfig[]>('/battery/config/enabled'),
  get: (id: number) => request.get<any, BatteryConfig>(`/battery/config/${id}`),
  getByDeviceSn: (deviceSn: string) => request.get<any, BatteryConfig>(`/battery/config/device/${deviceSn}`),
  listByType: (batteryType: string) => request.get<any, BatteryConfig[]>(`/battery/config/type/${batteryType}`),
  create: (data: Partial<BatteryConfig>) => request.post<any, BatteryConfig>('/battery/config', data),
  update: (id: number, data: BatteryConfig) => request.put<any, BatteryConfig>(`/battery/config/${id}`, data),
  delete: (id: number) => request.delete<any, void>(`/battery/config/${id}`),
  updateEnabled: (id: number, enabled: boolean) => 
    request.patch<any, void>(`/battery/config/${id}/enabled`, { enabled }),
  checkSocSafe: (deviceSn: string, soc: number) => 
    request.get<any, boolean>('/battery/config/check/soc', { params: { deviceSn, soc } }),
  checkTemperatureSafe: (deviceSn: string, temperature: number) => 
    request.get<any, boolean>('/battery/config/check/temperature', { params: { deviceSn, temperature } }),
  calculateMaxChargePower: (deviceSn: string, currentSoc: number, currentTemperature?: number) => 
    request.get<any, number>('/battery/config/calculate/charge-power', { params: { deviceSn, currentSoc, currentTemperature } }),
  calculateMaxDischargePower: (deviceSn: string, currentSoc: number, currentTemperature?: number) => 
    request.get<any, number>('/battery/config/calculate/discharge-power', { params: { deviceSn, currentSoc, currentTemperature } }),
  calculateEffectiveCapacity: (deviceSn: string) => 
    request.get<any, number>('/battery/config/calculate/effective-capacity', { params: { deviceSn } }),
  calculateAvailableEnergy: (deviceSn: string, currentSoc: number) => 
    request.get<any, number>('/battery/config/calculate/available-energy', { params: { deviceSn, currentSoc } }),
  getTypeStatistics: (enabled: boolean = true) => 
    request.get<any, Record<string, number>>('/battery/config/statistics/type', { params: { enabled } }),
}

/**
 * 电池衰减模型API
 * 后端路径：/api/battery/degradation
 */
export const batteryDegradationApi = {
  list: () => request.get<any, BatteryDegradationModel[]>('/battery/degradation/list'),
  listEnabled: () => request.get<any, BatteryDegradationModel[]>('/battery/degradation/enabled'),
  get: (id: number) => request.get<any, BatteryDegradationModel>(`/battery/degradation/${id}`),
  listByModelType: (modelType: string) => 
    request.get<any, BatteryDegradationModel[]>(`/battery/degradation/type/${modelType}`),
  listByBatteryType: (batteryType: string) => 
    request.get<any, BatteryDegradationModel[]>(`/battery/degradation/battery-type/${batteryType}`),
  getDefault: () => request.get<any, BatteryDegradationModel>('/battery/degradation/default'),
  getDefaultByBatteryType: (batteryType: string) => 
    request.get<any, BatteryDegradationModel>(`/battery/degradation/default/${batteryType}`),
  create: (data: Partial<BatteryDegradationModel>) => 
    request.post<any, BatteryDegradationModel>('/battery/degradation', data),
  update: (id: number, data: BatteryDegradationModel) => 
    request.put<any, BatteryDegradationModel>(`/battery/degradation/${id}`, data),
  delete: (id: number) => request.delete<any, void>(`/battery/degradation/${id}`),
  updateEnabled: (id: number, enabled: boolean) => 
    request.patch<any, void>(`/battery/degradation/${id}/enabled`, { enabled }),
  setDefault: (id: number) => 
    request.post<any, void>(`/battery/degradation/${id}/default`),
  estimateSoh: (modelId: number, cycleCount: number) => 
    request.get<any, number>('/battery/degradation/estimate/soh', { params: { modelId, cycleCount } }),
  estimateSohWithFactors: (params: {
    modelId: number
    cycleCount: number
    avgTemperature?: number
    avgSoc?: number
    avgChargeRate?: number
    avgDischargeRate?: number
    avgDepthOfDischarge?: number
  }) => request.get<any, number>('/battery/degradation/estimate/soh-with-factors', { params }),
  estimateRemainingCycles: (modelId: number, currentSoh: number, currentCycleCount: number) => 
    request.get<any, number>('/battery/degradation/estimate/remaining-cycles', { 
      params: { modelId, currentSoh, currentCycleCount } 
    }),
  estimateRemainingLifespan: (params: {
    modelId: number
    currentSoh: number
    currentCycleCount: number
    dailyCycles?: number
  }) => request.get<any, number>('/battery/degradation/estimate/remaining-lifespan', { params }),
  generateCurve: (modelId: number, startCycle?: number, endCycle?: number, step?: number) => 
    request.get<any, BatteryDegradationPoint[]>('/battery/degradation/curve', { 
      params: { modelId, startCycle, endCycle, step } 
    }),
  generateStandardLFPCurve: () => 
    request.get<any, BatteryDegradationPoint[]>('/battery/degradation/standard-curve/lfp'),
  generateStandardNMCCurve: () => 
    request.get<any, BatteryDegradationPoint[]>('/battery/degradation/standard-curve/nmc'),
  calculateCalendarAging: (params: {
    modelId: number
    startDate: string
    endDate: string
    storageSoc?: number
    storageTemperature?: number
  }) => request.get<any, number>('/battery/degradation/calendar-aging', { params }),
  addPoint: (modelId: number, point: BatteryDegradationPoint) => 
    request.post<any, BatteryDegradationModel>(`/battery/degradation/${modelId}/points`, point),
  addPoints: (modelId: number, points: BatteryDegradationPoint[]) => 
    request.post<any, BatteryDegradationModel>(`/battery/degradation/${modelId}/points/batch`, points),
  removePoint: (modelId: number, pointId: number) => 
    request.delete<any, void>(`/battery/degradation/${modelId}/points/${pointId}`),
  validate: (data: Partial<BatteryDegradationModel>) => 
    request.post<any, Record<string, string>>('/battery/degradation/validate', data),
}

/**
 * 变压器需量管理API
 * 后端路径：/api/transformer/demand
 */
export const transformerDemandApi = {
  list: () => request.get<any, TransformerDemandConfig[]>('/transformer/demand/list'),
  listEnabled: () => request.get<any, TransformerDemandConfig[]>('/transformer/demand/enabled'),
  listControlEnabled: () => request.get<any, TransformerDemandConfig[]>('/transformer/demand/control-enabled'),
  get: (id: number) => request.get<any, TransformerDemandConfig>(`/transformer/demand/${id}`),
  getByCode: (transformerCode: string) => 
    request.get<any, TransformerDemandConfig>(`/transformer/demand/code/${transformerCode}`),
  create: (data: Partial<TransformerDemandConfig>) => 
    request.post<any, TransformerDemandConfig>('/transformer/demand', data),
  update: (id: number, data: TransformerDemandConfig) => 
    request.put<any, TransformerDemandConfig>(`/transformer/demand/${id}`, data),
  delete: (id: number) => request.delete<any, void>(`/transformer/demand/${id}`),
  updateEnabled: (id: number, enabled: boolean) => 
    request.patch<any, void>(`/transformer/demand/${id}/enabled`, { enabled }),
  updateControlEnabled: (id: number, demandControlEnabled: boolean) => 
    request.patch<any, void>(`/transformer/demand/${id}/control-enabled`, { demandControlEnabled }),
  predictDemand: (params: {
    transformerCode: string
    currentPower: number
    cycleElapsedMinutes: number
    historyPowerData?: number[]
  }) => request.post<any, number>('/transformer/demand/predict', null, { params }),
  calculateReduction: (transformerCode: string, predictedDemand: number) => 
    request.get<any, number>('/transformer/demand/reduction', { params: { transformerCode, predictedDemand } }),
  generateRecommendation: (params: {
    transformerCode: string
    currentLoad: number
    currentPvPower: number
    currentSoc: number
    predictedDemand: number
  }) => request.post<any, {
    dischargePower: number
    chargePower: number
    loadSheddingPower: number
    urgencyLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | 'NORMAL'
    thresholdRatio: number
    requiredReduction: number
    recommendedActions: string[]
  }>('/transformer/demand/recommendation', null, { params }),
  checkWarning: (transformerCode: string, predictedDemand: number) => 
    request.get<any, string | null>('/transformer/demand/warning', { params: { transformerCode, predictedDemand } }),
  updateCurrentMaxDemand: (transformerCode: string, maxDemand: number) => 
    request.post<any, void>('/transformer/demand/max-demand/current', null, { params: { transformerCode, maxDemand } }),
  resetCycle: (transformerCode: string) => 
    request.post<any, void>('/transformer/demand/cycle/reset', null, { params: { transformerCode } }),
  calculateCharge: (transformerCode: string, maxDemand: number, days?: number) => 
    request.get<any, number>('/transformer/demand/charge', { params: { transformerCode, maxDemand, days } }),
  calculateSaving: (params: {
    transformerCode: string
    originalMaxDemand: number
    optimizedMaxDemand: number
    days?: number
  }) => request.get<any, number>('/transformer/demand/saving', { params }),
  getBillingStatistics: () => 
    request.get<any, Record<string, number>>('/transformer/demand/statistics/billing'),
  getTotalCapacity: () => 
    request.get<any, number>('/transformer/demand/statistics/total-capacity'),
  validate: (data: Partial<TransformerDemandConfig>) => 
    request.post<any, Record<string, string>>('/transformer/demand/validate', data),
}

export default request
