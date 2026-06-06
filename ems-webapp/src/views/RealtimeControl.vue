<template>
  <div class="realtime-control-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">实时策略控制</h2>
        <div class="header-actions">
          <el-button type="primary" @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
            刷新数据
          </el-button>
          <el-button :type="isAutoRefresh ? 'danger' : 'success'" @click="toggleAutoRefresh">
            <el-icon><Promotion /></el-icon>
            {{ isAutoRefresh ? '停止自动刷新' : '开启自动刷新' }}
          </el-button>
        </div>
      </div>

      <el-row :gutter="20" class="control-row">
        <el-col :span="8">
          <el-card shadow="hover" class="parameter-card">
            <template #header>
              <div class="card-header">
                <span>控制参数</span>
              </div>
            </template>
            <el-form :model="controlParams" label-width="130px">
              <el-form-item label="策略">
                <el-select v-model="controlParams.strategyCode" placeholder="请选择策略" style="width: 100%;">
                  <el-option
                    v-for="item in strategyList"
                    :key="item.strategyCode"
                    :label="item.strategyName"
                    :value="item.strategyCode"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="电池编号">
                <el-input v-model="controlParams.batterySn" placeholder="请输入电池编号" clearable />
              </el-form-item>
              <el-form-item label="变压器编号">
                <el-input v-model="controlParams.transformerCode" placeholder="请输入变压器编号" clearable />
              </el-form-item>
              <el-form-item label="当前SOC(%)">
                <el-input-number v-model="controlParams.currentSoc" :min="0" :max="100" :step="1" style="width: 100%;" />
              </el-form-item>
              <el-form-item label="当前负荷(kW)">
                <el-input-number v-model="controlParams.currentLoad" :min="0" :step="1" style="width: 100%;" />
              </el-form-item>
              <el-form-item label="当前光伏(kW)">
                <el-input-number v-model="controlParams.currentPv" :min="0" :step="1" style="width: 100%;" />
              </el-form-item>
              <el-form-item label="当前需量(kW)">
                <el-input-number v-model="controlParams.currentDemand" :min="0" :step="1" style="width: 100%;" />
              </el-form-item>
              <el-form-item label="当前电价(元/kWh)">
                <el-input-number v-model="controlParams.currentPrice" :min="0" :step="0.01" style="width: 100%;" />
              </el-form-item>
              <el-form-item label="电池温度(℃)">
                <el-input-number v-model="controlParams.batteryTemperature" :min="-20" :max="60" :step="1" style="width: 100%;" />
              </el-form-item>
              <el-form-item label="控制类型">
                <el-select v-model="controlParams.executionType" placeholder="请选择控制类型" style="width: 100%;">
                  <el-option label="综合控制" value="AUTO" />
                  <el-option label="需量控制优先" value="DEMAND_CONTROL" />
                  <el-option label="峰谷套利" value="ARBITRAGE" />
                  <el-option label="削峰" value="PEAK_SHAVING" />
                  <el-option label="填谷" value="VALLEY_FILLING" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" style="width: 100%;" @click="handleExecute">
                  执行策略控制
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <el-col :span="16">
          <el-row :gutter="20">
            <el-col :span="6">
              <el-card shadow="hover" class="status-card">
                <div class="status-item" :class="{ 'status-danger': currentStatus?.urgencyLevel === 'CRITICAL' || currentStatus?.urgencyLevel === 'ALARM' }">
                  <div class="status-label">需量告警级别</div>
                  <div class="status-value">
                    <el-tag :type="getUrgencyType(currentStatus?.urgencyLevel)" size="large">
                      {{ getUrgencyName(currentStatus?.urgencyLevel) }}
                    </el-tag>
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="status-card">
                <div class="status-item">
                  <div class="status-label">当前电价</div>
                  <div class="status-value" style="color: #409eff;">
                    ¥{{ formatNumber(currentResult?.currentPrice) }}/kWh
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="status-card">
                <div class="status-item">
                  <div class="status-label">建议动作</div>
                  <div class="status-value">
                    <el-tag :type="getActionTypeTag(currentResult?.actionType)" size="large">
                      {{ getActionTypeName(currentResult?.actionType) }}
                    </el-tag>
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="status-card">
                <div class="status-item">
                  <div class="status-label">目标功率</div>
                  <div class="status-value" :class="getPowerClass(currentResult?.targetPower)">
                    {{ formatNumber(currentResult?.targetPower) }} kW
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="hover" class="result-card" v-if="currentResult">
            <template #header>
              <div class="card-header">
                <span>策略执行结果</span>
                <el-tag :type="currentResult.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ currentResult.status === 'SUCCESS' ? '执行成功' : '执行失败' }}
                </el-tag>
              </div>
            </template>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="策略名称">{{ currentResult.strategyName }}</el-descriptions-item>
              <el-descriptions-item label="策略编码">{{ currentResult.strategyCode }}</el-descriptions-item>
              <el-descriptions-item label="动作类型">
                <el-tag :type="getActionTypeTag(currentResult.actionType)" size="small">
                  {{ getActionTypeName(currentResult.actionType) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="目标功率">{{ formatNumber(currentResult.targetPower) }} kW</el-descriptions-item>
              <el-descriptions-item label="预计SOC">{{ formatNumber(currentResult.expectedSoc) }}%</el-descriptions-item>
              <el-descriptions-item label="当前电价">¥{{ formatNumber(currentResult.currentPrice) }}/kWh</el-descriptions-item>
              <el-descriptions-item label="预期收益">¥{{ formatNumber(currentResult.expectedRevenue) }}</el-descriptions-item>
              <el-descriptions-item label="寿命损耗成本">¥{{ formatNumber(currentResult.expectedDegradationCost) }}</el-descriptions-item>
              <el-descriptions-item label="需量节省">¥{{ formatNumber(currentResult.expectedDemandSaving) }}</el-descriptions-item>
              <el-descriptions-item label="综合得分" :span="3">
                <div class="score-display">
                  <div class="score-bar">
                    <div class="score-item">
                      <span class="score-label">套利</span>
                      <el-progress
                        :percentage="Math.round((currentResult.arbitrageScore || 0) * 100)"
                        :stroke-width="15"
                        :show-text="false"
                        color="#67c23a"
                      />
                      <span class="score-value">{{ (currentResult.arbitrageScore || 0).toFixed(4) }}</span>
                    </div>
                    <div class="score-item">
                      <span class="score-label">寿命</span>
                      <el-progress
                        :percentage="Math.round((currentResult.lifespanScore || 0) * 100)"
                        :stroke-width="15"
                        :show-text="false"
                        color="#e6a23c"
                      />
                      <span class="score-value">{{ (currentResult.lifespanScore || 0).toFixed(4) }}</span>
                    </div>
                    <div class="score-item">
                      <span class="score-label">需量</span>
                      <el-progress
                        :percentage="Math.round((currentResult.demandScore || 0) * 100)"
                        :stroke-width="15"
                        :show-text="false"
                        color="#f56c6c"
                      />
                      <span class="score-value">{{ (currentResult.demandScore || 0).toFixed(4) }}</span>
                    </div>
                  </div>
                  <div class="total-score">
                    <span class="total-label">综合得分</span>
                    <span class="total-value">{{ currentResult.totalObjectiveScore?.toFixed(4) }}</span>
                  </div>
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="紧急级别" v-if="currentResult.urgencyLevel">
                <el-tag :type="getUrgencyType(currentResult.urgencyLevel)" size="small">
                  {{ getUrgencyName(currentResult.urgencyLevel) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="推荐措施" v-if="currentResult.recommendedActions && currentResult.recommendedActions.length > 0" :span="2">
                <div v-for="(action, index) in currentResult.recommendedActions" :key="index" class="recommended-action">
                  {{ index + 1 }}. {{ action }}
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="消息" v-if="currentResult.message">
                {{ currentResult.message }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="hover" class="chart-card" v-if="historyLogs.length > 0">
            <template #header>
              <div class="card-header">
                <span>功率趋势图</span>
                <el-tag type="info" size="small">最近{{ historyLogs.length }}条记录</el-tag>
              </div>
            </template>
            <div ref="chartRef" class="chart-container"></div>
          </el-card>

          <el-card shadow="hover" class="log-card">
            <template #header>
              <div class="card-header">
                <span>执行日志</span>
                <el-button size="small" @click="loadLogs">刷新日志</el-button>
              </div>
            </template>
            <el-table :data="historyLogs" border stripe style="width: 100%" height="300">
              <el-table-column prop="executionTime" label="执行时间" width="160" />
              <el-table-column prop="strategyCode" label="策略编码" width="140" />
              <el-table-column prop="actionType" label="动作类型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getActionTypeTag(row.actionType)" size="small">
                    {{ getActionTypeName(row.actionType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="targetPower" label="目标功率(kW)" width="110" align="right">
                <template #default="{ row }">{{ formatNumber(row.targetPower) }}</template>
              </el-table-column>
              <el-table-column prop="currentSoc" label="SOC(%)" width="90" align="center">
                <template #default="{ row }">{{ formatNumber(row.currentSoc) }}</template>
              </el-table-column>
              <el-table-column prop="currentDemand" label="需量(kW)" width="100" align="right">
                <template #default="{ row }">{{ formatNumber(row.currentDemand) }}</template>
              </el-table-column>
              <el-table-column prop="urgencyLevel" label="紧急级别" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getUrgencyType(row.urgencyLevel)" size="small">
                    {{ getUrgencyName(row.urgencyLevel) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalObjectiveScore" label="综合得分" width="100" align="right">
                <template #default="{ row }">{{ row.totalObjectiveScore?.toFixed(4) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                    {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Promotion } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  strategyConfigApi,
  realTimeStrategyApi,
  type StrategyConfig,
  type StrategyResultVO,
  type RealTimeControlRequest
} from '@/api'

const strategyList = ref<StrategyConfig[]>([])
const currentResult = ref<StrategyResultVO | null>(null)
const currentStatus = ref<{ urgencyLevel?: string } | null>(null)
const historyLogs = ref<any[]>([])
const isAutoRefresh = ref(false)
const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null
let autoRefreshTimer: number | null = null

const controlParams = ref<RealTimeControlRequest>({
  strategyCode: '',
  batterySn: '',
  transformerCode: '',
  currentSoc: 50,
  currentLoad: 800,
  currentPv: 200,
  currentDemand: 600,
  currentPrice: 0.8,
  batteryTemperature: 25,
  executionType: 'AUTO'
})

const formatNumber = (num: number | undefined) => {
  if (num === undefined || num === null) return '0.00'
  return num.toFixed(2)
}

const getUrgencyType = (level: string | undefined) => {
  const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    NORMAL: 'success',
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    CRITICAL: 'danger',
    ALARM: 'danger'
  }
  return typeMap[level || ''] || 'info'
}

const getUrgencyName = (level: string | undefined) => {
  const nameMap: Record<string, string> = {
    NORMAL: '正常',
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高',
    CRITICAL: '紧急',
    ALARM: '告警'
  }
  return nameMap[level || ''] || '--'
}

const getActionTypeTag = (type: string | undefined) => {
  const tagMap: Record<string, 'success' | 'danger' | 'warning' | 'info' | 'primary'> = {
    CHARGE: 'success',
    DISCHARGE: 'danger',
    PEAK_SHAVING: 'danger',
    VALLEY_FILLING: 'success',
    DEMAND_CONTROL: 'danger',
    ARBITRAGE_CHARGE: 'success',
    ARBITRAGE_DISCHARGE: 'danger',
    IDLE: 'info',
    AUTO: 'primary'
  }
  return tagMap[type || ''] || 'info'
}

const getActionTypeName = (type: string | undefined) => {
  const nameMap: Record<string, string> = {
    CHARGE: '充电',
    DISCHARGE: '放电',
    PEAK_SHAVING: '削峰',
    VALLEY_FILLING: '填谷',
    DEMAND_CONTROL: '需量控制',
    ARBITRAGE_CHARGE: '套利充电',
    ARBITRAGE_DISCHARGE: '套利放电',
    IDLE: '待机',
    AUTO: '综合控制'
  }
  return nameMap[type || ''] || type || '--'
}

const getPowerClass = (power: number | undefined) => {
  if (power === undefined) return ''
  return power > 0 ? 'text-danger' : power < 0 ? 'text-success' : 'text-info'
}

const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chartInstance || historyLogs.value.length === 0) return

  const times = historyLogs.value.map((_, index) => `T${historyLogs.value.length - index}`)
  const powerData = historyLogs.value.map(h => h.targetPower || 0)
  const socData = historyLogs.value.map(h => h.currentSoc || 0)
  const demandData = historyLogs.value.map(h => h.currentDemand || 0)

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['目标功率', 'SOC', '当前需量'],
      top: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times
    },
    yAxis: [
      {
        type: 'value',
        name: '功率(kW)'
      },
      {
        type: 'value',
        name: 'SOC/需量',
        max: 100
      }
    ],
    series: [
      {
        name: '目标功率',
        type: 'bar',
        data: powerData,
        itemStyle: {
          color: (params: any) => params.value >= 0 ? '#f56c6c' : '#67c23a'
        }
      },
      {
        name: 'SOC',
        type: 'line',
        yAxisIndex: 1,
        data: socData,
        smooth: true,
        lineStyle: { color: '#e6a23c' },
        itemStyle: { color: '#e6a23c' }
      },
      {
        name: '当前需量',
        type: 'line',
        yAxisIndex: 1,
        data: demandData,
        smooth: true,
        lineStyle: { color: '#409eff' },
        itemStyle: { color: '#409eff' }
      }
    ]
  }

  chartInstance.setOption(option)
}

const loadStrategies = async () => {
  try {
    strategyList.value = await strategyConfigApi.listEnabled()
    if (strategyList.value.length > 0 && !controlParams.value.strategyCode) {
      controlParams.value.strategyCode = strategyList.value[0].strategyCode
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载策略列表失败')
  }
}

const loadStatus = async () => {
  if (!controlParams.value.strategyCode) return
  try {
    currentStatus.value = await realTimeStrategyApi.getStatus(
      controlParams.value.strategyCode,
      controlParams.value.batterySn,
      controlParams.value.transformerCode
    )
  } catch (error: any) {
    console.error('加载状态失败', error)
  }
}

const loadLogs = async () => {
  if (!controlParams.value.strategyCode) return
  try {
    historyLogs.value = await realTimeStrategyApi.listRecentLogs(controlParams.value.strategyCode, 1)
    historyLogs.value = historyLogs.value.reverse()
    nextTick(() => {
      updateChart()
    })
  } catch (error: any) {
    console.error('加载日志失败', error)
  }
}

const handleExecute = async () => {
  if (!controlParams.value.strategyCode) {
    ElMessage.warning('请选择策略')
    return
  }
  try {
    let result
    switch (controlParams.value.executionType) {
      case 'DEMAND_CONTROL':
        result = await realTimeStrategyApi.executeDemandControl(controlParams.value)
        break
      case 'ARBITRAGE':
        result = await realTimeStrategyApi.executeArbitrage(controlParams.value)
        break
      case 'PEAK_SHAVING':
        result = await realTimeStrategyApi.executePeakShaving(controlParams.value)
        break
      case 'VALLEY_FILLING':
        result = await realTimeStrategyApi.executeValleyFilling(controlParams.value)
        break
      default:
        result = await realTimeStrategyApi.executeControl(controlParams.value)
    }
    currentResult.value = result
    ElMessage.success(`策略执行成功：${getActionTypeName(result.actionType)}`)
    loadLogs()
    loadStatus()
  } catch (error: any) {
    ElMessage.error(error.message || '策略执行失败')
  }
}

const handleRefresh = () => {
  loadStatus()
  loadLogs()
}

const toggleAutoRefresh = () => {
  isAutoRefresh.value = !isAutoRefresh.value
  if (isAutoRefresh.value) {
    autoRefreshTimer = window.setInterval(() => {
      handleRefresh()
    }, 5000)
    ElMessage.success('已开启自动刷新，每5秒刷新一次')
  } else {
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer)
      autoRefreshTimer = null
    }
    ElMessage.info('已停止自动刷新')
  }
}

onMounted(() => {
  loadStrategies()
  nextTick(() => {
    initChart()
  })
  window.addEventListener('resize', () => {
    chartInstance?.resize()
  })
})

onUnmounted(() => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
  }
  chartInstance?.dispose()
})
</script>

<style scoped lang="less">
.realtime-control-page {
  padding: 20px;

  .page-container {
    max-width: 1600px;
    margin: 0 auto;
  }

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .page-title {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
      color: #303133;
    }

    .header-actions {
      display: flex;
      gap: 10px;
    }
  }

  .control-row {
    .parameter-card {
      :deep(.el-form-item__label) {
        font-size: 13px;
      }
    }
  }

  .status-card {
    margin-bottom: 20px;

    .status-item {
      text-align: center;

      .status-label {
        font-size: 14px;
        color: #909399;
        margin-bottom: 8px;
      }

      .status-value {
        font-size: 22px;
        font-weight: 600;
        color: #303133;
      }

      &.status-danger .status-value {
        animation: blink 1s infinite;
      }
    }
  }

  @keyframes blink {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
  }

  .result-card {
    margin-bottom: 20px;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .score-display {
      .score-bar {
        display: flex;
        gap: 20px;
        margin-bottom: 15px;

        .score-item {
          flex: 1;
          display: flex;
          align-items: center;
          gap: 10px;

          .score-label {
            width: 40px;
            font-size: 13px;
            color: #606266;
          }

          .score-value {
            width: 70px;
            font-size: 13px;
            text-align: right;
            font-family: monospace;
          }

          :deep(.el-progress) {
            flex: 1;
          }
        }
      }

      .total-score {
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 15px;
        padding: 10px;
        background: #ecf5ff;
        border-radius: 8px;

        .total-label {
          font-size: 16px;
          color: #606266;
        }

        .total-value {
          font-size: 28px;
          font-weight: 600;
          color: #409eff;
          font-family: monospace;
        }
      }
    }

    .recommended-action {
      margin-bottom: 5px;
      color: #606266;
      font-size: 13px;
    }
  }

  .chart-card {
    margin-bottom: 20px;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .chart-container {
      height: 300px;
      width: 100%;
    }
  }

  .log-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }

  .text-success {
    color: #67c23a;
  }

  .text-danger {
    color: #f56c6c;
  }

  .text-info {
    color: #909399;
  }
}
</style>
