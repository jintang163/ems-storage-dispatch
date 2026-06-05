<template>
  <div class="battery-config-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">电池参数配置</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增电池
        </el-button>
      </div>

      <el-row :gutter="20" class="stat-row">
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">电池总数</div>
              <div class="stat-value">{{ statistics.total || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">总容量</div>
              <div class="stat-value">{{ totalCapacity.toFixed(2) }}<span class="stat-unit">kWh</span></div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">总功率</div>
              <div class="stat-value">{{ totalPower.toFixed(2) }}<span class="stat-unit">kW</span></div>
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
      </el-row>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="设备编号">
          <el-input v-model="queryForm.deviceSn" placeholder="请输入设备编号" clearable />
        </el-form-item>
        <el-form-item label="电池名称">
          <el-input v-model="queryForm.batteryName" placeholder="请输入电池名称" clearable />
        </el-form-item>
        <el-form-item label="电池类型">
          <el-select v-model="queryForm.batteryType" placeholder="请选择电池类型" clearable style="width: 150px;">
            <el-option label="磷酸铁锂(LFP)" value="LFP" />
            <el-option label="三元锂(NMC)" value="NMC" />
            <el-option label="铅酸" value="LEAD_ACID" />
            <el-option label="其他" value="OTHER" />
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
        <el-table-column prop="deviceSn" label="设备编号" width="130" />
        <el-table-column prop="batteryName" label="电池名称" width="130" />
        <el-table-column prop="batteryType" label="电池类型" width="110">
          <template #default="{ row }">
            <el-tag :type="getBatteryTypeTag(row.batteryType)" size="small">
              {{ row.batteryType || '--' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="额定容量" width="100">
          <template #default="{ row }">{{ row.ratedCapacity }} kWh</template>
        </el-table-column>
        <el-table-column label="额定功率" width="100">
          <template #default="{ row }">{{ row.ratedPower }} kW</template>
        </el-table-column>
        <el-table-column label="充放电效率" width="130">
          <template #default="{ row }">
            {{ (row.chargeEfficiency * 100).toFixed(1) }}% / {{ (row.dischargeEfficiency * 100).toFixed(1) }}%
          </template>
        </el-table-column>
        <el-table-column label="SOC范围" width="110">
          <template #default="{ row }">{{ row.minSoc }}% - {{ row.maxSoc }}%</template>
        </el-table-column>
        <el-table-column label="温度范围" width="110">
          <template #default="{ row }">
            {{ row.minTemperature || '--' }}°C - {{ row.maxTemperature || '--' }}°C
          </template>
        </el-table-column>
        <el-table-column prop="initialSoh" label="初始SOH" width="90">
          <template #default="{ row }">{{ row.initialSoh }}%</template>
        </el-table-column>
        <el-table-column label="当前SOH" width="90">
          <template #default="{ row }">
            <span :style="{ color: getSohColor(row.currentSoh) }">
              {{ row.currentSoh ? row.currentSoh + '%' : '--' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="cycleCount" label="循环次数" width="90" />
        <el-table-column prop="enabled" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleCalculate(row)">计算</el-button>
            <el-button type="success" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
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
            <el-form-item label="设备编号" prop="deviceSn">
              <el-input v-model="formData.deviceSn" placeholder="请输入设备编号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电池名称" prop="batteryName">
              <el-input v-model="formData.batteryName" placeholder="请输入电池名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">基本参数</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="额定容量" prop="ratedCapacity">
              <el-input-number v-model="formData.ratedCapacity" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">kWh</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="额定功率" prop="ratedPower">
              <el-input-number v-model="formData.ratedPower" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">kW</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="充电效率" prop="chargeEfficiency">
              <el-input-number v-model="formData.chargeEfficiency" :min="0" :max="1" :step="0.01" :precision="3" style="width: 100%" />
              <span class="form-unit">(0-1)</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="放电效率" prop="dischargeEfficiency">
              <el-input-number v-model="formData.dischargeEfficiency" :min="0" :max="1" :step="0.01" :precision="3" style="width: 100%" />
              <span class="form-unit">(0-1)</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最小SOC" prop="minSoc">
              <el-input-number v-model="formData.minSoc" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大SOC" prop="maxSoc">
              <el-input-number v-model="formData.maxSoc" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最优SOC下限">
              <el-input-number v-model="formData.optimalSocMin" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最优SOC上限">
              <el-input-number v-model="formData.optimalSocMax" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">电气参数</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标称电压">
              <el-input-number v-model="formData.nominalVoltage" :min="0" :step="0.1" style="width: 100%" />
              <span class="form-unit">V</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大充电电流">
              <el-input-number v-model="formData.maxChargeCurrent" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">A</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大放电电流">
              <el-input-number v-model="formData.maxDischargeCurrent" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">A</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大充电功率">
              <el-input-number v-model="formData.maxChargePower" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">kW</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">温度参数</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最低工作温度">
              <el-input-number v-model="formData.minTemperature" :step="1" style="width: 100%" />
              <span class="form-unit">°C</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高工作温度">
              <el-input-number v-model="formData.maxTemperature" :step="1" style="width: 100%" />
              <span class="form-unit">°C</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">健康参数</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="初始SOH" prop="initialSoh">
              <el-input-number v-model="formData.initialSoh" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="当前SOH">
              <el-input-number v-model="formData.currentSoh" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="循环次数">
              <el-input-number v-model="formData.cycleCount" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">次</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电池类型">
              <el-select v-model="formData.batteryType" placeholder="请选择电池类型" style="width: 100%">
                <el-option label="磷酸铁锂(LFP)" value="LFP" />
                <el-option label="三元锂(NMC)" value="NMC" />
                <el-option label="铅酸" value="LEAD_ACID" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">其他信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生产厂商">
              <el-input v-model="formData.manufacturer" placeholder="请输入生产厂商" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安装日期">
              <el-date-picker
                v-model="formData.installationDate"
                type="date"
                placeholder="选择安装日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="保修期限">
              <el-input-number v-model="formData.warrantyPeriodMonths" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">月</span>
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
      v-model="calculateDialogVisible"
      title="电池计算工具"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form label-width="140px">
        <el-form-item label="当前SOC">
          <el-input-number v-model="calculateParams.currentSoc" :min="0" :max="100" :step="1" />
          <span class="form-unit">%</span>
        </el-form-item>
        <el-form-item label="当前温度">
          <el-input-number v-model="calculateParams.currentTemperature" :step="0.1" />
          <span class="form-unit">°C</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="executeCalculation">执行计算</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="calculateResult" title="计算结果" border column="1" style="margin-top: 20px;">
        <el-descriptions-item label="SOC安全检查">
          <el-tag :type="calculateResult.socSafe ? 'success' : 'danger'">
            {{ calculateResult.socSafe ? '安全' : '危险' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="温度安全检查">
          <el-tag :type="calculateResult.temperatureSafe ? 'success' : 'danger'">
            {{ calculateResult.temperatureSafe ? '安全' : '危险' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="有效容量">
          {{ calculateResult.effectiveCapacity?.toFixed(2) }} kWh
        </el-descriptions-item>
        <el-descriptions-item label="可用电量">
          {{ calculateResult.availableEnergy?.toFixed(2) }} kWh
        </el-descriptions-item>
        <el-descriptions-item label="最大充电功率">
          {{ calculateResult.maxChargePower?.toFixed(2) }} kW
        </el-descriptions-item>
        <el-descriptions-item label="最大放电功率">
          {{ calculateResult.maxDischargePower?.toFixed(2) }} kW
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { batteryConfigApi, type BatteryConfig } from '@/api'

const tableData = ref<BatteryConfig[]>([])
const dialogVisible = ref(false)
const calculateDialogVisible = ref(false)
const dialogTitle = ref('新增电池')
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const queryForm = reactive({
  deviceSn: '',
  batteryName: '',
  batteryType: '',
  enabled: undefined as boolean | undefined
})

const statistics = ref<Record<string, number>>({})

const filteredData = computed(() => {
  return tableData.value.filter(item => {
    if (queryForm.deviceSn && !item.deviceSn.includes(queryForm.deviceSn)) return false
    if (queryForm.batteryName && !item.batteryName.includes(queryForm.batteryName)) return false
    if (queryForm.batteryType && item.batteryType !== queryForm.batteryType) return false
    if (queryForm.enabled !== undefined && item.enabled !== queryForm.enabled) return false
    return true
  })
})

const totalCapacity = computed(() => {
  return tableData.value.reduce((sum, item) => sum + item.ratedCapacity, 0)
})

const totalPower = computed(() => {
  return tableData.value.reduce((sum, item) => sum + item.ratedPower, 0)
})

const enabledCount = computed(() => {
  return tableData.value.filter(item => item.enabled).length
})

const formData = reactive<Partial<BatteryConfig>>({
  id: undefined,
  deviceSn: '',
  batteryName: '',
  ratedCapacity: 100,
  ratedPower: 50,
  chargeEfficiency: 0.95,
  dischargeEfficiency: 0.95,
  minSoc: 10,
  maxSoc: 90,
  initialSoh: 100,
  enabled: true
})

const formRules: FormRules = {
  deviceSn: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  batteryName: [{ required: true, message: '请输入电池名称', trigger: 'blur' }],
  ratedCapacity: [{ required: true, message: '请输入额定容量', trigger: 'blur' }],
  ratedPower: [{ required: true, message: '请输入额定功率', trigger: 'blur' }],
  chargeEfficiency: [{ required: true, message: '请输入充电效率', trigger: 'blur' }],
  dischargeEfficiency: [{ required: true, message: '请输入放电效率', trigger: 'blur' }],
  minSoc: [{ required: true, message: '请输入最小SOC', trigger: 'blur' }],
  maxSoc: [{ required: true, message: '请输入最大SOC', trigger: 'blur' }],
  initialSoh: [{ required: true, message: '请输入初始SOH', trigger: 'blur' }],
  enabled: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const selectedBattery = ref<BatteryConfig | null>(null)
const calculateParams = reactive({
  currentSoc: 50,
  currentTemperature: 25
})

const calculateResult = ref<{
  socSafe?: boolean
  temperatureSafe?: boolean
  effectiveCapacity?: number
  availableEnergy?: number
  maxChargePower?: number
  maxDischargePower?: number
} | null>(null)

const getBatteryTypeTag = (type: string | undefined) => {
  const tagMap: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    LFP: 'success',
    NMC: 'warning',
    LEAD_ACID: 'info',
    OTHER: 'danger'
  }
  return tagMap[type || ''] || 'info'
}

const getSohColor = (soh: number | undefined) => {
  if (soh === undefined) return '#606266'
  if (soh >= 80) return '#67c23a'
  if (soh >= 60) return '#e6a23c'
  return '#f56c6c'
}

const loadData = async () => {
  try {
    const [list, stats] = await Promise.all([
      batteryConfigApi.list(),
      batteryConfigApi.getTypeStatistics().catch(() => ({}))
    ])
    tableData.value = list
    statistics.value = stats
  } catch (e) {
    ElMessage.error('加载数据失败')
  }
}

const resetQuery = () => {
  queryForm.deviceSn = ''
  queryForm.batteryName = ''
  queryForm.batteryType = ''
  queryForm.enabled = undefined
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增电池'
  Object.assign(formData, {
    id: undefined,
    deviceSn: '',
    batteryName: '',
    ratedCapacity: 100,
    ratedPower: 50,
    chargeEfficiency: 0.95,
    dischargeEfficiency: 0.95,
    roundTripEfficiency: undefined,
    minSoc: 10,
    maxSoc: 90,
    optimalSocMin: undefined,
    optimalSocMax: undefined,
    nominalVoltage: undefined,
    maxChargeCurrent: undefined,
    maxDischargeCurrent: undefined,
    maxChargePower: undefined,
    maxDischargePower: undefined,
    minTemperature: undefined,
    maxTemperature: undefined,
    optimalTempMin: undefined,
    optimalTempMax: undefined,
    initialSoh: 100,
    currentSoh: undefined,
    cycleCount: undefined,
    batteryType: undefined,
    manufacturer: undefined,
    installationDate: undefined,
    warrantyPeriodMonths: undefined,
    enabled: true,
    description: undefined
  })
  dialogVisible.value = true
}

const handleEdit = (row: BatteryConfig) => {
  isEdit.value = true
  dialogTitle.value = '编辑电池'
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

const handleDelete = (row: BatteryConfig) => {
  ElMessageBox.confirm('确定要删除该电池配置吗？', '提示', {
    type: 'warning'
  })
    .then(async () => {
      await batteryConfigApi.delete(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value && formData.id) {
          await batteryConfigApi.update(formData.id, formData as BatteryConfig)
          ElMessage.success('更新成功')
        } else {
          await batteryConfigApi.create(formData)
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

const handleCalculate = (row: BatteryConfig) => {
  selectedBattery.value = row
  calculateResult.value = null
  calculateDialogVisible.value = true
}

const executeCalculation = async () => {
  if (!selectedBattery.value) return

  try {
    const [
      socSafe,
      temperatureSafe,
      effectiveCapacity,
      availableEnergy,
      maxChargePower,
      maxDischargePower
    ] = await Promise.all([
      batteryConfigApi.checkSocSafe(selectedBattery.value.deviceSn, calculateParams.currentSoc),
      batteryConfigApi.checkTemperatureSafe(selectedBattery.value.deviceSn, calculateParams.currentTemperature),
      batteryConfigApi.calculateEffectiveCapacity(selectedBattery.value.deviceSn),
      batteryConfigApi.calculateAvailableEnergy(selectedBattery.value.deviceSn, calculateParams.currentSoc),
      batteryConfigApi.calculateMaxChargePower(selectedBattery.value.deviceSn, calculateParams.currentSoc, calculateParams.currentTemperature),
      batteryConfigApi.calculateMaxDischargePower(selectedBattery.value.deviceSn, calculateParams.currentSoc, calculateParams.currentTemperature)
    ])

    calculateResult.value = {
      socSafe,
      temperatureSafe,
      effectiveCapacity,
      availableEnergy,
      maxChargePower,
      maxDischargePower
    }
  } catch (e) {
    ElMessage.error('计算失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 20px;
}

.stat-item {
  text-align: center;
}

.stat-label {
  color: #909399;
  font-size: 14px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-unit {
  font-size: 14px;
  color: #909399;
  margin-left: 4px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
}

.query-form {
  margin-bottom: 20px;
}

.form-unit {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
