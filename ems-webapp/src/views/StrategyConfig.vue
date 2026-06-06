<template>
  <div class="strategy-config-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">日内与实时策略配置</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增策略
        </el-button>
      </div>

      <el-row :gutter="20" class="stat-row">
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">策略总数</div>
              <div class="stat-value">{{ strategyList.length || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">启用数量</div>
              <div class="stat-value" style="color: #67c23a;">{{ enabledCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">综合优化策略</div>
              <div class="stat-value">{{ typeStats.MULTI_OBJECTIVE || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">默认策略</div>
              <div class="stat-value" style="color: #409eff;">{{ defaultStrategyName || '--' }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="策略名称">
          <el-input v-model="queryForm.strategyName" placeholder="请输入策略名称" clearable />
        </el-form-item>
        <el-form-item label="策略类型">
          <el-select v-model="queryForm.strategyType" placeholder="请选择策略类型" clearable style="width: 180px;">
            <el-option label="综合优化" value="MULTI_OBJECTIVE" />
            <el-option label="收益优先" value="ARBITRAGE_FOCUSED" />
            <el-option label="寿命优先" value="LIFESPAN_FOCUSED" />
            <el-option label="需量优先" value="DEMAND_FOCUSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.enabled" placeholder="请选择状态" clearable style="width: 120px;">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="filteredData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="strategyName" label="策略名称" width="150" />
        <el-table-column prop="strategyCode" label="策略编码" width="180" />
        <el-table-column prop="strategyType" label="策略类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getStrategyTypeTag(row.strategyType)" size="small">
              {{ getStrategyTypeName(row.strategyType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="多目标权重" width="220">
          <template #default="{ row }">
            <div class="weight-display">
              <div class="weight-item">
                <span class="weight-label">套利</span>
                <el-progress :percentage="Math.round(row.arbitrageWeight * 100)" :stroke-width="8" :show-text="false" color="#67c23a" />
                <span class="weight-value">{{ (row.arbitrageWeight * 100).toFixed(0) }}%</span>
              </div>
              <div class="weight-item">
                <span class="weight-label">寿命</span>
                <el-progress :percentage="Math.round(row.lifespanWeight * 100)" :stroke-width="8" :show-text="false" color="#e6a23c" />
                <span class="weight-value">{{ (row.lifespanWeight * 100).toFixed(0) }}%</span>
              </div>
              <div class="weight-item">
                <span class="weight-label">需量</span>
                <el-progress :percentage="Math.round(row.demandWeight * 100)" :stroke-width="8" :show-text="false" color="#f56c6c" />
                <span class="weight-value">{{ (row.demandWeight * 100).toFixed(0) }}%</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="充放电倍率" width="130">
          <template #default="{ row }">
            {{ row.maxChargeRate.toFixed(2) }}C / {{ row.maxDischargeRate.toFixed(2) }}C
          </template>
        </el-table-column>
        <el-table-column label="SOC范围" width="100">
          <template #default="{ row }">{{ row.minSoc }}% - {{ row.maxSoc }}%</template>
        </el-table-column>
        <el-table-column label="最大放电深度" width="110">
          <template #default="{ row }">{{ row.maxDepthOfDischarge }}%</template>
        </el-table-column>
        <el-table-column label="每日循环次数" width="110">
          <template #default="{ row }">{{ row.maxDailyCycles }}次</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="enabled" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="defaultStrategy" label="默认" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.defaultStrategy" type="warning" size="small">默认</el-tag>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" size="small" @click="handleSetDefault(row)" :disabled="row.defaultStrategy">设为默认</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="140px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="策略名称" prop="strategyName">
              <el-input v-model="formData.strategyName" placeholder="请输入策略名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="策略编码" prop="strategyCode">
              <el-input v-model="formData.strategyCode" placeholder="请输入策略编码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="策略类型" prop="strategyType">
              <el-select v-model="formData.strategyType" placeholder="请选择策略类型" style="width: 100%;">
                <el-option label="综合优化策略" value="MULTI_OBJECTIVE" />
                <el-option label="收益优先策略" value="ARBITRAGE_FOCUSED" />
                <el-option label="寿命优先策略" value="LIFESPAN_FOCUSED" />
                <el-option label="需量控制优先策略" value="DEMAND_FOCUSED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="formData.priority" :min="1" :max="10" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">多目标权重配置</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="套利收益权重" prop="arbitrageWeight">
              <el-slider
                v-model="formData.arbitrageWeight"
                :min="0"
                :max="1"
                :step="0.05"
                :show-tooltip="true"
                :format-tooltip="(val) => (val * 100).toFixed(0) + '%'"
              />
              <div class="slider-value">{{ (formData.arbitrageWeight * 100).toFixed(0) }}%</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="寿命损耗权重" prop="lifespanWeight">
              <el-slider
                v-model="formData.lifespanWeight"
                :min="0"
                :max="1"
                :step="0.05"
                :show-tooltip="true"
                :format-tooltip="(val) => (val * 100).toFixed(0) + '%'"
              />
              <div class="slider-value">{{ (formData.lifespanWeight * 100).toFixed(0) }}%</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="需量费用权重" prop="demandWeight">
              <el-slider
                v-model="formData.demandWeight"
                :min="0"
                :max="1"
                :step="0.05"
                :show-tooltip="true"
                :format-tooltip="(val) => (val * 100).toFixed(0) + '%'"
              />
              <div class="slider-value">{{ (formData.demandWeight * 100).toFixed(0) }}%</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">电池寿命约束</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="最大充电倍率" prop="maxChargeRate">
              <el-input-number v-model="formData.maxChargeRate" :min="0.1" :max="2" :step="0.1" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大放电倍率" prop="maxDischargeRate">
              <el-input-number v-model="formData.maxDischargeRate" :min="0.1" :max="2" :step="0.1" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大日循环次数" prop="maxDailyCycles">
              <el-input-number v-model="formData.maxDailyCycles" :min="0.1" :max="10" :step="0.1" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="最小SOC(%)" prop="minSoc">
              <el-input-number v-model="formData.minSoc" :min="0" :max="100" :step="1" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大SOC(%)" prop="maxSoc">
              <el-input-number v-model="formData.maxSoc" :min="0" :max="100" :step="1" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大放电深度(%)" prop="maxDepthOfDischarge">
              <el-input-number v-model="formData.maxDepthOfDischarge" :min="0" :max="100" :step="1" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">功能开关</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="峰谷套利">
              <el-switch v-model="formData.peakValleyArbitrageEnabled" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="削峰">
              <el-switch v-model="formData.peakShavingEnabled" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="填谷">
              <el-switch v-model="formData.valleyFillingEnabled" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="需量控制">
              <el-switch v-model="formData.demandControlEnabled" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="电价预测">
              <el-switch v-model="formData.priceForecastEnabled" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="滚动优化">
              <el-switch v-model="formData.rollingOptimizationEnabled" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="需量阈值比例" prop="demandThresholdRatio">
              <el-input-number v-model="formData.demandThresholdRatio" :min="0.5" :max="1.5" :step="0.05" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调度间隔(分钟)" prop="scheduleIntervalMinutes">
              <el-input-number v-model="formData.scheduleIntervalMinutes" :min="15" :max="1440" :step="15" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="滚动间隔(分钟)" prop="rollingIntervalMinutes">
              <el-input-number v-model="formData.rollingIntervalMinutes" :min="5" :max="60" :step="5" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="关联电池">
              <el-input v-model="formData.batterySn" placeholder="请输入电池设备编号" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联变压器">
              <el-input v-model="formData.transformerCode" placeholder="请输入变压器编号" clearable />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-switch v-model="formData.enabled" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设为默认">
              <el-switch v-model="formData.defaultStrategy" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入策略描述" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleNormalizeWeights">归一化权重</el-button>
        <el-button type="success" @click="handleValidate">验证参数</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { strategyConfigApi, type StrategyConfig } from '@/api'

const strategyList = ref<StrategyConfig[]>([])
const queryForm = ref({
  strategyName: '',
  strategyType: '',
  enabled: null as boolean | null
})
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const formData = ref<Partial<StrategyConfig>>({
  strategyName: '',
  strategyType: 'MULTI_OBJECTIVE',
  strategyCode: '',
  arbitrageWeight: 0.5,
  lifespanWeight: 0.3,
  demandWeight: 0.2,
  maxChargeRate: 0.5,
  maxDischargeRate: 0.5,
  minSoc: 20,
  maxSoc: 90,
  maxDailyCycles: 1,
  maxDepthOfDischarge: 70,
  demandThresholdRatio: 0.9,
  priceForecastEnabled: true,
  peakValleyArbitrageEnabled: true,
  peakShavingEnabled: true,
  valleyFillingEnabled: true,
  demandControlEnabled: true,
  scheduleIntervalMinutes: 60,
  rollingOptimizationEnabled: true,
  rollingIntervalMinutes: 15,
  lookAheadHours: 24,
  priority: 5,
  enabled: true,
  defaultStrategy: false,
  description: ''
})

const formRules: FormRules = {
  strategyName: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  strategyType: [{ required: true, message: '请选择策略类型', trigger: 'change' }],
  strategyCode: [{ required: true, message: '请输入策略编码', trigger: 'blur' }],
  minSoc: [{ required: true, message: '请输入最小SOC', trigger: 'blur' }],
  maxSoc: [{ required: true, message: '请输入最大SOC', trigger: 'blur' }],
  scheduleIntervalMinutes: [{ required: true, message: '请输入调度间隔', trigger: 'blur' }]
}

const filteredData = computed(() => {
  return strategyList.value.filter(item => {
    if (queryForm.value.strategyName && !item.strategyName.includes(queryForm.value.strategyName)) {
      return false
    }
    if (queryForm.value.strategyType && item.strategyType !== queryForm.value.strategyType) {
      return false
    }
    if (queryForm.value.enabled !== null && item.enabled !== queryForm.value.enabled) {
      return false
    }
    return true
  })
})

const enabledCount = computed(() => {
  return strategyList.value.filter(item => item.enabled).length
})

const typeStats = computed(() => {
  const stats: Record<string, number> = {}
  strategyList.value.forEach(item => {
    stats[item.strategyType] = (stats[item.strategyType] || 0) + 1
  })
  return stats
})

const defaultStrategyName = computed(() => {
  const defaultStrategy = strategyList.value.find(item => item.defaultStrategy)
  return defaultStrategy?.strategyName || ''
})

const getStrategyTypeTag = (type: string) => {
  const tagMap: Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'> = {
    MULTI_OBJECTIVE: 'primary',
    ARBITRAGE_FOCUSED: 'success',
    LIFESPAN_FOCUSED: 'warning',
    DEMAND_FOCUSED: 'danger'
  }
  return tagMap[type] || 'info'
}

const getStrategyTypeName = (type: string) => {
  const nameMap: Record<string, string> = {
    MULTI_OBJECTIVE: '综合优化',
    ARBITRAGE_FOCUSED: '收益优先',
    LIFESPAN_FOCUSED: '寿命优先',
    DEMAND_FOCUSED: '需量优先'
  }
  return nameMap[type] || type
}

const loadData = async () => {
  try {
    strategyList.value = await strategyConfigApi.list()
  } catch (error: any) {
    ElMessage.error(error.message || '加载数据失败')
  }
}

const resetQuery = () => {
  queryForm.value = {
    strategyName: '',
    strategyType: '',
    enabled: null
  }
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增策略配置'
  formData.value = {
    strategyName: '',
    strategyType: 'MULTI_OBJECTIVE',
    strategyCode: '',
    arbitrageWeight: 0.5,
    lifespanWeight: 0.3,
    demandWeight: 0.2,
    maxChargeRate: 0.5,
    maxDischargeRate: 0.5,
    minSoc: 20,
    maxSoc: 90,
    maxDailyCycles: 1,
    maxDepthOfDischarge: 70,
    demandThresholdRatio: 0.9,
    priceForecastEnabled: true,
    peakValleyArbitrageEnabled: true,
    peakShavingEnabled: true,
    valleyFillingEnabled: true,
    demandControlEnabled: true,
    scheduleIntervalMinutes: 60,
    rollingOptimizationEnabled: true,
    rollingIntervalMinutes: 15,
    lookAheadHours: 24,
    priority: 5,
    enabled: true,
    defaultStrategy: false,
    description: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row: StrategyConfig) => {
  isEdit.value = true
  dialogTitle.value = '编辑策略配置'
  formData.value = { ...row }
  dialogVisible.value = true
}

const handleDelete = (row: StrategyConfig) => {
  ElMessageBox.confirm('确定要删除该策略配置吗？', '删除确认', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await strategyConfigApi.delete(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
    }
  })
}

const handleSetDefault = async (row: StrategyConfig) => {
  try {
    await strategyConfigApi.setDefault(row.id)
    ElMessage.success('设置成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '设置失败')
  }
}

const handleNormalizeWeights = async () => {
  try {
    const total = await strategyConfigApi.normalizeWeights(formData.value as StrategyConfig)
    formData.value = { ...formData.value }
    ElMessage.success(`权重已归一化，总和: ${total.toFixed(2)}`)
  } catch (error: any) {
    ElMessage.error(error.message || '权重归一化失败')
  }
}

const handleValidate = async () => {
  try {
    const errors = await strategyConfigApi.validate(formData.value as StrategyConfig)
    if (Object.keys(errors).length === 0) {
      ElMessage.success('参数验证通过')
    } else {
      ElMessage.warning('参数验证失败: ' + JSON.stringify(errors))
    }
  } catch (error: any) {
    ElMessage.error(error.message || '验证失败')
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    if (isEdit.value && formData.value.id) {
      await strategyConfigApi.update(formData.value.id, formData.value as StrategyConfig)
      ElMessage.success('更新成功')
    } else {
      await strategyConfigApi.create(formData.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="less">
.strategy-config-page {
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
        font-size: 28px;
        font-weight: 600;
        color: #303133;

        .stat-unit {
          font-size: 14px;
          color: #909399;
          margin-left: 4px;
        }
      }
    }
  }

  .query-form {
    margin-bottom: 20px;
  }

  .weight-display {
    .weight-item {
      display: flex;
      align-items: center;
      margin-bottom: 4px;

      .weight-label {
        width: 30px;
        font-size: 12px;
        color: #606266;
      }

      .weight-value {
        width: 35px;
        font-size: 12px;
        text-align: right;
        color: #606266;
      }

      :deep(.el-progress) {
        flex: 1;
        margin: 0 8px;
      }
    }
  }

  .slider-value {
    text-align: center;
    font-size: 14px;
    color: #409eff;
    margin-top: 4px;
  }
}
</style>
