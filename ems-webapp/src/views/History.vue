<template>
  <div class="history-page">
    <div class="page-container">
      <h2 class="page-title">历史数据查询</h2>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="设备类型">
          <el-select v-model="queryForm.deviceType" placeholder="请选择设备类型" style="width: 150px">
            <el-option label="电表" value="meter" />
            <el-option label="光伏" value="pv" />
            <el-option label="BMS" value="bms" />
            <el-option label="PCS" value="pcs" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备编号">
          <el-select v-model="queryForm.deviceSn" placeholder="请选择设备" style="width: 180px">
            <el-option
              v-for="sn in deviceSnsList"
              :key="sn"
              :label="sn"
              :value="sn"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据字段">
          <el-select v-model="queryForm.field" placeholder="请选择字段" style="width: 150px">
            <el-option
              v-for="field in fieldOptions"
              :key="field.value"
              :label="field.label"
              :value="field.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="exportData">导出</el-button>
        </el-form-item>
      </el-form>

      <div class="chart-section">
        <div ref="chartRef" class="chart-container" style="height: 400px;"></div>
      </div>

      <div class="table-section">
        <h3 class="section-title">数据明细</h3>
        <el-table :data="tableData" border stripe style="width: 100%" max-height="400">
          <el-table-column prop="time" label="时间" width="180" fixed="left" />
          <el-table-column
            v-for="column in tableColumns"
            :key="column.prop"
            :prop="column.prop"
            :label="column.label"
            :width="column.width"
          >
            <template #default="{ row }">
              <span v-if="typeof row[column.prop] === 'number'">
                {{ row[column.prop].toFixed(2) }}
              </span>
              <span v-else>{{ row[column.prop] }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { dataApi, type DataQuery } from '@/api'
import dayjs from 'dayjs'

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const deviceSnsList = ['METER-001', 'PV-001', 'BMS-001', 'PCS-001']

const fieldMap: Record<string, { label: string; value: string }[]> = {
  meter: [
    { label: '有功功率', value: 'activePower' },
    { label: '无功功率', value: 'reactivePower' },
    { label: '功率因数', value: 'powerFactor' },
    { label: '频率', value: 'frequency' },
    { label: 'A相电压', value: 'voltageA' },
    { label: 'B相电压', value: 'voltageB' },
    { label: 'C相电压', value: 'voltageC' }
  ],
  pv: [
    { label: '输出功率', value: 'outputPower' },
    { label: '日发电量', value: 'dailyEnergy' },
    { label: '总发电量', value: 'totalEnergy' },
    { label: '机内温度', value: 'temperature' },
    { label: '直流电压', value: 'dcVoltage' },
    { label: '直流电流', value: 'dcCurrent' }
  ],
  bms: [
    { label: 'SOC', value: 'soc' },
    { label: 'SOH', value: 'soh' },
    { label: '总电压', value: 'totalVoltage' },
    { label: '总电流', value: 'totalCurrent' },
    { label: '最高温度', value: 'maxTemperature' },
    { label: '最低温度', value: 'minTemperature' }
  ],
  pcs: [
    { label: '有功功率', value: 'activePower' },
    { label: '无功功率', value: 'reactivePower' },
    { label: '直流电压', value: 'dcVoltage' },
    { label: '直流电流', value: 'dcCurrent' },
    { label: '效率', value: 'efficiency' },
    { label: '工作模式', value: 'workMode' }
  ]
}

const tableColumnMap: Record<string, { prop: string; label: string; width: number }[]> = {
  meter: [
    { prop: 'activePower', label: '有功功率(kW)', width: 120 },
    { prop: 'reactivePower', label: '无功功率(kvar)', width: 130 },
    { prop: 'powerFactor', label: '功率因数', width: 100 },
    { prop: 'frequency', label: '频率(Hz)', width: 100 },
    { prop: 'voltageA', label: 'A相电压(V)', width: 110 },
    { prop: 'voltageB', label: 'B相电压(V)', width: 110 },
    { prop: 'voltageC', label: 'C相电压(V)', width: 110 }
  ],
  pv: [
    { prop: 'outputPower', label: '输出功率(kW)', width: 120 },
    { prop: 'dailyEnergy', label: '日发电量(kWh)', width: 130 },
    { prop: 'totalEnergy', label: '总发电量(kWh)', width: 130 },
    { prop: 'temperature', label: '温度(°C)', width: 100 },
    { prop: 'dcVoltage', label: '直流电压(V)', width: 110 },
    { prop: 'dcCurrent', label: '直流电流(A)', width: 110 }
  ],
  bms: [
    { prop: 'soc', label: 'SOC(%)', width: 100 },
    { prop: 'soh', label: 'SOH(%)', width: 100 },
    { prop: 'totalVoltage', label: '总电压(V)', width: 110 },
    { prop: 'totalCurrent', label: '总电流(A)', width: 110 },
    { prop: 'maxTemperature', label: '最高温度(°C)', width: 120 },
    { prop: 'minTemperature', label: '最低温度(°C)', width: 120 }
  ],
  pcs: [
    { prop: 'activePower', label: '有功功率(kW)', width: 120 },
    { prop: 'reactivePower', label: '无功功率(kvar)', width: 130 },
    { prop: 'dcVoltage', label: '直流电压(V)', width: 110 },
    { prop: 'dcCurrent', label: '直流电流(A)', width: 110 },
    { prop: 'efficiency', label: '效率(%)', width: 100 },
    { prop: 'workMode', label: '工作模式', width: 100 }
  ]
}

const queryForm = reactive({
  deviceType: 'meter',
  deviceSn: 'METER-001',
  field: 'activePower',
  timeRange: [
    dayjs().subtract(1, 'day').format('YYYY-MM-DD 00:00:00'),
    dayjs().format('YYYY-MM-DD HH:mm:ss')
  ] as [string, string]
})

const tableData = ref<any[]>([])

const fieldOptions = computed(() => fieldMap[queryForm.deviceType] || [])
const tableColumns = computed(() => tableColumnMap[queryForm.deviceType] || [])

watch(
  () => queryForm.deviceType,
  (newType) => {
    const typeMap: Record<string, string> = {
      meter: 'METER-001',
      pv: 'PV-001',
      bms: 'BMS-001',
      pcs: 'PCS-001'
    }
    queryForm.deviceSn = typeMap[newType] || ''
    queryForm.field = fieldOptions[0]?.value || ''
  }
)

const initChart = () => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
  }
}

