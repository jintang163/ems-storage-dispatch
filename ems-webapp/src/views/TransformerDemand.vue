<template>
  <div class="transformer-demand-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">变压器需量管理</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增变压器
        </el-button>
      </div>

      <el-row :gutter="20" class="stat-row">
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">变压器总数</div>
              <div class="stat-value">{{ tableData.length }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">总容量</div>
              <div class="stat-value">{{ totalCapacity.toFixed(2) }}<span class="stat-unit">kVA</span></div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">需量控制启用</div>
              <div class="stat-value" style="color: #67c23a;">{{ controlEnabledCount }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">月度电费预估</div>
              <div class="stat-value">{{ monthlyChargeEstimate.toFixed(2) }}<span class="stat-unit">元</span></div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-card shadow="hover">
            <template #header>
              <div class="card-header">
                <span>变压器列表</span>
              </div>
            </template>
            <el-scrollbar height="400px">
              <div
                v-for="item in tableData"
                :key="item.id"
                class="transformer-item"
                :class="{ active: selectedTransformer?.id === item.id }"
                @click="selectTransformer(item)"
              >
                <div class="transformer-item-header">
                  <span class="transformer-name">{{ item.transformerName }}</span>
                  <el-tag v-if="item.demandControlEnabled" type="success" size="small">控制中</el-tag>
                  <el-tag v-else type="info" size="small">未控制</el-tag>
                </div>
                <div class="transformer-item-info">
                  <span class="transformer-code">{{ item.transformerCode }}</span>
                  <span class="transformer-capacity">{{ item.ratedCapacity }} kVA</span>
                </div>
                <div class="transformer-item-threshold">
                  <el-progress
                    :percentage="getDemandPercentage(item)"
                    :status="getDemandStatus(item)"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <span class="threshold-text">需量阈值: {{ item.demandThreshold }} kW</span>
                </div>
              </div>
            </el-scrollbar>
          </el-card>
        </el-col>

        <el-col :span="16">
          <el-card shadow="hover" v-if="selectedTransformer">
            <template #header>
              <div class="card-header">
                <span>{{ selectedTransformer.transformerName }} - 详情</span>
                <el-button-group>
                  <el-button size="small" @click="loadDemandPrediction">需量预测</el-button>
                  <el-button size="small" type="primary" @click="handleEdit(selectedTransformer)">编辑</el-button>
                  <el-button size="small" type="success" @click="toggleControl" :loading="togglingControl">
                    {{ selectedTransformer.demandControlEnabled ? '停用控制' : '启用控制' }}
                  </el-button>
                </el-button-group>
              </div>
            </template>

            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="变压器编号">
                {{ selectedTransformer.transformerCode }}
              </el-descriptions-item>
              <el-descriptions-item label="额定容量">
                {{ selectedTransformer.ratedCapacity }} kVA
              </el-descriptions-item>
              <el-descriptions-item label="额定电压">
                {{ selectedTransformer.ratedVoltage || '--' }} V
              </el-descriptions-item>
              <el-descriptions-item label="需量阈值">
                {{ selectedTransformer.demandThreshold }} kW
              </el-descriptions-item>
              <el-descriptions-item label="预警阈值">
                {{ selectedTransformer.demandWarningThreshold || '--' }} kW
              </el-descriptions-item>
              <el-descriptions-item label="考核周期">
                {{ selectedTransformer.assessmentCycleMinutes }} 分钟
              </el-descriptions-item>
              <el-descriptions-item label="计费方式">
                {{ getBillingMethodText(selectedTransformer.demandBillingMethod) }}
              </el-descriptions-item>
              <el-descriptions-item label="需量电价">
                {{ selectedTransformer.demandPrice || '--' }} 元/kW/月
              </el-descriptions-item>
              <el-descriptions-item label="容量电价">
                {{ selectedTransformer.capacityPrice || '--' }} 元/kVA/月
              </el-descriptions-item>
              <el-descriptions-item label="上期最大需量">
                {{ selectedTransformer.maxDemandPrevious || '--' }} kW
              </el-descriptions-item>
              <el-descriptions-item label="最小SOC保护">
                {{ selectedTransformer.minSocProtection || '--' }}%
              </el-descriptions-item>
              <el-descriptions-item label="安装位置">
                {{ selectedTransformer.location || '--' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-divider content-position="left">控制策略优先级</el-divider>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-statistic title="放电优先级" :value="selectedTransformer.dischargePriority || 0" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="负载切除优先级" :value="selectedTransformer.loadSheddingPriority || 0" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="光伏自用优先级" :value="selectedTransformer.pvSelfUsePriority || 0" />
              </el-col>
            </el-row>
          </el-card>

          <el-card shadow="hover" style="margin-top: 20px;">
            <template #header>
              <div class="card-header">
                <span>需量预测与控制建议</span>
              </div>
            </template>

            <el-row :gutter="20">
              <el-col :span="12">
                <h4 style="margin-top: 0;">需量预测</h4>
                <el-form label-width="120px">
                  <el-form-item label="当前功率">
                    <el-input-number v-model="predictionParams.currentPower" :min="0" :step="1" style="width: 200px;" />
                    <span class="form-unit">kW</span>
                  </el-form-item>
                  <el-form-item label="周期已过时间">
                    <el-input-number v-model="predictionParams.cycleElapsedMinutes" :min="0" :step="1" style="width: 200px;" />
                    <span class="form-unit">分钟</span>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" @click="predictDemand">预测需量</el-button>
                    <el-button @click="generateRecommendation">生成建议</el-button>
                  </el-form-item>
                </el-form>

                <el-alert v-if="predictedDemand !== null" :title="`预测需量: ${predictedDemand.toFixed(2)} kW`" :type="getAlertType(predictedDemand)" show-icon style="margin-top: 10px;" />
                <el-alert v-if="warningMessage" :title="warningMessage" type="warning" show-icon style="margin-top: 10px;" />
              </el-col>

              <el-col :span="12">
                <h4 style="margin-top: 0;">控制建议</h4>
                <div v-if="recommendation">
                  <el-descriptions border column="1" size="small">
                    <el-descriptions-item label="紧急程度">
                      <el-tag :type="getUrgencyTagType(recommendation.urgencyLevel)" size="small">
                        {{ getUrgencyText(recommendation.urgencyLevel) }}
                      </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="阈值比例">
                      {{ (recommendation.thresholdRatio * 100).toFixed(1) }}%
                    </el-descriptions-item>
                    <el-descriptions-item label="需削减功率">
                      {{ recommendation.requiredReduction.toFixed(2) }} kW
                    </el-descriptions-item>
                    <el-descriptions-item label="建议放电功率">
                      {{ recommendation.dischargePower.toFixed(2) }} kW
                    </el-descriptions-item>
                    <el-descriptions-item label="建议充电功率">
                      {{ recommendation.chargePower.toFixed(2) }} kW
                    </el-descriptions-item>
                    <el-descriptions-item label="建议切负荷">
                      {{ recommendation.loadSheddingPower.toFixed(2) }} kW
                    </el-descriptions-item>
                  </el-descriptions>
                  <div style="margin-top: 10px;">
                    <h5>建议措施：</h5>
                    <ul>
                      <li v-for="(action, index) in recommendation.recommendedActions" :key="index">{{ action }}</li>
                    </ul>
                  </div>
                </div>
                <div v-else style="color: #909399; text-align: center; padding: 40px 0;">
                  点击"生成建议"按钮获取控制策略
                </div>
              </el-col>
            </el-row>
          </el-card>

          <el-card shadow="hover" style="margin-top: 20px;">
            <template #header>
              <div class="card-header">
                <span>电费计算工具</span>
              </div>
            </template>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form label-width="120px">
                  <el-form-item label="最大需量">
                    <el-input-number v-model="chargeParams.maxDemand" :min="0" :step="1" style="width: 200px;" />
                    <span class="form-unit">kW</span>
                  </el-form-item>
                  <el-form-item label="计费天数">
                    <el-input-number v-model="chargeParams.days" :min="1" :max="31" :step="1" style="width: 200px;" />
                    <span class="form-unit">天</span>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" @click="calculateCharge">计算电费</el-button>
                  </el-form-item>
                </el-form>
              </el-col>

              <el-col :span="12">
                <el-descriptions v-if="calculatedCharge !== null" border column="1" size="small">
                  <el-descriptions-item label="计算结果">
                    <span style="font-size: 24px; font-weight: 600; color: #f56c6c;">{{ calculatedCharge.toFixed(2) }}</span>
                    <span style="color: #909399; margin-left: 8px;">元</span>
                  </el-descriptions-item>
                </el-descriptions>

                <el-divider content-position="left">节电计算</el-divider>

                <el-form label-width="120px">
                  <el-form-item label="原最大需量">
                    <el-input-number v-model="savingParams.originalMaxDemand" :min="0" :step="1" style="width: 150px;" />
                    <span class="form-unit">kW</span>
                  </el-form-item>
                  <el-form-item label="优化后需量">
                    <el-input-number v-model="savingParams.optimizedMaxDemand" :min="0" :step="1" style="width: 150px;" />
                    <span class="form-unit">kW</span>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="success" @click="calculateSaving">计算节电</el-button>
                  </el-form-item>
                </el-form>
                <el-alert v-if="calculatedSaving !== null" :title="`预计节省电费: ${calculatedSaving.toFixed(2)} 元`" type="success" show-icon style="margin-top: 10px;" />
              </el-col>
            </el-row>
          </el-card>
        </el-col>
      </el-row>
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
            <el-form-item label="变压器编号" prop="transformerCode">
              <el-input v-model="formData.transformerCode" placeholder="请输入变压器编号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="变压器名称" prop="transformerName">
              <el-input v-model="formData.transformerName" placeholder="请输入变压器名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">基本参数</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="额定容量" prop="ratedCapacity">
              <el-input-number v-model="formData.ratedCapacity" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">kVA</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="额定电压">
              <el-input-number v-model="formData.ratedVoltage" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">V</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">需量配置</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="需量阈值" prop="demandThreshold">
              <el-input-number v-model="formData.demandThreshold" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">kW</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预警阈值">
              <el-input-number v-model="formData.demandWarningThreshold" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">kW</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="需量上限">
              <el-input-number v-model="formData.demandLimit" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">kW</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考核周期" prop="assessmentCycleMinutes">
              <el-select v-model="formData.assessmentCycleMinutes" style="width: 100%">
                <el-option label="15分钟" :value="15" />
                <el-option label="30分钟" :value="30" />
                <el-option label="60分钟" :value="60" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">电价配置</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计费方式">
              <el-select v-model="formData.demandBillingMethod" style="width: 100%">
                <el-option label="需量计费" value="DEMAND" />
                <el-option label="容量计费" value="CAPACITY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需量电价">
              <el-input-number v-model="formData.demandPrice" :min="0" :step="0.01" :precision="2" style="width: 100%" />
              <span class="form-unit">元/kW/月</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="容量电价">
              <el-input-number v-model="formData.capacityPrice" :min="0" :step="0.01" :precision="2" style="width: 100%" />
              <span class="form-unit">元/kVA/月</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上期最大需量">
              <el-input-number v-model="formData.maxDemandPrevious" :min="0" :step="1" style="width: 100%" />
              <span class="form-unit">kW</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">控制策略</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="需量控制" prop="demandControlEnabled">
              <el-radio-group v-model="formData.demandControlEnabled">
                <el-radio :value="true">启用</el-radio>
                <el-radio :value="false">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预警通知">
              <el-switch v-model="formData.warningNotificationEnabled" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="放电优先级">
              <el-input-number v-model="formData.dischargePriority" :min="0" :max="10" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="切负荷优先级">
              <el-input-number v-model="formData.loadSheddingPriority" :min="0" :max="10" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="光伏自用优先级">
              <el-input-number v-model="formData.pvSelfUsePriority" :min="0" :max="10" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最小SOC保护">
              <el-input-number v-model="formData.minSocProtection" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通知阈值百分比">
              <el-input-number v-model="formData.notificationThresholdPercent" :min="0" :max="100" :step="1" style="width: 100%" />
              <span class="form-unit">%</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="削峰填谷">
              <el-switch v-model="formData.peakShavingEnabled" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="能量管理">
              <el-switch v-model="formData.energyManagementEnabled" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">其他信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="安装位置">
              <el-input v-model="formData.location" placeholder="请输入安装位置" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生产厂商">
              <el-input v-model="formData.manufacturer" placeholder="请输入生产厂商" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { transformerDemandApi, type TransformerDemandConfig } from '@/api'

const tableData = ref<TransformerDemandConfig[]>([])
const selectedTransformer = ref<TransformerDemandConfig | null>(null)
const dialogVisible = ref(false)
const dialogTitle = ref('新增变压器')
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const togglingControl = ref(false)

const predictionParams = reactive({
  currentPower: 100,
  cycleElapsedMinutes: 10
})

const chargeParams = reactive({
  maxDemand: 100,
  days: 30
})

const savingParams = reactive({
  originalMaxDemand: 120,
  optimizedMaxDemand: 100,
  days: 30
})

const predictedDemand = ref<number | null>(null)
const warningMessage = ref<string | null>(null)
const recommendation = ref<{
  dischargePower: number
  chargePower: number
  loadSheddingPower: number
  urgencyLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | 'NORMAL'
  thresholdRatio: number
  requiredReduction: number
  recommendedActions: string[]
} | null>(null)

const calculatedCharge = ref<number | null>(null)
const calculatedSaving = ref<number | null>(null)

const totalCapacity = computed(() => {
  return tableData.value.reduce((sum, item) => sum + item.ratedCapacity, 0)
})

const controlEnabledCount = computed(() => {
  return tableData.value.filter(item => item.demandControlEnabled).length
})

const monthlyChargeEstimate = computed(() => {
  return tableData.value.reduce((sum, item) => {
    if (item.demandBillingMethod === 'DEMAND' && item.demandPrice && item.demandThreshold) {
      return sum + item.demandPrice * item.demandThreshold
    } else if (item.demandBillingMethod === 'CAPACITY' && item.capacityPrice) {
      return sum + item.capacityPrice * item.ratedCapacity
    }
    return sum
  }, 0)
})

const formData = reactive<Partial<TransformerDemandConfig>>({
  id: undefined,
  transformerCode: '',
  transformerName: '',
  ratedCapacity: 500,
  demandThreshold: 400,
  assessmentCycleMinutes: 15,
  demandControlEnabled: false,
  enabled: true
})

const formRules: FormRules = {
  transformerCode: [{ required: true, message: '请输入变压器编号', trigger: 'blur' }],
  transformerName: [{ required: true, message: '请输入变压器名称', trigger: 'blur' }],
  ratedCapacity: [{ required: true, message: '请输入额定容量', trigger: 'blur' }],
  demandThreshold: [{ required: true, message: '请输入需量阈值', trigger: 'blur' }],
  assessmentCycleMinutes: [{ required: true, message: '请选择考核周期', trigger: 'change' }],
  demandControlEnabled: [{ required: true, message: '请选择需量控制状态', trigger: 'change' }],
  enabled: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const getDemandPercentage = (item: TransformerDemandConfig) => {
  if (!item.demandThreshold || !item.maxDemandCurrent) return 0
  return Math.min(100, (item.maxDemandCurrent / item.demandThreshold) * 100)
}

const getDemandStatus = (item: TransformerDemandConfig) => {
  const percentage = getDemandPercentage(item)
  if (percentage >= 100) return 'exception'
  if (percentage >= 90) return 'warning'
  return ''
}

const getBillingMethodText = (method: string | undefined) => {
  const methodMap: Record<string, string> = {
    DEMAND: '需量计费',
    CAPACITY: '容量计费'
  }
  return methodMap[method || ''] || '--'
}

const getAlertType = (demand: number) => {
  if (!selectedTransformer.value) return 'info'
  const ratio = demand / selectedTransformer.value.demandThreshold
  if (ratio >= 1) return 'error'
  if (ratio >= 0.9) return 'warning'
  return 'success'
}

const getUrgencyTagType = (level: string) => {
  const tagMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    NORMAL: 'success',
    LOW: 'info',
    MEDIUM: 'warning',
    HIGH: 'warning',
    CRITICAL: 'danger'
  }
  return tagMap[level] || 'info'
}

const getUrgencyText = (level: string) => {
  const textMap: Record<string, string> = {
    NORMAL: '正常',
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高',
    CRITICAL: '紧急'
  }
  return textMap[level] || level
}

const loadData = async () => {
  try {
    const list = await transformerDemandApi.list()
    tableData.value = list
    if (list.length > 0 && !selectedTransformer.value) {
      selectTransformer(list[0])
    }
  } catch (e) {
    ElMessage.error('加载数据失败')
  }
}

const selectTransformer = (item: TransformerDemandConfig) => {
  selectedTransformer.value = item
  predictedDemand.value = null
  warningMessage.value = null
  recommendation.value = null
  calculatedCharge.value = null
  calculatedSaving.value = null
  predictionParams.currentPower = Math.round(item.ratedCapacity * 0.7)
  chargeParams.maxDemand = item.demandThreshold
}

const loadDemandPrediction = async () => {
  if (!selectedTransformer.value) return

  try {
    const predicted = await transformerDemandApi.predictDemand({
      transformerCode: selectedTransformer.value.transformerCode,
      currentPower: predictionParams.currentPower,
      cycleElapsedMinutes: predictionParams.cycleElapsedMinutes
    })
    predictedDemand.value = predicted

    const warning = await transformerDemandApi.checkWarning(
      selectedTransformer.value.transformerCode,
      predicted
    )
    warningMessage.value = warning
  } catch (e) {
    ElMessage.error('预测失败')
  }
}

const predictDemand = async () => {
  await loadDemandPrediction()
}

const generateRecommendation = async () => {
  if (!selectedTransformer.value) return

  try {
    const rec = await transformerDemandApi.generateRecommendation({
      transformerCode: selectedTransformer.value.transformerCode,
      currentLoad: predictionParams.currentPower,
      currentPvPower: 20,
      currentSoc: 60,
      predictedDemand: predictedDemand.value || predictionParams.currentPower
    })
    recommendation.value = rec
  } catch (e) {
    ElMessage.error('生成建议失败')
  }
}

const calculateCharge = async () => {
  if (!selectedTransformer.value) return

  try {
    const result = await transformerDemandApi.calculateCharge(
      selectedTransformer.value.transformerCode,
      chargeParams.maxDemand,
      chargeParams.days
    )
    calculatedCharge.value = result
  } catch (e) {
    ElMessage.error('计算失败')
  }
}

const calculateSaving = async () => {
  if (!selectedTransformer.value) return

  try {
    const result = await transformerDemandApi.calculateSaving({
      transformerCode: selectedTransformer.value.transformerCode,
      originalMaxDemand: savingParams.originalMaxDemand,
      optimizedMaxDemand: savingParams.optimizedMaxDemand,
      days: savingParams.days
    })
    calculatedSaving.value = result
  } catch (e) {
    ElMessage.error('计算失败')
  }
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增变压器'
  Object.assign(formData, {
    id: undefined,
    transformerCode: '',
    transformerName: '',
    ratedCapacity: 500,
    ratedVoltage: undefined,
    ratedCurrent: undefined,
    demandThreshold: 400,
    demandWarningThreshold: undefined,
    demandLimit: undefined,
    assessmentCycleMinutes: 15,
    demandBillingMethod: undefined,
    demandPrice: undefined,
    capacityPrice: undefined,
    maxDemandCurrent: undefined,
    maxDemandPrevious: undefined,
    demandControlEnabled: false,
    controlStrategy: undefined,
    dischargePriority: undefined,
    loadSheddingPriority: undefined,
    pvSelfUsePriority: undefined,
    minSocProtection: undefined,
    warningNotificationEnabled: undefined,
    notificationThresholdPercent: undefined,
    peakShavingEnabled: undefined,
    peakShavingThreshold: undefined,
    energyManagementEnabled: undefined,
    location: undefined,
    installationDate: undefined,
    manufacturer: undefined,
    enabled: true,
    description: undefined
  })
  dialogVisible.value = true
}

const handleEdit = (row: TransformerDemandConfig) => {
  isEdit.value = true
  dialogTitle.value = '编辑变压器'
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

const toggleControl = async () => {
  if (!selectedTransformer.value) return

  togglingControl.value = true
  try {
    await transformerDemandApi.updateControlEnabled(
      selectedTransformer.value.id,
      !selectedTransformer.value.demandControlEnabled
    )
    ElMessage.success(selectedTransformer.value.demandControlEnabled ? '已停用需量控制' : '已启用需量控制')
    loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    togglingControl.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value && formData.id) {
          await transformerDemandApi.update(formData.id, formData as TransformerDemandConfig)
          ElMessage.success('更新成功')
        } else {
          await transformerDemandApi.create(formData)
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.transformer-item {
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.transformer-item:hover {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.transformer-item.active {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.transformer-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.transformer-name {
  font-weight: 600;
  color: #303133;
}

.transformer-item-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.transformer-code {
  font-size: 12px;
  color: #909399;
}

.transformer-capacity {
  font-size: 12px;
  color: #606266;
}

.transformer-item-threshold {
  display: flex;
  align-items: center;
  gap: 10px;
}

.threshold-text {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.form-unit {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
