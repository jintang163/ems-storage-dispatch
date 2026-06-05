<template>
  <div class="battery-degradation-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">电池衰减模型</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增模型
        </el-button>
      </div>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-card shadow="hover" class="model-list-card">
            <template #header>
              <div class="card-header">
                <span>模型列表</span>
                <el-button-group>
                  <el-button size="small" :type="modelTypeFilter === '' ? 'primary' : ''" @click="modelTypeFilter = ''">全部</el-button>
                  <el-button size="small" :type="modelTypeFilter === 'LINEAR' ? 'primary' : ''" @click="modelTypeFilter = 'LINEAR'">线性</el-button>
                  <el-button size="small" :type="modelTypeFilter === 'EXPONENTIAL' ? 'primary' : ''" @click="modelTypeFilter = 'EXPONENTIAL'">指数</el-button>
                </el-button-group>
              </div>
            </template>
            <el-scrollbar height="400px">
              <div
                v-for="model in filteredModels"
                :key="model.id"
                class="model-item"
                :class="{ active: selectedModel?.id === model.id }"
                @click="selectModel(model)"
              >
                <div class="model-item-header">
                  <span class="model-name">{{ model.modelName }}</span>
                  <el-tag v-if="model.defaultModel" type="success" size="small">默认</el-tag>
                </div>
                <div class="model-item-info">
                  <el-tag :type="getModelTypeTag(model.modelType)" size="small">{{ getModelTypeName(model.modelType) }}</el-tag>
                  <span class="model-battery-type">{{ model.batteryType || '通用' }}</span>
                </div>
                <div class="model-item-desc">
                  寿命终止SOH: {{ model.endOfLifeSoh }}%
                </div>
              </div>
            </el-scrollbar>
          </el-card>
        </el-col>

        <el-col :span="16">
          <el-card shadow="hover" v-if="selectedModel">
            <template #header>
              <div class="card-header">
                <span>{{ selectedModel.modelName }} - 衰减曲线</span>
                <el-button-group>
                  <el-button size="small" @click="loadCurveData">刷新曲线</el-button>
                  <el-button size="small" type="primary" @click="handleEdit(selectedModel)">编辑</el-button>
                  <el-button size="small" type="success" @click="handleSetDefault" :disabled="selectedModel.defaultModel">设为默认</el-button>
                </el-button-group>
              </div>
            </template>

            <el-row :gutter="20">
              <el-col :span="16">
                <div ref="chartRef" class="chart-container" style="height: 350px;"></div>
              </el-col>
              <el-col :span="8">
                <el-descriptions title="模型参数" border column="1" size="small">
                  <el-descriptions-item label="模型类型">
                    {{ getModelTypeName(selectedModel.modelType) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="电池类型">
                    {{ selectedModel.batteryType || '--' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="每循环衰减率">
                    {{ selectedModel.degradationRatePerCycle ? (selectedModel.degradationRatePerCycle * 100).toFixed(4) + '%' : '--' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="寿命终止SOH">
                    {{ selectedModel.endOfLifeSoh }}%
                  </el-descriptions-item>
                  <el-descriptions-item label="保修循环次数">
                    {{ selectedModel.warrantyCycleCount || '--' }} 次
                  </el-descriptions-item>
                  <el-descriptions-item label="预计寿命">
                    {{ selectedModel.estimatedLifespanYears || '--' }} 年
                  </el-descriptions-item>
                  <el-descriptions-item label="日历老化率">
                    {{ selectedModel.calendarAgingRatePerYear ? (selectedModel.calendarAgingRatePerYear * 100).toFixed(2) + '%/年' : '--' }}
                  </el-descriptions-item>
                </el-descriptions>
              </el-col>
            </el-row>
          </el-card>

          <el-card shadow="hover" style="margin-top: 20px;">
            <template #header>
              <div class="card-header">
                <span>SOH预估与寿命计算工具</span>
              </div>
            </template>

            <el-row :gutter="20">
              <el-col :span="12">
                <h4 style="margin-top: 0;">SOH预估</h4>
                <el-form label-width="120px">
                  <el-form-item label="循环次数">
                    <el-input-number v-model="estimateParams.cycleCount" :min="0" :step="100" style="width: 200px;" />
                    <span class="form-unit">次</span>
                  </el-form-item>
                  <el-form-item label="平均温度">
                    <el-input-number v-model="estimateParams.avgTemperature" :step="0.1" style="width: 200px;" />
                    <span class="form-unit">°C</span>
                  </el-form-item>
                  <el-form-item label="平均SOC">
                    <el-input-number v-model="estimateParams.avgSoc" :min="0" :max="100" :step="1" style="width: 200px;" />
                    <span class="form-unit">%</span>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" @click="estimateSoh">预估SOH</el-button>
                  </el-form-item>
                </el-form>
                <el-alert v-if="estimateResult !== null" :title="`预估SOH: ${estimateResult.toFixed(2)}%`" type="success" show-icon />
              </el-col>

              <el-col :span="12">
                <h4 style="margin-top: 0;">剩余寿命预估</h4>
                <el-form label-width="120px">
                  <el-form-item label="当前SOH">
                    <el-input-number v-model="lifetimeParams.currentSoh" :min="0" :max="100" :step="1" style="width: 200px;" />
                    <span class="form-unit">%</span>
                  </el-form-item>
                  <el-form-item label="当前循环次数">
                    <el-input-number v-model="lifetimeParams.currentCycleCount" :min="0" :step="100" style="width: 200px;" />
                    <span class="form-unit">次</span>
                  </el-form-item>
                  <el-form-item label="日循环次数">
                    <el-input-number v-model="lifetimeParams.dailyCycles" :min="0" :step="0.1" :precision="1" style="width: 200px;" />
                    <span class="form-unit">次/天</span>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" @click="estimateLifetime">预估寿命</el-button>
                  </el-form-item>
                </el-form>
                <el-alert v-if="lifetimeResult !== null" :title="`预计剩余寿命: ${lifetimeResult.toFixed(1)} 年`" type="success" show-icon />
              </el-col>
            </el-row>
          </el-card>

          <el-card shadow="hover" style="margin-top: 20px;" v-if="selectedModel">
            <template #header>
              <div class="card-header">
                <span>数据点管理</span>
                <el-button size="small" type="primary" @click="handleAddPoint">
                  <el-icon><Plus /></el-icon>
                  添加数据点
                </el-button>
              </div>
            </template>

            <el-table :data="selectedModel.degradationPoints || []" border stripe size="small" style="width: 100%">
              <el-table-column prop="cycleCount" label="循环次数" width="100" />
              <el-table-column prop="soh" label="SOH (%)" width="100">
                <template #default="{ row }">
                  <span :style="{ color: getSohColor(row.soh) }">{{ row.soh }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="capacityRetention" label="容量保持率 (%)" width="130" />
              <el-table-column prop="internalResistanceRatio" label="内阻比" width="100" />
              <el-table-column prop="temperature" label="温度 (°C)" width="100" />
              <el-table-column prop="depthOfDischarge" label="DOD (%)" width="100" />
              <el-table-column prop="remarks" label="备注" />
              <el-table-column label="操作" width="80" fixed="right">
                <template #default="{ row }">
                  <el-button type="danger" size="small" @click="handleRemovePoint(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="formData.modelName" placeholder="请输入模型名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型类型" prop="modelType">
              <el-select v-model="formData.modelType" placeholder="请选择模型类型" style="width: 100%">
                <el-option label="线性模型" value="LINEAR" />
                <el-option label="指数模型" value="EXPONENTIAL" />
                <el-option label="分段模型" value="PIECEWISE" />
                <el-option label="经验模型" value="EMPIRICAL" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="电池类型">
              <el-select v-model="formData.batteryType" placeholder="请选择电池类型" style="width: 100%">
                <el-option label="磷酸铁锂(LFP)" value="LFP" />
                <el-option label="三元锂(NMC)" value="NMC" />
                <el-option label="铅酸" value="LEAD_ACID" />
                <el-option label="通用" value="GENERAL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="寿命终止SOH" prop="endOfLifeSoh">
              <el-input-number v-model="formData.endOfLifeSoh" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">衰减参数</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="每循环衰减率">
              <el-input-number v-model="formData.degradationRatePerCycle" :min="0" :step="0.00001" :precision="5" style="width: 100%" />
              <span class="form-unit">(0-1)</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="衰减常数">
              <el-input-number v-model="formData.decayConstant" :min="0" :step="0.00001" :precision="5" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="保修循环次数">
              <el-input-number v-model="formData.warrantyCycleCount" :min="0" :step="100" style="width: 100%" />
              <span class="form-unit">次</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保修SOH">
              <el-input-number v-model="formData.warrantySoh" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="日历老化率">
              <el-input-number v-model="formData.calendarAgingRatePerYear" :min="0" :step="0.001" :precision="4" style="width: 100%" />
              <span class="form-unit">/年</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预计寿命">
              <el-input-number v-model="formData.estimatedLifespanYears" :min="0" :step="0.1" :precision="1" style="width: 100%" />
              <span class="form-unit">年</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">影响因子</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="温度因子">
              <el-input-number v-model="formData.temperatureFactor" :min="0" :step="0.001" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SOC因子">
              <el-input-number v-model="formData.socFactor" :min="0" :step="0.001" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="充电倍率因子">
              <el-input-number v-model="formData.chargeRateFactor" :min="0" :step="0.001" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="放电倍率因子">
              <el-input-number v-model="formData.dischargeRateFactor" :min="0" :step="0.001" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="放电深度因子">
              <el-input-number v-model="formData.depthOfDischargeFactor" :min="0" :step="0.001" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大循环次数">
              <el-input-number v-model="formData.maxCycleCount" :min="0" :step="100" style="width: 100%" />
              <span class="form-unit">次</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">其他设置</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="默认模型">
              <el-switch v-model="formData.defaultModel" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="enabled">
              <el-radio-group v-model="formData.enabled">
                <el-radio :value="true">启用</el-radio>
                <el-radio :value="false">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="2"
            placeholder="请输入描述信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="pointDialogVisible"
      title="添加衰减数据点"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="pointFormRef"
        :model="pointFormData"
        :rules="pointFormRules"
        label-width="120px"
      >
        <el-form-item label="循环次数" prop="cycleCount">
          <el-input-number v-model="pointFormData.cycleCount" :min="0" :step="100" style="width: 100%" />
          <span class="form-unit">次</span>
        </el-form-item>
        <el-form-item label="SOH" prop="soh">
          <el-input-number v-model="pointFormData.soh" :min="0" :max="100" :step="0.1" :precision="2" style="width: 100%" />
          <span class="form-unit">%</span>
        </el-form-item>
        <el-form-item label="容量保持率">
          <el-input-number v-model="pointFormData.capacityRetention" :min="0" :max="100" :step="0.1" :precision="2" style="width: 100%" />
          <span class="form-unit">%</span>
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="pointFormData.temperature" :step="0.1" style="width: 100%" />
          <span class="form-unit">°C</span>
        </el-form-item>
        <el-form-item label="放电深度">
          <el-input-number v-model="pointFormData.depthOfDischarge" :min="0" :max="100" :step="1" style="width: 100%" />
          <span class="form-unit">%</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="pointFormData.remarks"
            type="textarea"
            :rows="2"
            placeholder="请输入备注信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pointDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitPoint">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { batteryDegradationApi, type BatteryDegradationModel, type BatteryDegradationPoint } from '@/api'

const tableData = ref<BatteryDegradationModel[]>([])
const selectedModel = ref<BatteryDegradationModel | null>(null)
const dialogVisible = ref(false)
const pointDialogVisible = ref(false)
const dialogTitle = ref('新增模型')
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const pointFormRef = ref<FormInstance>()
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const modelTypeFilter = ref('')

const filteredModels = computed(() => {
  if (!modelTypeFilter.value) return tableData.value
  return tableData.value.filter(item => item.modelType === modelTypeFilter.value)
})

const estimateParams = reactive({
  cycleCount: 1000,
  avgTemperature: 25,
  avgSoc: 50,
  avgChargeRate: 0.5,
  avgDischargeRate: 0.5,
  avgDepthOfDischarge: 80
})

const lifetimeParams = reactive({
  currentSoh: 95,
  currentCycleCount: 500,
  dailyCycles: 1
})

const estimateResult = ref<number | null>(null)
const lifetimeResult = ref<number | null>(null)

const formData = reactive<Partial<BatteryDegradationModel>>({
  id: undefined,
  modelName: '',
  modelType: 'LINEAR',
  endOfLifeSoh: 80,
  defaultModel: false,
  enabled: true
})

const formRules: FormRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  endOfLifeSoh: [{ required: true, message: '请输入寿命终止SOH', trigger: 'blur' }],
  enabled: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const pointFormData = reactive<Partial<BatteryDegradationPoint>>({
  cycleCount: 0,
  soh: 100
})

const pointFormRules: FormRules = {
  cycleCount: [{ required: true, message: '请输入循环次数', trigger: 'blur' }],
  soh: [{ required: true, message: '请输入SOH', trigger: 'blur' }]
}

const curveData = ref<BatteryDegradationPoint[]>([])

const getModelTypeName = (type: string) => {
  const typeMap: Record<string, string> = {
    LINEAR: '线性模型',
    EXPONENTIAL: '指数模型',
    PIECEWISE: '分段模型',
    EMPIRICAL: '经验模型'
  }
  return typeMap[type] || type
}

const getModelTypeTag = (type: string) => {
  const tagMap: Record<string, 'primary' | 'success' | 'warning' | 'info'> = {
    LINEAR: 'primary',
    EXPONENTIAL: 'success',
    PIECEWISE: 'warning',
    EMPIRICAL: 'info'
  }
  return tagMap[type] || 'info'
}

const getSohColor = (soh: number | undefined) => {
  if (soh === undefined) return '#606266'
  if (soh >= 80) return '#67c23a'
  if (soh >= 60) return '#e6a23c'
  return '#f56c6c'
}

const loadData = async () => {
  try {
    const list = await batteryDegradationApi.list()
    tableData.value = list
    if (list.length > 0 && !selectedModel.value) {
      selectModel(list[0])
    }
  } catch (e) {
    ElMessage.error('加载数据失败')
  }
}

const selectModel = async (model: BatteryDegradationModel) => {
  selectedModel.value = model
  estimateResult.value = null
  lifetimeResult.value = null
  await loadCurveData()
}

const loadCurveData = async () => {
  if (!selectedModel.value) return

  try {
    const data = await batteryDegradationApi.generateCurve(selectedModel.value.id, 0, 6000, 100)
    curveData.value = data
    await nextTick()
    initChart()
  } catch (e) {
    ElMessage.error('加载曲线数据失败')
  }
}

const initChart = () => {
  if (!chartRef.value) return

  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const data = params[0]
        return `循环次数: ${data.name}<br/>SOH: ${data.value}%`
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
      name: '循环次数',
      boundaryGap: false,
      data: curveData.value.map(d => d.cycleCount)
    },
    yAxis: {
      type: 'value',
      name: 'SOH (%)',
      min: 0,
      max: 100
    },
    series: [
      {
        name: 'SOH',
        type: 'line',
        smooth: true,
        data: curveData.value.map(d => d.soh),
        itemStyle: { color: '#667eea' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#667eea80' },
            { offset: 1, color: '#667eea10' }
          ])
        },
        markLine: {
          silent: true,
          lineStyle: {
            color: '#f56c6c',
            type: 'dashed'
          },
          data: [
            {
              yAxis: selectedModel.value?.endOfLifeSoh || 80,
              label: {
                formatter: `EOL: ${selectedModel.value?.endOfLifeSoh || 80}%`
              }
            }
          ]
        }
      }
    ]
  }

  chart.setOption(option)
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增模型'
  Object.assign(formData, {
    id: undefined,
    modelName: '',
    modelType: 'LINEAR',
    batteryType: undefined,
    degradationRatePerCycle: undefined,
    decayConstant: undefined,
    endOfLifeSoh: 80,
    warrantyCycleCount: undefined,
    warrantySoh: undefined,
    calendarAgingRatePerYear: undefined,
    temperatureFactor: undefined,
    socFactor: undefined,
    chargeRateFactor: undefined,
    dischargeRateFactor: undefined,
    depthOfDischargeFactor: undefined,
    maxCycleCount: undefined,
    estimatedLifespanYears: undefined,
    defaultModel: false,
    enabled: true,
    description: undefined,
    degradationPoints: []
  })
  dialogVisible.value = true
}

const handleEdit = (row: BatteryDegradationModel) => {
  isEdit.value = true
  dialogTitle.value = '编辑模型'
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

const handleSetDefault = async () => {
  if (!selectedModel.value) return

  try {
    await batteryDegradationApi.setDefault(selectedModel.value.id)
    ElMessage.success('已设为默认模型')
    loadData()
  } catch (e) {
    ElMessage.error('设置失败')
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value && formData.id) {
          await batteryDegradationApi.update(formData.id, formData as BatteryDegradationModel)
          ElMessage.success('更新成功')
        } else {
          await batteryDegradationApi.create(formData)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadData()
      } catch (e) {
        ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
      }
    }
  })
}

const estimateSoh = async () => {
  if (!selectedModel.value) return

  try {
    const result = await batteryDegradationApi.estimateSohWithFactors({
      modelId: selectedModel.value.id,
      cycleCount: estimateParams.cycleCount,
      avgTemperature: estimateParams.avgTemperature,
      avgSoc: estimateParams.avgSoc,
      avgChargeRate: estimateParams.avgChargeRate,
      avgDischargeRate: estimateParams.avgDischargeRate,
      avgDepthOfDischarge: estimateParams.avgDepthOfDischarge
    })
    estimateResult.value = result
  } catch (e) {
    ElMessage.error('预估失败')
  }
}

const estimateLifetime = async () => {
  if (!selectedModel.value) return

  try {
    const result = await batteryDegradationApi.estimateRemainingLifespan({
      modelId: selectedModel.value.id,
      currentSoh: lifetimeParams.currentSoh,
      currentCycleCount: lifetimeParams.currentCycleCount,
      dailyCycles: lifetimeParams.dailyCycles
    })
    lifetimeResult.value = result
  } catch (e) {
    ElMessage.error('预估失败')
  }
}

const handleAddPoint = () => {
  Object.assign(pointFormData, {
    id: undefined,
    cycleCount: 0,
    soh: 100,
    capacityRetention: undefined,
    internalResistanceRatio: undefined,
    temperature: undefined,
    depthOfDischarge: undefined,
    chargeRate: undefined,
    dischargeRate: undefined,
    remarks: undefined
  })
  pointDialogVisible.value = true
}

const handleSubmitPoint = async () => {
  if (!pointFormRef.value || !selectedModel.value) return

  await pointFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await batteryDegradationApi.addPoint(selectedModel.value.id, pointFormData as BatteryDegradationPoint)
        ElMessage.success('添加成功')
        pointDialogVisible.value = false
        loadData()
      } catch (e) {
        ElMessage.error('添加失败')
      }
    }
  })
}

const handleRemovePoint = (pointId: number | undefined) => {
  if (!selectedModel.value || pointId === undefined) return

  ElMessageBox.confirm('确定要删除该数据点吗？', '提示', {
    type: 'warning'
  })
    .then(async () => {
      await batteryDegradationApi.removePoint(selectedModel.value!.id, pointId)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

const handleResize = () => {
  chart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

watch(
  () => selectedModel.value,
  () => {
    if (chart) {
      chart.dispose()
      chart = null
    }
  }
)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.model-list-card {
  height: 450px;
}

.model-item {
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.model-item:hover {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.model-item.active {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.model-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.model-name {
  font-weight: 600;
  color: #303133;
}

.model-item-info {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 4px;
}

.model-battery-type {
  font-size: 12px;
  color: #909399;
}

.model-item-desc {
  font-size: 12px;
  color: #606266;
}

.chart-container {
  width: 100%;
}

.form-unit {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
