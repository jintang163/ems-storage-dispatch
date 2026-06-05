<template>
  <div class="dashboard">
    <div class="page-container">
      <h2 class="page-title">实时监控</h2>

      <el-row :gutter="20" class="stat-row">
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-header">
              <div class="stat-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
                <el-icon><Lightning /></el-icon>
              </div>
            </div>
            <div class="stat-content">
              <div class="stat-label">实时用电负荷</div>
              <div class="stat-value">
                {{ meterData?.data?.activePower || '--' }}
                <span class="stat-unit">kW</span>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-header">
              <div class="stat-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
                <el-icon><Sunny /></el-icon>
              </div>
            </div>
            <div class="stat-content">
              <div class="stat-label">光伏出力</div>
              <div class="stat-value">
                {{ pvData?.data?.outputPower || '--' }}
                <span class="stat-unit">kW</span>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-header">
              <div class="stat-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
                <el-icon><Battery /></el-icon>
              </div>
            </div>
            <div class="stat-content">
              <div class="stat-label">电池SOC</div>
              <div class="stat-value">
                {{ bmsData?.data?.soc || '--' }}
                <span class="stat-unit">%</span>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-header">
              <div class="stat-icon" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);">
                <el-icon><Switch /></el-icon>
              </div>
            </div>
            <div class="stat-content">
              <div class="stat-label">PCS充放电功率</div>
              <div class="stat-value">
                {{ pcsData?.data?.activePower || '--' }}
                <span class="stat-unit">kW</span>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="chart-row">
        <el-col :span="12">
          <div class="page-container">
            <h3 class="page-title">用电负荷趋势</h3>
            <div ref="meterChartRef" class="chart-container" style="height: 350px;"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-container">
            <h3 class="page-title">光伏出力趋势</h3>
            <div ref="pvChartRef" class="chart-container" style="height: 350px;"></div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="chart-row">
        <el-col :span="12">
          <div class="page-container">
            <h3 class="page-title">电池SOC趋势</h3>
            <div ref="bmsChartRef" class="chart-container" style="height: 350px;"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-container">
            <h3 class="page-title">PCS充放电功率趋势</h3>
            <div ref="pcsChartRef" class="chart-container" style="height: 350px;"></div>
          </div>
        </el-col>
      </el-row>

      <div class="page-container">
        <h3 class="page-title">设备状态</h3>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-card shadow="hover">
              <template #header>
                <div class="card-header">
                  <span>电表</span>
                  <el-tag :type="meterData ? 'success' : 'danger'" size="small">
                    {{ meterData ? '在线' : '离线' }}
                  </el-tag>
                </div>
              </template>
              <div class="device-info">
                <p><strong>设备编号：</strong>{{ dataStore.deviceSns.meter }}</p>
                <p><strong>有功功率：</strong>{{ meterData?.data?.activePower || '--' }} kW</p>
                <p><strong>无功功率：</strong>{{ meterData?.data?.reactivePower || '--' }} kvar</p>
                <p><strong>功率因数：</strong>{{ meterData?.data?.powerFactor || '--' }}</p>
                <p><strong>频率：</strong>{{ meterData?.data?.frequency || '--' }} Hz</p>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <template #header>
                <div class="card-header">
                  <span>光伏逆变器</span>
                  <el-tag :type="pvData ? 'success' : 'danger'" size="small">
                    {{ pvData ? '在线' : '离线' }}
                  </el-tag>
                </div>
              </template>
              <div class="device-info">
                <p><strong>设备编号：</strong>{{ dataStore.deviceSns.pv }}</p>
                <p><strong>输出功率：</strong>{{ pvData?.data?.outputPower || '--' }} kW</p>
                <p><strong>日发电量：</strong>{{ pvData?.data?.dailyEnergy || '--' }} kWh</p>
                <p><strong>总发电量：</strong>{{ pvData?.data?.totalEnergy || '--' }} kWh</p>
                <p><strong>机内温度：</strong>{{ pvData?.data?.temperature || '--' }} °C</p>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <template #header>
                <div class="card-header">
                  <span>BMS电池管理</span>
                  <el-tag :type="bmsData ? 'success' : 'danger'" size="small">
                    {{ bmsData ? '在线' : '离线' }}
                  </el-tag>
                </div>
              </template>
              <div class="device-info">
                <p><strong>设备编号：</strong>{{ dataStore.deviceSns.bms }}</p>
                <p><strong>SOC：</strong>{{ bmsData?.data?.soc || '--' }} %</p>
                <p><strong>SOH：</strong>{{ bmsData?.data?.soh || '--' }} %</p>
                <p><strong>总电压：</strong>{{ bmsData?.data?.totalVoltage || '--' }} V</p>
                <p><strong>总电流：</strong>{{ bmsData?.data?.totalCurrent || '--' }} A</p>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <template #header>
                <div class="card-header">
                  <span>PCS储能变流器</span>
                  <el-tag :type="pcsData ? 'success' : 'danger'" size="small">
                    {{ pcsData ? '在线' : '离线' }}
                  </el-tag>
                </div>
              </template>
              <div class="device-info">
                <p><strong>设备编号：</strong>{{ dataStore.deviceSns.pcs }}</p>
                <p><strong>有功功率：</strong>{{ pcsData?.data?.activePower || '--' }} kW</p>
                <p><strong>无功功率：</strong>{{ pcsData?.data?.reactivePower || '--' }} kvar</p>
                <p><strong>工作模式：</strong>{{ getWorkModeText(pcsData?.data?.workMode) }}</p>
                <p><strong>运行状态：</strong>{{ getStatusText(pcsData?.data?.status) }}</p>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <div class="page-container">
        <h3 class="page-title">储能控制</h3>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header>
                <span>充电控制</span>
              </template>
              <div class="control-form">
                <el-form label-width="80px">
                  <el-form-item label="充电功率">
                    <el-input-number v-model="chargePower" :min="0" :max="100" :step="1" />
                    <span class="unit">kW</span>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" @click="startCharge">开始充电</el-button>
                  </el-form-item>
                </el-form>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header>
                <span>放电控制</span>
              </template>
              <div class="control-form">
                <el-form label-width="80px">
                  <el-form-item label="放电功率">
                    <el-input-number v-model="dischargePower" :min="0" :max="100" :step="1" />
                    <span class="unit">kW</span>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="warning" @click="startDischarge">开始放电</el-button>
                  </el-form-item>
                </el-form>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header>
                <span>停止控制</span>
              </template>
              <div class="control-form">
                <p style="margin-bottom: 20px; color: #606266;">
                  点击停止按钮将终止当前的充放电操作
                </p>
                <el-button type="danger" @click="stopControl">停止运行</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { useDataStore } from '@/stores/data'