const updateChart = (data: any[]) => {
  if (!chart || !queryForm.field) return

  const fieldLabel = fieldOptions.value.find(f => f.value === queryForm.field)?.label || queryForm.field

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const param = params[0]
        return `${param.axisValue}<br/>${fieldLabel}: ${param.value}`
      }
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
      name: fieldLabel
    },
    series: [
      {
        name: fieldLabel,
        type: 'line',
        smooth: true,
        data: data.map(d => d[queryForm.field]),
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff80' },
            { offset: 1, color: '#409eff10' }
          ])
        }
      }
    ]
  }

  chart.setOption(option)
}

const loadData = async () => {
  if (!queryForm.timeRange || queryForm.timeRange.length !== 2) {
    ElMessage.warning('请选择时间范围')
    return
  }

  try {
    const params: DataQuery = {
      deviceType: queryForm.deviceType,
      deviceSn: queryForm.deviceSn,
      startTime: queryForm.timeRange[0],
      endTime: queryForm.timeRange[1],
      field: queryForm.field,
      limit: 1000
    }

    const data = await dataApi.getHistory(params)

    const formattedData = data.map(item => ({
      ...item,
      time: dayjs(item.time || item.timestamp).format('YYYY-MM-DD HH:mm:ss')
    }))

    tableData.value = formattedData
    updateChart(formattedData)

    ElMessage.success(`查询成功，共 ${data.length} 条数据`)
  } catch (e) {
    ElMessage.error('查询失败')
  }
}

const exportData = () => {
  if (!tableData.value.length) {
    ElMessage.warning('没有可导出的数据')
    return
  }

  const headers = ['时间', ...tableColumns.value.map(c => c.label)]
  const rows = tableData.value.map(row => {
    return [
      row.time,
      ...tableColumns.value.map(col => {
        const val = row[col.prop]
        return typeof val === 'number' ? val.toFixed(2) : val
      })
    ]
  })

  const csvContent = [headers.join(','), ...rows.map(r => r.join(','))].join('\n')
  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${queryForm.deviceType}_${dayjs().format('YYYYMMDDHHmmss')}.csv`
  link.click()
  URL.revokeObjectURL(url)

  ElMessage.success('导出成功')
}

const handleResize = () => {
  chart?.resize()
}

onMounted(() => {
  initChart()
  loadData()
  window.addEventListener('resize', handleResize)
})
</script>

<style scoped>
.query-form {
  margin-bottom: 20px;
}

.chart-section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 15px;
  color: #303133;
}
</style>
