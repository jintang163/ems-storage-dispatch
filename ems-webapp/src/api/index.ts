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
 * 策略配置类型定义
 * 用于定义日内与实时策略的配置参数
 */
export interface StrategyConfig {
  id: number
  strategyName: string
  strategyType: string
  strategyCode: string
  arbitrageWeight: number
  lifespanWeight: number
  demandWeight: number
  maxChargeRate: number
  maxDischargeRate: number
  minSoc: number
  maxSoc: number
  maxDailyCycles: number
  maxDepthOfDischarge: number
  demandThresholdRatio: number
  priceForecastEnabled: boolean
  peakValleyArbitrageEnabled: boolean
  peakShavingEnabled: boolean
  valleyFillingEnabled: boolean
  demandControlEnabled: boolean
  batterySn?: string
  transformerCode?: string
  scheduleIntervalMinutes: number
  rollingOptimizationEnabled: boolean
  rollingIntervalMinutes?: number
  lookAheadHours?: number
  priority: number
  enabled: boolean
  defaultStrategy: boolean
  description?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 调度计划时段类型定义
 */
export interface DispatchPlanHour {
  id?: number
  planId?: number
  hourIndex: number
  startTime: string
  endTime: string
  periodType?: string
  price?: number
  power: number
  energy?: number
  expectedSoc?: number
  chargeRate?: number
  depthOfDischarge?: number
  actionType?: string
  forecastLoad?: number
  forecastPv?: number
  expectedDemand?: number
  demandControlRequired?: boolean
  revenue?: number
  degradationCost?: number
  demandSaving?: number
  objectiveScore?: number
  remark?: string
}

/**
 * 调度计划类型定义
 */
export interface DispatchPlan {
  id: number
  strategyId: number
  strategyCode: string
  planDate: string
  planType: string
  batterySn?: string
  transformerCode?: string
  initialSoc?: number
  expectedRevenue?: number
  expectedDegradation?: number
  expectedDemandSaving?: number
  totalObjectiveScore?: number
  arbitrageScore?: number
  lifespanScore?: number
  demandScore?: number
  generatedAt?: string
  executedAt?: string
  status: string
  createdBy?: string
  remark?: string
  planHours?: DispatchPlanHour[]
  createdAt?: string
  updatedAt?: string
}

/**
 * 策略执行结果类型定义
 */
export interface StrategyResultVO {
  strategyCode: string
  strategyName: string
  actionType: string
  targetPower?: number
  expectedSoc?: number
  expectedRevenue?: number
  expectedDegradationCost?: number
  expectedDemandSaving?: number
  totalObjectiveScore?: number
  arbitrageScore?: number
  lifespanScore?: number
  demandScore?: number
  urgencyLevel?: string
  recommendedActions?: string[]
  additionalInfo?: Record<string, any>
  status: string
  message?: string
  currentPrice?: number
}

/**
 * 实时控制请求类型定义
 */
export interface RealTimeControlRequest {
  strategyCode: string
  batterySn?: string
  transformerCode?: string
  currentSoc?: number
  currentLoad?: number
  currentPv?: number
  currentDemand?: number
  currentPrice?: number
  batteryTemperature?: number
  batteryHealth?: number
  executionType?: string
}

/**
 * 电价预测类型定义
 */
export interface PriceForecast {
  id?: number
  forecastDate: string
  hourIndex: number
  startTime: string
  endTime: string
  forecastPrice: number
  actualPrice?: number
  priceDeviation?: number
  deviationPercentage?: number
  periodType?: string
  forecastSource?: string
  forecastModel?: string
  confidenceLevel?: number
  isPeak: boolean
  isValley: boolean
  remark?: string
}

/**
 * 负荷预测类型定义
 */
export interface LoadForecast {
  id?: number
  forecastDate: string
  hourIndex: number
  startTime: string
  endTime: string
  forecastLoad: number
  forecastPv?: number
  forecastGrid?: number
  actualLoad?: number
  actualPv?: number
  loadDeviation?: number
  deviationPercentage?: number
  forecastType?: string
  forecastSource?: string
  forecastModel?: string
  confidenceLevel?: number
  isPeakHour: boolean
  transformerCode?: string
  remark?: string
}

/**
 * 策略配置API
 * 后端路径：/api/strategy/config
 */
export const strategyConfigApi = {
  list: () => request.get<any, StrategyConfig[]>('/strategy/config/list'),
  listEnabled: () => request.get<any, StrategyConfig[]>('/strategy/config/enabled'),
  get: (id: number) => request.get<any, StrategyConfig>(`/strategy/config/${id}`),
  getByCode: (strategyCode: string) => request.get<any, StrategyConfig>(`/strategy/config/code/${strategyCode}`),
  listByType: (strategyType: string) => request.get<any, StrategyConfig[]>(`/strategy/config/type/${strategyType}`),
  getDefault: () => request.get<any, StrategyConfig>('/strategy/config/default'),
  listByBatterySn: (batterySn: string) => request.get<any, StrategyConfig[]>(`/strategy/config/battery/${batterySn}`),
  listByTransformerCode: (transformerCode: string) => request.get<any, StrategyConfig[]>(`/strategy/config/transformer/${transformerCode}`),
  create: (data: Partial<StrategyConfig>) => request.post<any, StrategyConfig>('/strategy/config', data),
  update: (id: number, data: StrategyConfig) => request.put<any, StrategyConfig>(`/strategy/config/${id}`, data),
  delete: (id: number) => request.delete<any, void>(`/strategy/config/${id}`),
  updateEnabled: (id: number, enabled: boolean) =>
    request.patch<any, void>(`/strategy/config/${id}/enabled`, { enabled }),
  setDefault: (id: number) => request.patch<any, void>(`/strategy/config/${id}/default`),
  validate: (data: Partial<StrategyConfig>) =>
    request.post<any, Record<string, string>>('/strategy/config/validate', data),
  normalizeWeights: (data: Partial<StrategyConfig>) =>
    request.post<any, number>('/strategy/config/normalize-weights', data),
  getTypeStatistics: () => request.get<any, Record<string, number>>('/strategy/config/statistics/type'),
  getEnabledCount: () => request.get<any, number>('/strategy/config/statistics/enabled-count'),
}

/**
 * 调度计划API
 * 后端路径：/api/strategy/plan
 */
export const dispatchPlanApi = {
  generate: (data: {
    strategyId?: number
    strategyCode: string
    planDate: string
    planType?: string
    initialSoc?: number
    batterySn?: string
    transformerCode?: string
    usePriceForecast?: boolean
    useLoadForecast?: boolean
    createdBy?: string
    remark?: string
  }) => request.post<any, DispatchPlan>('/strategy/plan/generate', data),
  regenerate: (id: number) => request.post<any, DispatchPlan>(`/strategy/plan/regenerate/${id}`),
  get: (id: number) => request.get<any, DispatchPlan>(`/strategy/plan/${id}`),
  getLatestPending: (strategyCode: string, date?: string) =>
    request.get<any, DispatchPlan>(`/strategy/plan/latest/${strategyCode}`, { params: { date } }),
  listByStrategyId: (strategyId: number) =>
    request.get<any, DispatchPlan[]>(`/strategy/plan/strategy/${strategyId}`),
  listByDate: (date: string) => request.get<any, DispatchPlan[]>(`/strategy/plan/date/${date}`),
  listByDateRange: (startDate: string, endDate: string) =>
    request.get<any, DispatchPlan[]>('/strategy/plan/date-range', { params: { startDate, endDate } }),
  listByStatus: (status: string) => request.get<any, DispatchPlan[]>(`/strategy/plan/status/${status}`),
  listPending: () => request.get<any, DispatchPlan[]>('/strategy/plan/pending'),
  execute: (id: number) => request.post<any, void>(`/strategy/plan/execute/${id}`),
  cancel: (id: number) => request.post<any, void>(`/strategy/plan/cancel/${id}`),
  approve: (id: number, approvedBy: string) =>
    request.post<any, void>(`/strategy/plan/approve/${id}`, { approvedBy }),
  executeCurrentHour: (strategyCode: string, batterySn?: string) =>
    request.post<any, StrategyResultVO>(`/strategy/plan/execute-current/${strategyCode}`, null, { params: { batterySn } }),
  generateRolling: (strategyCode: string, date: string, startHour: number) =>
    request.post<any, DispatchPlan>(`/strategy/plan/rolling/${strategyCode}`, null, { params: { date, startHour } }),
  getBenefits: (id: number) => request.get<any, Record<string, number>>(`/strategy/plan/benefits/${id}`),
  getStatisticsByDate: (date: string, strategyCode: string) =>
    request.get<any, any>(`/strategy/plan/statistics/date/${date}`, { params: { strategyCode } }),
  getStatisticsByDateRange: (startDate: string, endDate: string, strategyCode: string) =>
    request.get<any, any[]>('/strategy/plan/statistics/date-range', { params: { startDate, endDate, strategyCode } }),
  getStatusSummary: () => request.get<any, Record<string, any>>('/strategy/plan/status-summary'),
  getTotalBenefits: (startDate: string, endDate: string) =>
    request.get<any, Record<string, number>>('/strategy/plan/total-benefits', { params: { startDate, endDate } }),
}

/**
 * 实时策略API
 * 后端路径：/api/strategy/realtime
 */
export const realTimeStrategyApi = {
  executeControl: (data: RealTimeControlRequest) =>
    request.post<any, StrategyResultVO>('/strategy/realtime/control', data),
  executeDemandControl: (data: RealTimeControlRequest) =>
    request.post<any, StrategyResultVO>('/strategy/realtime/demand-control', data),
  executeArbitrage: (data: RealTimeControlRequest) =>
    request.post<any, StrategyResultVO>('/strategy/realtime/arbitrage', data),
  executePeakShaving: (data: RealTimeControlRequest) =>
    request.post<any, StrategyResultVO>('/strategy/realtime/peak-shaving', data),
  executeValleyFilling: (data: RealTimeControlRequest) =>
    request.post<any, StrategyResultVO>('/strategy/realtime/valley-filling', data),
  executeMultiObjective: (data: RealTimeControlRequest) =>
    request.post<any, StrategyResultVO>('/strategy/realtime/multi-objective', data),
  checkDemandWarning: (strategyCode: string, currentDemand: number, predictedDemand?: number) =>
    request.get<any, string>('/strategy/realtime/demand-warning/${strategyCode}', { params: { currentDemand, predictedDemand } }),
  calculateDischargePower: (strategyCode: string, currentDemand: number, predictedDemand?: number) =>
    request.get<any, number>('/strategy/realtime/discharge-power/${strategyCode}', { params: { currentDemand, predictedDemand } }),
  calculateChargePower: (strategyCode: string, currentDemand: number, currentSoc: number) =>
    request.get<any, number>('/strategy/realtime/charge-power/${strategyCode}', { params: { currentDemand, currentSoc } }),
  listLogs: (strategyCode: string, startTime: string, endTime: string) =>
    request.get<any, any[]>('/strategy/realtime/logs/${strategyCode}', { params: { startTime, endTime } }),
  listRecentLogs: (strategyCode: string, hours?: number) =>
    request.get<any, any[]>('/strategy/realtime/logs/recent/${strategyCode}', { params: { hours } }),
  getStatistics: (strategyCode: string, startDate: string, endDate: string) =>
    request.get<any, any>('/strategy/realtime/statistics/${strategyCode}', { params: { startDate, endDate } }),
  getStatus: (strategyCode: string, batterySn?: string, transformerCode?: string) =>
    request.get<any, Record<string, any>>('/strategy/realtime/status/${strategyCode}', { params: { batterySn, transformerCode } }),
  getTotalBenefits: (strategyCode: string, startDate: string, endDate: string) =>
    request.get<any, Record<string, number>>('/strategy/realtime/benefits/${strategyCode}', { params: { startDate, endDate } }),
  getActionStatistics: (strategyCode: string, startDate: string, endDate: string) =>
    request.get<any, Record<string, any>>('/strategy/realtime/action-statistics/${strategyCode}', { params: { startDate, endDate } }),
}

/**
 * 预测数据API
 * 后端路径：/api/strategy/forecast
 */
export const forecastApi = {
  generatePriceForecast: (date: string, source?: string) =>
    request.post<any, PriceForecast[]>('/strategy/forecast/price/generate', null, { params: { date, source } }),
  generatePriceForecastByTou: (date: string) =>
    request.post<any, PriceForecast[]>('/strategy/forecast/price/generate-tou', null, { params: { date } }),
  generateLoadForecast: (date: string, transformerCode: string) =>
    request.post<any, LoadForecast[]>('/strategy/forecast/load/generate', null, { params: { date, transformerCode } }),
  getPriceForecast: (date: string) => request.get<any, PriceForecast[]>(`/strategy/forecast/price/${date}`),
  getLoadForecast: (date: string, transformerCode: string) =>
    request.get<any, LoadForecast[]>(`/strategy/forecast/load/${date}`, { params: { transformerCode } }),
  getPriceAtHour: (date: string, hour: number) =>
    request.get<any, PriceForecast>(`/strategy/forecast/price/${date}/hour/${hour}`),
  getLoadAtHour: (date: string, hour: number, transformerCode: string) =>
    request.get<any, LoadForecast>(`/strategy/forecast/load/${date}/hour/${hour}`, { params: { transformerCode } }),
  getPeakHours: (date: string) => request.get<any, PriceForecast[]>(`/strategy/forecast/price/${date}/peak-hours`),
  getValleyHours: (date: string) => request.get<any, PriceForecast[]>(`/strategy/forecast/price/${date}/valley-hours`),
  getMaxPrice: (date: string) => request.get<any, number>(`/strategy/forecast/price/${date}/max`),
  getMinPrice: (date: string) => request.get<any, number>(`/strategy/forecast/price/${date}/min`),
  getAvgPrice: (date: string) => request.get<any, number>(`/strategy/forecast/price/${date}/avg`),
  getMaxLoad: (date: string, transformerCode: string) =>
    request.get<any, number>(`/strategy/forecast/load/${date}/max`, { params: { transformerCode } }),
  getMinLoad: (date: string, transformerCode: string) =>
    request.get<any, number>(`/strategy/forecast/load/${date}/min`, { params: { transformerCode } }),
  getAvgLoad: (date: string, transformerCode: string) =>
    request.get<any, number>(`/strategy/forecast/load/${date}/avg`, { params: { transformerCode } }),
  calculatePriceSpread: (date: string) => request.get<any, number>(`/strategy/forecast/price/${date}/spread`),
  identifyArbitrageOpportunities: (date: string) =>
    request.get<any, any[]>(`/strategy/forecast/arbitrage-opportunities/${date}`),
  identifyPeakShavingOpportunities: (date: string, transformerCode: string) =>
    request.get<any, any[]>(`/strategy/forecast/peak-shaving-opportunities/${date}`, { params: { transformerCode } }),
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