import { ElMessage } from 'element-plus'
import { commandApi } from '@/api'

const dataStore = useDataStore()
const { meterData, pvData, bmsData, pcsData } = dataStore

const meterChartRef = ref<HTMLElement>()
const pvChartRef = ref<HTMLElement>()
const bmsChartRef = ref<HTMLElement>()
const pcsChartRef = ref<HTMLElement>()

let meterChart: echarts.ECharts | null = null
let pvChart: echarts.ECharts | null = null
let bmsChart: echarts.ECharts | null = null
let pcsChart: echarts.ECharts | null = null

const chargePower = ref(50)
const dischargePower = ref(50)

const meterDataHistory = ref<{ time: string; value: number }[]>([])
const pvDataHistory = ref<{ time: string; value: number }[]>([])
const bmsDataHistory = ref<{ time: string; value: number }[]>([])
const pcsDataHistory = ref<{ time: string; value: number }[]>([])

const getWorkModeText = (mode: number | undefined) => {
  const modeMap: Record<number, string> = {
    0: '待机',
    1: '充电',
    2: '放电',
    3: '恒压',
    4: '恒流'
  }
  return mode !== undefined ? modeMap[mode] || '未知' : '--'
}

const getStatusText = (status: number | undefined) => {
  const statusMap: Record<number, string> = {
    0: '停止',
    1: '运行',
    2: '故障',
    3: '告警'
  }
  return status !== undefined ? statusMap[status] || '未知' : '--'
}

const initCharts = () => {
  if (meterChartRef.value) {
    meterChart = echarts.init(meterChartRef.value)
    updateMeterChart()
  }
  if (pvChartRef.value) {
    pvChart = echarts.init(pvChartRef.value)
    updatePvChart()
  }
  if (bmsChartRef.value) {
    bmsChart = echarts.init(bmsChartRef.value)
    updateBmsChart()
  }
  if (pcsChartRef.value) {
    pcsChart = echarts.init(pcsChartRef.value)
    updatePcsChart()
  }
}

