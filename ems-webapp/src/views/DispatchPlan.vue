<template>
  <div class="dispatch-plan-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">调度计划管理</h2>
        <div class="header-actions">
          <el-select v-model="selectedStrategy" placeholder="请选择策略" style="width: 200px; margin-right: 10px;">
            <el-option
              v-for="item in strategyList"
              :key="item.strategyCode"
              :label="item.strategyName"
              :value="item.strategyCode"
            />
          </el-select>
          <el-date-picker
            v-model="planDate"
            type="date"
            placeholder="选择日期"
            style="width: 180px; margin-right: 10px;"
            value-format="YYYY-MM-DD"
          />
          <el-button type="primary" @click="handleGenerate">
            <el-icon><Refresh /></el-icon>
            生成计划
          </el-button>
        </div>
      </div>

      <el-row :gutter="20" class="stat-row" v-if="selectedPlan">
        <el-col :span="4">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">总预期收益</div>
              <div class="stat-value" style="color: #67c23a;">¥{{ formatNumber(selectedPlan.expectedRevenue) }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">预计寿命损耗</div>
              <div class="stat-value" style="color: #e6a23c;">¥{{ formatNumber(selectedPlan.expectedDegradation) }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">预计需量节省</div>
              <div class="stat-value" style="color: #409eff;">¥{{ formatNumber(selectedPlan.expectedDemandSaving) }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">净收益</div>
              <div class="stat-value" style="color: #67c23a;">¥{{ calculateNetBenefit() }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">综合目标得分</div>
              <div class="stat-value">{{ formatNumber(selectedPlan.totalObjectiveScore) }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">状态</div>
              <div class="stat-value">
                <el-tag :type="getStatusType(selectedPlan.status)" size="large">{{ getStatusName(selectedPlan.status) }}</el-tag>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="24小时充放电计划" name="chart">
          <el-card class="chart-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span>充放电功率曲线 (24小时)</span>
                <div class="card-actions">
                  <el-tag size="small" type="success" v-if="selectedPlan">
                    充电功率 (负) / 放电功率 (正)
                  </el-tag>
                </div>
              </div>
            </template>
            <div ref="chartRef" class="chart-container"></div>
          </el-card>

          <el-row :gutter="20" class="score-row" v-if="selectedPlan">
            <el-col :span="8">
              <el-card shadow="hover">
                <div class="score-item">
                  <div class="score-label">套利收益得分</div>
                  <el-progress
                    type="dashboard"
                    :percentage="Math.round((selectedPlan.arbitrageScore || 0) * 100)"
                    :width="100"
                    color="#67c23a"
                  />
                  <div class="score-value">{{ (selectedPlan.arbitrageScore || 0).toFixed(4) }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="hover">
                <div class="score-item">
                  <div class="score-label">寿命保护得分</div>
                  <el-progress
                    type="dashboard"
                    :percentage="Math.round((selectedPlan.lifespanScore || 0) * 100)"
                    :width="100"
                    color="#e6a23c"
                  />
                  <div class="score-value">{{ (selectedPlan.lifespanScore || 0).toFixed(4) }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="hover">
                <div class="score-item">
                  <div class="score-label">需量控制得分</div>
                  <el-progress
                    type="dashboard"
                    :percentage="Math.round((selectedPlan.demandScore || 0) * 100)"
                    :width="100"
                    color="#f56c6c"
                  />
                  <div class="score-value">{{ (selectedPlan.demandScore || 0).toFixed(4) }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="时段详情" name="table">
          <el-card shadow="hover">
            <template #header>
              <div class="card-header">
                <span>24小时时段详情</span>
                <div class="card-actions">
                  <el-button size="small" type="success" @click="handleExecuteCurrent" v-if="selectedPlan && selectedPlan.status === 'PENDING'">
                    执行当前时段
                  </el-button>
                  <el-button size="small" type="primary" @click="handleRegenerate" v-if="selectedPlan">
                    重新生成
                  </el-button>
                  <el-button size="small" type="warning" @click="handleApprove" v-if="selectedPlan && selectedPlan.status === 'PENDING'">
                    审核通过
                  </el-button>
                  <el-button size="small" type="danger" @click="handleCancel" v-if="selectedPlan && selectedPlan.status !== 'EXECUTED'">
                    取消计划
                  </el-button>
                </div>
              </div>
            </template>
            <el-table :data="selectedPlan?.planHours || []" border stripe style="width: 100%" height="500">
              <el-table-column prop="hourIndex" label="时段" width="70" align="center" />
              <el-table-column prop="startTime" label="开始时间" width="100" />
              <el-table-column prop="endTime" label="结束时间" width="100" />
              <el-table-column prop="periodType" label="时段类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="getPeriodTypeTag(row.periodType)" size="small">
                    {{ getPeriodTypeName(row.periodType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="price" label="电价(元/kWh)" width="110" align="right">
                <template #default="{ row }">{{ row.price?.toFixed(3) }}</template>
              </el-table-column>
              <el-table-column prop="power" label="功率(kW)" width="100" align="right">
                <template #default="{ row }">
                  <span :class="row.power >= 0 ? 'text-danger' : 'text-success'">
                    {{ row.power?.toFixed(2) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="energy" label="电量(kWh)" width="100" align="right">
                <template #default="{ row }">{{ row.energy?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="expectedSoc" label="预计SOC(%)" width="110" align="center">
                <template #default="{ row }">
                  <el-progress
                    :percentage="row.expectedSoc || 0"
                    :stroke-width="10"
                    :show-text="true"
                    :color="getSocColor(row.expectedSoc)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="chargeRate" label="充放倍率" width="90" align="center">
                <template #default="{ row }">{{ row.chargeRate?.toFixed(2) }}C</template>
              </el-table-column>
              <el-table-column prop="actionType" label="动作类型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getActionTypeTag(row.actionType)" size="small">
                    {{ getActionTypeName(row.actionType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="forecastLoad" label="预测负荷(kW)" width="120" align="right">
                <template #default="{ row }">{{ row.forecastLoad?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="forecastPv" label="预测光伏(kW)" width="120" align="right">
                <template #default="{ row }">{{ row.forecastPv?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="expectedDemand" label="预计需量(kW)" width="120" align="right">
                <template #default="{ row }">
                  <span :class="row.demandControlRequired ? 'text-danger' : ''">
                    {{ row.expectedDemand?.toFixed(2) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="demandControlRequired" label="需量控制" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.demandControlRequired" type="danger" size="small">是</el-tag>
                  <span v-else>否</span>
                </template>
              </el-table-column>
              <el-table-column prop="revenue" label="收益(元)" width="100" align="right">
                <template #default="{ row }">{{ row.revenue?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="degradationCost" label="损耗成本(元)" width="110" align="right">
                <template #default="{ row }">{{ row.degradationCost?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="demandSaving" label="需量节省(元)" width="110" align="right">
                <template #default="{ row }">{{ row.demandSaving?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="objectiveScore" label="目标得分" width="100" align="right">
                <template #default="{ row }">{{ row.objectiveScore?.toFixed(4) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="历史记录" name="history">
          <el-card shadow="hover">
            <template #header>
              <div class="card-header">
                <span>计划历史记录</span>
              </div>
            </template>
            <el-table :data="historyList" border stripe style="width: 100%">
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column prop="strategyCode" label="策略编码" width="150" />
              <el-table-column prop="planDate" label="计划日期" width="120" />
              <el-table-column prop="planType" label="计划类型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.planType === 'DAY_AHEAD' ? 'primary' : 'warning'" size="small">
                    {{ row.planType === 'DAY_AHEAD' ? '日前计划' : '滚动计划' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="expectedRevenue" label="预期收益(元)" width="120" align="right">
                <template #default="{ row }">{{ row.expectedRevenue?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="expectedDegradation" label="损耗(元)" width="100" align="right">
                <template #default="{ row }">{{ row.expectedDegradation?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="expectedDemandSaving" label="需量节省(元)" width="120" align="right">
                <template #default="{ row }">{{ row.expectedDemandSaving?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="totalObjectiveScore" label="综合得分" width="100" align="right">
                <template #default="{ row }">{{ row.totalObjectiveScore?.toFixed(4) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)" size="small">
                    {{ getStatusName(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="generatedAt" label="生成时间" width="160" />
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" size="small" @click="handleViewPlan(row)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { strategyConfigApi, dispatchPlanApi, type DispatchPlan, type StrategyConfig } from '@/api'

const strategyList = ref<StrategyConfig[]>([])
const selectedStrategy = ref('')
const planDate = ref('')
const selectedPlan = ref<DispatchPlan | null>(null)
const historyList = ref<DispatchPlan[]>([])
const activeTab = ref('chart')
const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

const formatNumber = (num: number | undefined) => {
  if (num === undefined || num === null) return '0.00'
  return num.toFixed(2)
}

const calculateNetBenefit = () => {
  if (!selectedPlan.value) return '0.00'
  const revenue = selectedPlan.value.expectedRevenue || 0
  const degradation = selectedPlan.value.expectedDegradation || 0
  const demandSaving = selectedPlan.value.expectedDemandSaving || 0
  return (revenue + demandSaving - degradation).toFixed(2)
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'warning',
    APPROVED: 'primary',
    EXECUTING: 'primary',
    EXECUTED: 'success',
    CANCELLED: 'danger',
    EXPIRED: 'info'
  }
  return typeMap[status] || 'info'
}

const getStatusName = (status: string) => {
  const nameMap: Record<string, string> = {
    PENDING: '待执行',
    APPROVED: '已审核',
    EXECUTING: '执行中',
    EXECUTED: '已完成',
    CANCELLED: '已取消',
    EXPIRED: '已过期'
  }
  return nameMap[status] || status
}

const getPeriodTypeTag = (type: string | undefined) => {
  const tagMap: Record<string, 'danger' | 'warning' | 'success' | 'info'> = {
    PEAK: 'danger',
    FLAT: 'warning',
    VALLEY: 'success'
  }
  return tagMap[type || ''] || 'info'
}

const getPeriodTypeName = (type: string | undefined) => {
  const nameMap: Record<string, string> = {
    PEAK: '尖峰',
    FLAT: '平时',
    VALLEY: '谷段'
  }
  return nameMap[type || ''] || type || '--'
}

const getActionTypeTag = (type: string | undefined) => {
  const tagMap: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
    CHARGE: 'success',
    DISCHARGE: 'danger',
    PEAK_SHAVING: 'danger',
    VALLEY_FILLING: 'success',
    DEMAND_CONTROL: 'danger',
    IDLE: 'info'
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
    IDLE: '待机'
  }
  return nameMap[type || ''] || type || '--'
}

const getSocColor = (soc: number | undefined) => {
  if (soc === undefined) return '#dcdfe6'
  if (soc >= 80) return '#67c23a'
  if (soc >= 50) return '#409eff'
  if (soc >= 30) return '#e6a23c'
  return '#f56c6c'
}

const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chartInstance || !selectedPlan.value?.planHours) return

  const hours = selectedPlan.value.planHours.map(h => `${h.hourIndex}:00`)
  const powerData = selectedPlan.value.planHours.map(h => h.power || 0)
  const priceData = selectedPlan.value.planHours.map(h => (h.price || 0) * 100)
  const socData = selectedPlan.value.planHours.map(h => h.expectedSoc || 0)

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['充放电功率', '电价(×100)', 'SOC'],
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
      data: hours,
      axisLabel: { interval: 2 }
    },
    yAxis: [
      {
        type: 'value',
        name: '功率(kW)',
        position: 'left'
      },
      {
        type: 'value',
        name: '电价/SOC',
        position: 'right',
        max: 100
      }
    ],
    series: [
      {
        name: '充放电功率',
        type: 'bar',
        data: powerData,
        itemStyle: {
          color: (params: any) => params.value >= 0 ? '#f56c6c' : '#67c23a'
        },
        markLine: {
          silent: true,
          data: [{ yAxis: 0, lineStyle: { color: '#909399', type: 'dashed' } }]
        }
      },
      {
        name: '电价(×100)',
        type: 'line',
        yAxisIndex: 1,
        data: priceData,
        smooth: true,
        lineStyle: { color: '#409eff' },
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        }
      },
      {
        name: 'SOC',
        type: 'line',
        yAxisIndex: 1,
        data: socData,
        smooth: true,
        lineStyle: { color: '#e6a23c', type: 'dashed' },
        itemStyle: { color: '#e6a23c' }
      }
    ]
  }

  chartInstance.setOption(option)
}

const loadStrategies = async () => {
  try {
    strategyList.value = await strategyConfigApi.listEnabled()
    if (strategyList.value.length > 0 && !selectedStrategy.value) {
      selectedStrategy.value = strategyList.value[0].strategyCode
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载策略列表失败')
  }
}

const loadHistory = async () => {
  if (!selectedStrategy.value) return
  try {
    historyList.value = await dispatchPlanApi.listByStrategyId(0)
    historyList.value = historyList.value.filter(p => p.strategyCode === selectedStrategy.value)
  } catch (error: any) {
    ElMessage.error(error.message || '加载历史记录失败')
  }
}

const handleGenerate = async () => {
  if (!selectedStrategy.value || !planDate.value) {
    ElMessage.warning('请选择策略和日期')
    return
  }
  try {
    selectedPlan.value = await dispatchPlanApi.generate({
      strategyCode: selectedStrategy.value,
      planDate: planDate.value,
      planType: 'DAY_AHEAD',
      usePriceForecast: true,
      useLoadForecast: true
    })
    ElMessage.success('计划生成成功')
    nextTick(() => {
      updateChart()
    })
    loadHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '生成计划失败')
  }
}

const handleRegenerate = async () => {
  if (!selectedPlan.value?.id) return
  try {
    selectedPlan.value = await dispatchPlanApi.regenerate(selectedPlan.value.id)
    ElMessage.success('计划重新生成成功')
    nextTick(() => {
      updateChart()
    })
    loadHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '重新生成计划失败')
  }
}

const handleExecuteCurrent = async () => {
  if (!selectedStrategy.value) return
  try {
    const result = await dispatchPlanApi.executeCurrentHour(selectedStrategy.value)
    ElMessage.success(`执行成功：${result.actionType}`)
  } catch (error: any) {
    ElMessage.error(error.message || '执行失败')
  }
}

const handleApprove = async () => {
  if (!selectedPlan.value?.id) return
  try {
    await dispatchPlanApi.approve(selectedPlan.value.id, 'admin')
    ElMessage.success('审核通过')
    selectedPlan.value.status = 'APPROVED'
  } catch (error: any) {
    ElMessage.error(error.message || '审核失败')
  }
}

const handleCancel = async () => {
  if (!selectedPlan.value?.id) return
  try {
    await dispatchPlanApi.cancel(selectedPlan.value.id)
    ElMessage.success('计划已取消')
    selectedPlan.value.status = 'CANCELLED'
  } catch (error: any) {
    ElMessage.error(error.message || '取消失败')
  }
}

const handleViewPlan = async (row: DispatchPlan) => {
  try {
    selectedPlan.value = await dispatchPlanApi.get(row.id)
    activeTab.value = 'chart'
    nextTick(() => {
      updateChart()
    })
  } catch (error: any) {
    ElMessage.error(error.message || '加载计划失败')
  }
}

watch(selectedStrategy, () => {
  selectedPlan.value = null
  loadHistory()
})

onMounted(() => {
  const today = new Date()
  planDate.value = today.toISOString().split('T')[0]
  loadStrategies()
  nextTick(() => {
    initChart()
  })
  window.addEventListener('resize', () => {
    chartInstance?.resize()
  })
})
</script>

<style scoped lang="less">
.dispatch-plan-page {
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
      align-items: center;
    }
  }

  .stat-row {
    margin-bottom: 20px;

    .stat-item {
      text-align: center;

      .stat-label {
        font-size: 14px;
        color: #909399;
        margin-bottom: 8px;
      }

      .stat-value {
        font-size: 24px;
        font-weight: 600;
        color: #303133;
      }
    }
  }

  .main-tabs {
    margin-top: 20px;

    :deep(.el-tabs__content) {
      padding: 0;
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
      height: 400px;
      width: 100%;
    }
  }

  .score-row {
    margin-bottom: 20px;

    .score-item {
      text-align: center;

      .score-label {
        font-size: 14px;
        color: #909399;
        margin-bottom: 10px;
      }

      .score-value {
        font-size: 16px;
        font-weight: 600;
        color: #303133;
        margin-top: 10px;
      }
    }
  }

  .text-success {
    color: #67c23a;
  }

  .text-danger {
    color: #f56c6c;
  }
}
</style>