const getChartOption = (data: { time: string; value: number }[], color: string, name: string, unit: string) => {
  return {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>{a}: {c}' + unit
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
      data: data.map(d => d.time)
    },
    yAxis: {
      type: 'value',
      name: unit
    },
    series: [
      {
        name,
        type: 'line',
        smooth: true,
        data: data.map(d => d.value),
        itemStyle: { color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: color + '80' },
            { offset: 1, color: color + '10' }
          ])
        }
      }
    ]
  }
}

const updateMeterChart = () => {
  if (meterChart) {
    meterChart.setOption(getChartOption(meterDataHistory.value, '#667eea', '用电负荷', 'kW'))
  }
}

const updatePvChart = () => {
  if (pvChart) {
    pvChart.setOption(getChartOption(pvDataHistory.value, '#f5576c', '光伏出力', 'kW'))
  }
}

const updateBmsChart = () => {
  if (bmsChart) {
    bmsChart.setOption(getChartOption(bmsDataHistory.value, '#4facfe', 'SOC', '%'))
  }
}

const updatePcsChart = () => {
  if (pcsChart) {
    pcsChart.setOption(getChartOption(pcsDataHistory.value, '#43e97b', '充放电功率', 'kW'))
  }
}

const addDataPoint = (
  history: { time: string; value: number }[],
  value: number | undefined,
  maxPoints: number = 60
) => {
  if (value === undefined || value === null) return
  const now = new Date()
  const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
  history.push({ time, value })
  if (history.length > maxPoints) {
    history.shift()
  }
}

watch(
  () => dataStore.meterData,
  (data) => {
    if (data?.data?.activePower !== undefined) {
      addDataPoint(meterDataHistory.value, data.data.activePower)
      updateMeterChart()
    }
  },
  { deep: true }
)

watch(
  () => dataStore.pvData,
  (data) => {
    if (data?.data?.outputPower !== undefined) {
      addDataPoint(pvDataHistory.value, data.data.outputPower)
      updatePvChart()
    }
  },
  { deep: true }
)

watch(
  () => dataStore.bmsData,
  (data) => {
    if (data?.data?.soc !== undefined) {
      addDataPoint(bmsDataHistory.value, data.data.soc)
      updateBmsChart()
    }
  },
  { deep: true }
)

watch(
  () => dataStore.pcsData,
  (data) => {
    if (data?.data?.activePower !== undefined) {
      addDataPoint(pcsDataHistory.value, data.data.activePower)
      updatePcsChart()
    }
  },
  { deep: true }
)

const startCharge = async () => {
  try {
    await commandApi.charge(dataStore.deviceSns.pcs, chargePower.value)
    ElMessage.success(`已发送充电指令，功率：${chargePower.value}kW`)
  } catch (e) {
    ElMessage.error('充电指令发送失败')
  }
}

const startDischarge = async () => {
  try {
    await commandApi.discharge(dataStore.deviceSns.pcs, dischargePower.value)
    ElMessage.success(`已发送放电指令，功率：${dischargePower.value}kW`)
  } catch (e) {
    ElMessage.error('放电指令发送失败')
  }
}

const stopControl = async () => {
  try {
    await commandApi.stop(dataStore.deviceSns.pcs)
    ElMessage.success('已发送停止指令')
  } catch (e) {
    ElMessage.error('停止指令发送失败')
  }
}

const handleResize = () => {
  meterChart?.resize()
  pvChart?.resize()
  bmsChart?.resize()
  pcsChart?.resize()
}

onMounted(() => {
  initCharts()
  dataStore.startPolling(5000)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  dataStore.stopPolling()
  window.removeEventListener('resize', handleResize)
  meterChart?.dispose()
  pvChart?.dispose()
  bmsChart?.dispose()
  pcsChart?.dispose()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-header {
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
}

.chart-row {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.device-info p {
  margin: 8px 0;
  color: #606266;
  font-size: 14px;
}

.control-form {
  padding: 10px 0;
}

.unit {
  margin-left: 8px;
  color: #909399;
}
</style>
